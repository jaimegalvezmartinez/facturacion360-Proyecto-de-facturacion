package edu.xtd.facturacion360.dto;

import java.time.LocalDateTime;

/**
 * Una fila de la tabla {@code usuarios}: el portal de acceso.
 *
 * <p>Es el único DTO del proyecto que lleva dentro el hash de la contraseña, y a propósito
 * no tiene ningún compañero de salida. Lo que sale por la API es {@link UsuarioResponse}, que
 * no tiene este campo. Así no es posible devolverlo por error: para que un hash saliera por
 * un endpoint habría que escribir su nombre a mano.</p>
 *
 * <p>La contraseña se guarda <strong>hasheada con BCrypt</strong>, nunca en claro. BCrypt
 * guarda el resultado de miles de vueltas de una función de derivación con sal propia, así
 * que dos usuarios con la misma contraseña tienen hashes distintos y el ataque con tablas
 * precalculadas (rainbow tables) no sirve. Comprobar la contraseña es, por tanto, volver a
 * hashear lo que se teclea y comparar los dos resultados, y eso lo hace el codificador de
 * Spring Security, no esta clase.</p>
 *
 * @param idUsuario    clave primaria de la tabla
 * @param usuario      el nombre con el que se inicia sesión (único)
 * @param claveHash    la contraseña hasheada con BCrypt, en formato modular de Spring
 * @param nombre       nombre y apellidos de la persona, solo para mostrarlo
 * @param rol          perfil de permisos
 * @param activo       {@code false} deja al usuario existir pero sin poder entrar
 * @param fechaAlta    momento del registro
 * @param ultimoAcceso momento del último inicio de sesión correcto, {@code null} si nunca ha entrado
 */
public record Usuario(
		int idUsuario,
		String usuario,
		String claveHash,
		String nombre,
		Rol rol,
		boolean activo,
		LocalDateTime fechaAlta,
		LocalDateTime ultimoAcceso) {

}