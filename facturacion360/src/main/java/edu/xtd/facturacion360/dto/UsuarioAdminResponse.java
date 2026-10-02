package edu.xtd.facturacion360.dto;

import java.time.LocalDateTime;

/**
 * Un usuario tal y como lo ve el <strong>panel de administración</strong>.
 *
 * <p>Es un record aparte de {@link UsuarioResponse} y no una ampliación de él, por dos razones
 * que conviene tener presentes:</p>
 * <ul>
 *   <li>Ninguno de los dos tiene un campo para el hash, así que <strong>no hay forma de que la
 *       contraseña salga</strong> por este endpoint: no hay ni siquiera un sitio donde escribir su
 *       nombre por error.</li>
 *   <li>{@link UsuarioResponse} es lo que devuelve {@code /auth/yo} y {@code /auth/login}, y
 *       esos dos los lee JavaScript que no sabe nada de paneles. Si se les añadiera
 *       {@code activo} y las fechas, cada pantalla que pinta un usuario tendría que decidir qué
 *       hacer con ellas.</li>
 * </ul>
 *
 * @param idUsuario     clave primaria de la tabla
 * @param usuario       nombre con el que inicia sesión
 * @param nombre        nombre y apellidos de la persona
 * @param rol           perfil de permisos
 * @param activo        {@code false} si la cuenta existe pero no puede entrar
 * @param fechaAlta     momento del alta de la cuenta
 * @param ultimoAcceso  momento del último acceso correcto, {@code null} si nunca ha entrado
 */
public record UsuarioAdminResponse(

		int idUsuario,
		String usuario,
		String nombre,
		Rol rol,
		boolean activo,
		LocalDateTime fechaAlta,
		LocalDateTime ultimoAcceso) {

	/**
	 * Traduce la fila de la base de datos al DTO de salida.
	 *
	 * <p>Es el único punto por el que un {@link Usuario} —que sí lleva el hash— se convierte en
	 * algo serializable, y no copia ningún campo de la contraseña. Por eso el hash no aparece en
	 * la respuesta ni aunque alguien añada un campo más abajo.</p>
	 *
	 * @param usuario la fila leída de la base de datos
	 * @return el DTO que sale por la API
	 */
	public static UsuarioAdminResponse de(Usuario usuario) {

		return new UsuarioAdminResponse(
				usuario.idUsuario(),
				usuario.usuario(),
				usuario.nombre(),
				usuario.rol(),
				usuario.activo(),
				usuario.fechaAlta(),
				usuario.ultimoAcceso());
	}

}
