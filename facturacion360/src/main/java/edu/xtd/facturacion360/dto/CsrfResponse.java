package edu.xtd.facturacion360.dto;

/**
 * El token de CSRF que el JavaScript necesita para poder escribir.
 *
 * <p>Se pide a {@code GET /auth/csrf} y se manda después en cada petición que modifique algo,
 * dentro de la cabecera que dice este mismo DTO ({@code X-XSRF-TOKEN}). Sin él, Spring
 * rechaza con un 403 cualquier POST, PUT o DELETE: es lo que impide que una página cualquiera
 * de Internet pueda hacer escribir cosas en el servidor con la sesión de quien la tenga
 * abierta en otra pestaña.</p>
 *
 * @param cabecera  nombre de la cabecera en la que hay que mandar el token
 * @param parametro nombre del parámetro de formulario equivalente, por si algún día hace falta
 * @param token     el valor a mandar
 */
public record CsrfResponse(
		String cabecera,
		String parametro,
		String token) {

}