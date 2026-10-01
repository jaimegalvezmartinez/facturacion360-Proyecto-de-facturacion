/**
 * @file Lo que la aplicación hace con la sesión: quién está dentro y cerrar sesión.
 *
 * Se carga en las cinco pantallas de la aplicación (index, clientes, facturas, perfil y
 * ayuda). No protege nada por sí mismo: eso lo hace el filtro de seguridad del servidor,
 * que ya no deja abrir ninguna de estas páginas sin sesión. Lo que hace aquí es la parte
 * bonita de la seguridad, que es la parte pequeña pero es la que se nota:
 *
 *   - Si el que ha entrado no es administrador, esconde lo que no puede usar (el enlace a
 *     "Mi Perfil" y el botón de la papelera). Es comodidad, no protección: el botón seguiría
 *     dando un 403 si alguien lo quitase del DOM, y eso es lo que importa. Lo contrario
 *     tampoco: esconderlo sin comprobar el rol dejaría a un administrador sin poder
 *     borrar.
 *
 *   - Enlaza el botón "Salir", que no es un enlace sino un <button>: si fuera un <a> y
 *     el JavaScript no llegara a cargarse, al pulsarlo se vería el login y se pensaría
 *     que la sesión se había cerrado. Con un botón, o lo cierra o no hace nada.
 *
 * @author AngelDanielC0des
 */

import { cerrarSesion, irAlLogin } from "./seguridad.js";

const botonSalir = document.getElementById("salir");

/**
 * Quién está dentro.
 *
 * La página ya se ha servido solo si hay sesión, así que un 401 aquí significa que la
 * sesión ha caducado entre que se pidió la página y que se ha ejecutado este archivo.
 *
 * @return {Promise<Object|null>} los datos del usuario, o null si no se ha podido saber
 */
async function usuarioDeLaSesion() {
    try {
        const respuesta = await fetch("/auth/yo", { headers: { Accept: "application/json" } });

        if (!respuesta.ok) return null;

        return await respuesta.json();
    } catch {
        return null;
    }
}

/**
 * Esconde lo que necesita ser administrador.
 *
 * Se hace añadiendo una clase al <body> en vez de quitar los elementos: el CSS de acceso.css
 * esconde lo que lleva `data-solo-admin` cuando el body tiene esa clase. Así funciona con
 * los elementos que se crean después, que es el caso del botón de la papelera: cada fila de
 * la tabla se repinta al hacer scroll y al guardar, y si el JavaScript las borrara una a
 * una, la siguiente pintada volvería a traer el botón.
 *
 * Cuando no se ha podido saber quién está dentro (la llamada a /auth/yo ha fallado), el
 * criterio es esconder también. Es lo contrario de lo cómodo y es lo que toca: sin saber si
 * la persona es ADMIN, lo que se enseña por defecto es lo de menos.
 */
function ajustarPermisos(usuario) {
    if (!usuario || usuario.rol === "ADMIN") return;

    document.body.classList.add("sin-permiso");
}

/**
 * Cierra la sesión y vuelve al login.
 *
 * Es un POST y lleva token de CSRF como cualquier otra escritura: aunque solo invalide una
 * sesión, es una operación que cambia el estado del servidor y no puede venir de un
 * formulario suelto de otra página.
 */
async function salir() {
    botonSalir.disabled = true;

    try {
        await cerrarSesion();
    } catch {
        // Si la petición falla, se vuelve al login igualmente. Peor es dejar a alguien
        // dentro de la aplicación creyendo que ha salido, y la sesión, en ese caso,
        // sigue viva en el servidor.
    }

    irAlLogin();
}

botonSalir?.addEventListener("click", salir);

await usuarioDeLaSesion().then(ajustarPermisos);