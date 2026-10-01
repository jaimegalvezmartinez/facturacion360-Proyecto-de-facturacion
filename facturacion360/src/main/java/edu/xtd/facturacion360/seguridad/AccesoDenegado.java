package edu.xtd.facturacion360.seguridad;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Qué se responde cuando quien está dentro no tiene permiso para lo que pide.
 *
 * <p>Es un 403 y no un 401, y la diferencia importa: un 401 significaría «no sé quién eres»,
 * y quien esto recibe sí que sabe, porque acaba de entrar. Repitiendo el 401, un USUARIO que
 * topase con un botón de borrado creería que su sesión se había roto, y no volvería a
 * entrar.</p>
 *
 * <p>Ojo con un detalle: a este manejador llegan también los fallos de <em>CSRF</em>, que
 * Spring rechaza antes de que la petición llegue a ningún controlador. Los dos casos
 * comparten el mismo camino, así que el motivo se elige mirando el tipo de excepción
 * ({@link CsrfException}) y no el texto, que es un mensaje de Spring y puede cambiar de una
 * versión a otra.</p>
 *
 * @author AngelDanielC0des
 */
public class AccesoDenegado implements AccessDeniedHandler {

	private static final Logger log = LoggerFactory.getLogger(AccesoDenegado.class);

	private static final String MOTIVO_PERMISOS =
			"No tienes permiso para hacer esta operación. Necesitas ser administrador.";

	private static final String MOTIVO_CSRF =
			"No se ha podido comprobar que la petición venga de esta pantalla. "
			+ "Recarga la página e inténtalo de nuevo.";

	private final RespuestasDeSeguridad respuestas;

	public AccesoDenegado(ObjectMapper objectMapper) {
		this.respuestas = new RespuestasDeSeguridad(objectMapper);
	}

	@Override
	public void handle(HttpServletRequest peticion,
					   HttpServletResponse respuesta,
					   AccessDeniedException denegado) throws IOException {

		// Sin sesión, la respuesta es un 401 aunque lo que Spring haya tirado sea el token
		// de CSRF, y merece su propio párrafo: el filtro de CSRF va ANTES que el de
		// autorización, así que una escritura sin sesión y sin token se cae por el token
		// primero. Decir 403 ahí sería mentir: el problema no es que a alguien le falte
		// permiso, es que no ha dicho quién es. Y el 401 es lo que lleva al login, que es lo
		// que esa persona necesita.
		//
		// Quién es se mira en el contexto de seguridad y NO en peticion.getUserPrincipal(),
		// que en una petición que solo lleva la sesión en el contenedor puede venir a null:
		// ese método solo responde si la petición ha pasado por el filtro que envuelve las
		// peticiones, y no es el caso aquí.
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();

		boolean sinSesion = autenticacion == null
				|| !autenticacion.isAuthenticated()
				|| autenticacion instanceof AnonymousAuthenticationToken;

		if (sinSesion) {

			log.info("Petición sin sesión a {} rechazada por CSRF: se responde 401",
					peticion.getRequestURI());

			respuestas.responder(respuesta, HttpStatus.UNAUTHORIZED,
					RespuestasDeSeguridad.MOTIVO_SIN_SESION);

			return;
		}

		// El motivo se decide por el tipo de excepción y no por su texto, que es un mensaje
		// de Spring y puede cambiar de una versión a otra.
		boolean esCsrf = denegado instanceof CsrfException;

		String motivo = esCsrf ? MOTIVO_CSRF : MOTIVO_PERMISOS;

		log.warn("Acceso denegado a {} por {}: {}",
				peticion.getRequestURI(),
				autenticacion.getName(),
				motivo);

		respuestas.responder(respuesta, HttpStatus.FORBIDDEN, motivo);
	}

}