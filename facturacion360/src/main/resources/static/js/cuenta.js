/**
 * @file La pantalla "Mi cuenta": quién está dentro y cambio de contraseña.
 *
 * Fuera del login y dentro de la aplicación, que es lo que se pidió: se llega desde el menú
 * con la sesión ya abierta.
 *
 * No importa api.js (sí seguridad.js y problema.js) por el motivo que ya salió en sesion.js:
 * api.js arrastra dom.js, que al cargarse busca "#tabla-clientes" y aquí no hay ninguna tabla.
 *
 * Lo que NO hace esta pantalla, y es lo importante: no decide si el cambio es válido. Eso lo
 * comprueba el servidor (que además es el único que sabe el hash guardado), y lo que llega
 * aquí es un 200 o un 400 con el motivo. Toda comprobación de contraseña en el navegador
 * sería solo para poder avisar antes de gastar una petición.
 *
 * @author AngelDanielC0des
 */

import { cabecerasConSeguridad, irAlLogin } from "./seguridad.js";
import { motivoDe } from "./problema.js";

const formulario = document.getElementById("formulario-clave");
const botonGuardar = document.getElementById("boton-guardar");

const avisoError = document.getElementById("aviso-error");
const avisoOk = document.getElementById("aviso-ok");

const datoUsuario = document.getElementById("dato-usuario");
const datoNombre = document.getElementById("dato-nombre");
const datoRol = document.getElementById("dato-rol");

/** El mínimo del servidor, replicado para no gastar una petición en un 400 previsible. */
const MINIMO_CLAVE = 8;

/**
 * Escribe un error y esconde el aviso bueno.
 *
 * @param {string} texto el motivo que se lee
 */
function mostrarError(texto) {
    avisoError.textContent = texto;
    avisoError.hidden = false;
    avisoOk.hidden = true;
}

/**
 * Escribe un aviso de que la operación ha ido bien.
 *
 * @param {string} texto el mensaje que se lee
 */
function mostrarOk(texto) {
    avisoOk.textContent = texto;
    avisoOk.hidden = false;
    avisoError.hidden = true;
}

/** Esconde los dos avisos. */
function limpiarAvisos() {
    avisoError.hidden = true;
    avisoOk.hidden = true;
}

/**
 * Rellena los tres datos de la sesión.
 *
 * @return {Promise<Object>} el usuario de la sesión, o un objeto con `caducada: true` si el
 *         servidor dijo que ya no hay sesión. Un fallo de red NO se cuenta como sesión
 *         caducada: si la red falla un segundo, no debe echar a nadie al login.
 */
async function pintarDatos() {
    try {
        const respuesta = await fetch("/auth/yo", { headers: { Accept: "application/json" } });

        if (respuesta.status === 401) return { caducada: true };
        if (!respuesta.ok) return {};

        const usuario = await respuesta.json();

        datoUsuario.textContent = usuario.usuario;
        datoNombre.textContent = usuario.nombre;
        datoRol.textContent = usuario.rol === "ADMIN" ? "Administrador" : "Usuario";

        return usuario;
    } catch {
        return {};
    }
}

/**
 * Comprueba aquí lo que se puede comprobar, y manda el cambio al servidor.
 *
 * @param {Event} evento el submit
 */
async function cambiarClave(evento) {
    evento.preventDefault();
    limpiarAvisos();

    const actual = formulario.elements.claveActual;
    const nueva = formulario.elements.nueva;
    const repetida = formulario.elements.nuevaRepetida;

    // Las dos repeticiones se comparan en el navegador, pero el cambio se manda igualmente
    // al servidor: esto es una comodidad para no mandar una petición a la que ya se sabe
    // la respuesta, no una comprobación de seguridad.
    if (nueva.value !== repetida.value) {
        mostrarError("Las dos contraseñas nuevas no son iguales.");
        repetida.focus();
        return;
    }

    if (nueva.value.length < MINIMO_CLAVE) {
        mostrarError(`La contraseña nueva necesita al menos ${MINIMO_CLAVE} caracteres.`);
        nueva.focus();
        return;
    }

    if (nueva.value === actual.value) {
        mostrarError("La contraseña nueva tiene que ser distinta de la actual.");
        nueva.focus();
        return;
    }

    botonGuardar.disabled = true;

    try {
        const respuesta = await fetch("/auth/clave", {
            method: "PUT",
            headers: await cabecerasConSeguridad({ "Content-Type": "application/json" }),
            body: JSON.stringify({ claveActual: actual.value, nueva: nueva.value }),
        });

        if (respuesta.ok) {
            // El formulario se vacía y el foco vuelve a la casilla de la actual. Vaciarlo
            // es lo importante: dejar la contraseña nueva escrita en un campo de la página
            // es dejarla a la vista de cualquiera que se acerque a la pantalla.
            formulario.reset();
            actual.focus();

            mostrarOk("Contraseña guardada. La próxima vez que entres, usa la nueva.");
            return;
        }

        // Un 401 aquí es una sesión caducada, no una contraseña mala: seguridad.js ya está
        // llevando al login cuando aparece, y por eso aquí no se toca.
        mostrarError(await motivoDe(respuesta, "No se ha podido cambiar la contraseña."));
    } catch {
        mostrarError("No se ha podido contactar con el servidor. Inténtalo de nuevo en unos segundos.");
    } finally {
        botonGuardar.disabled = false;
    }
}

formulario.addEventListener("submit", cambiarClave);

// La página solo se ha servido si hay sesión, así que un 401 aquí quiere decir que se cerró
// mientras estaba abierta: entonces sí, al login.
pintarDatos().then((info) => {
    if (info.caducada) irAlLogin();
});