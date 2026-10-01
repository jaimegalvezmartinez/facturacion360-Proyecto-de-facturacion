package edu.xtd.facturacion360.seguridad;

import java.io.IOException;

import tools.jackson.core.JacksonException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

// Jackson 3 (tools.jackson), que es el que usa Spring Boot 4 para convertir a JSON.
// El paquete com.fasterxml.jackson.databind.ObjectMapper también está en el classpath
// (lo trae otra dependencia) pero NO es ningún bean del contexto, así que pedirlo por
// ese nombre falla al arrancar.
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Escribe en JSON los dos errores que produce la capa de seguridad: el 401 de quien no ha
 * iniciado sesión y el 403 de quien no tiene permiso.
 *
 * <p>Está aparte porque los dos los necesita y porque su formato tiene que ser el mismo que
 * el de {@code ManejadorExcepciones}: un {@link ProblemDetail} (RFC 9457) con el motivo en
 * {@code detail}. Si el 403 saliera como texto plano y el 400 como ProblemDetail, el frontend
 * tendría que leer los tres formatos por separado, que es justo lo que este proyecto lleva
 * evitandolo desde el principio.</p>
 *
 * @author AngelDanielC0des
 */
class RespuestasDeSeguridad {

	private static final Logger log = LoggerFactory.getLogger(RespuestasDeSeguridad.class);

	/**
	 * El motivo del 401, el mismo sin importar si la petición se paró en el filtro de
	 * autorización o en el de CSRF.
	 *
	 * <p>Vive aquí y no en cada manejador porque los dos lo necesitan y porque tienen que
	 * decir exactamente lo mismo: si uno dijera «tu sesión ha caducado» y el otro «no
	 * tienes permiso», quien se encontrara con los dos leería dos problemas donde solo hay
	 * uno.</p>
	 */
	public static final String MOTIVO_SIN_SESION =
			"No has iniciado sesión o la sesión ha caducado. Vuelve a entrar.";

	private final ObjectMapper objectMapper;

	RespuestasDeSeguridad(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * Responde un error con el cuerpo de {@link ProblemDetail}.
	 *
	 * @param respuesta la respuesta HTTP que hay que rellenar
	 * @param estado    el código que se devuelve
	 * @param detalle   el motivo, redactado para que lo lea una persona
	 */
	void responder(HttpServletResponse respuesta, HttpStatus estado, String detalle) {

		ProblemDetail cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);
		cuerpo.setTitle(estado.getReasonPhrase());

		try {

			respuesta.setStatus(estado.value());
			respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			respuesta.setCharacterEncoding("UTF-8");
			respuesta.getWriter().write(objectMapper.writeValueAsString(cuerpo));

		} catch (IOException | JacksonException e) {

			// Si ni el cuerpo sale, el cliente se queda con un 401 o un 403 sin explicación.
			// Es preferible a relanzar: desde un manejador de seguridad, una excepción
			// acaba siendo una página de error del contenedor, que además no sería JSON y
			// rompería al JavaScript que la está leyendo.
			log.error("No se ha podido escribir la respuesta de seguridad", e);
		}
	}

	/**
	 * Manda al login a quien intenta abrir una página sin sesión.
	 *
	 * <p>Se concatena el contexto de la aplicación porque un día la aplicación puede
	 * desplegarse con un prefijo de ruta ({@code /facturacion360}) y un 302 a la ruta
	 * absoluta se quedaría fuera sin avisar.</p>
	 *
	 * @param respuesta la respuesta HTTP que hay que rellenar
	 * @param peticion  la petición que ha llegado
	 * @param ruta      la ruta a la que se redirige
	 */
	void redirigir(HttpServletResponse respuesta, HttpServletRequest peticion, String ruta) {
		try {
			respuesta.sendRedirect(peticion.getContextPath() + ruta);
		} catch (IOException e) {
			log.error("No se ha podido redirigir a {}", ruta, e);
		}
	}

}