package edu.xtd.facturacion360.dto;

/**
 * Un usuario tal y como lo devuelve la API.
 *
 * <p>Nada de lo que sale por la API lleva el hash de la contraseña. Ni el nombre de la
 * columna aparece en este record, de modo que serializarlo ni siquiera es posible por
 * descuido: hay que escribir {@code claveHash} a mano para que se salga, y eso ya es una
 * decisión, no un olvido.</p>
 *
 * @param idUsuario identificador único del usuario
 * @param usuario   nombre con el que inicia sesión
 * @param nombre    nombre y apellidos de la persona
 * @param rol       perfil de permisos
 */
public record UsuarioResponse(
		int idUsuario,
		String usuario,
		String nombre,
		Rol rol) {

}