package edu.xtd.facturacion360.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que envía la pantalla de acceso para iniciar sesión.
 *
 * <p>El campo se llama {@code usuario} y no {@code username} porque así lo llama la columna
 * de la tabla y así se entiende en el mensaje de error. Por dentro Spring Security lo llama
 * de las dos formas, así que el nombre del JSON no afecta a nada más.</p>
 *
 * <p>Los límites de longitud no son un adorno: vienen a poner freno a que alguien mande un
 * cuerpo de dos megabytes para comprobar si la contraseña empieza por {@code a}, y de paso
 *devuelven un 400 con el motivo en vez de un 500.</p>
 *
 * @param usuario nombre de usuario
 * @param clave   contraseña en claro
 */
public record LoginRequest(

		@NotBlank(message = "El usuario es obligatorio")
		@Size(max = 50, message = "El usuario no puede superar 50 caracteres")
		String usuario,

		@NotBlank(message = "La contraseña es obligatoria")
		@Size(max = 200, message = "La contraseña no puede superar 200 caracteres")
		String clave) {

}