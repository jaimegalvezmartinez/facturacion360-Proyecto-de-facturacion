package edu.xtd.facturacion360.seguridad;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Qué se responde cuando llega una petición sin sesión iniciada.
 *
 * <p>Spring Security llama a este punto de entrada en cuanto ve que hace falta autenticación
 * y no la hay. La respuesta cambia según quién pregunta, y esa diferencia es lo que evita que
 * el portal sea inservible:</p>
 *
 * <ul>
 *   <li>Una <strong>página</strong> ({@code Accept: text/html}, o una ruta terminada en
 *       {@code .html}) recibe un 302 a la pantalla de login. Redirigir y no contestar un 401
 *       con JSON es lo que hace que al abrir la dirección de inicio sin sesión aparezca el
 *       formulario de acceso en lugar de un bloque de texto.</li>
 *   <li>Una <strong>llamada a la API</strong> ({@code fetch}) recibe un 401 con el mismo
 *       {@link ProblemDetail} que usa el resto de errores del proyecto, y no una
 *       redirección: un 302 en un fetch se traduce en una respuesta opaca, y el JavaScript se
 *       quedaría esperando un cuerpo que no llega.</li>
 * </ul>
 *
 * <p>Las dos respuestas llevan el mismo motivo y ninguna dice si el usuario existe o cuál es
 * su rol.</p>
 *
 * @author AngelDanielC0des
 */
public class PuntoEntradaNoAutenticada implements AuthenticationEntryPoint {

	private static final Logger log = LoggerFactory.getLogger(PuntoEntradaNoAutenticada.class);

	/** A dónde se manda a quien intente abrir una página sin haber entrado. */
	public static final String RUTA_LOGIN = "/login.html";

	private static final String MOTIVO = RespuestasDeSeguridad.MOTIVO_SIN_SESION;

	private final RespuestasDeSeguridad respuestas;

	public PuntoEntradaNoAutenticada(ObjectMapper objectMapper) {
		this.respuestas = new RespuestasDeSeguridad(objectMapper);
	}

	@Override
	public void commence(HttpServletRequest peticion,
						 HttpServletResponse respuesta,
						 AuthenticationException autenticacion) throws IOException {

		if (pideUnaPagina(peticion)) {

			log.info("Petición sin sesión a {}: se redirige al login", peticion.getRequestURI());

			respuestas.redirigir(respuesta, peticion, RUTA_LOGIN);

			return;
		}

		log.info("Petición sin sesión a {}: se responde 401", peticion.getRequestURI());

		respuestas.responder(respuesta, HttpStatus.UNAUTHORIZED, MOTIVO);
	}

	/**
	 * ¿Lo que ha llegado es alguien abriendo una página en el navegador?
	 *
	 * <p>Se mira la cabecera {@code Accept}, que el navegador pone a {@code text/html} cuando
	 * navega y a un asterisco seguido de barra (lo que manda el JavaScript del {@code fetch})
	 * cuando la llamada la hace la pantalla. Se mira además la extensión de la ruta, porque hay
	 * clientes que no rellenan esa cabecera y en ese caso, de no mirarla, al abrir
	 * {@code /clientes} se vería un JSON en vez del login.</p>
	 *
	 * @param peticion la petición que ha llegado
	 * @return true si hay que redirigir a la pantalla de acceso
	 */
	private boolean pideUnaPagina(HttpServletRequest peticion) {

		String accept = peticion.getHeader("Accept");

		// Si el cliente dice claramente qué tipo de cosa quiere, a eso se le hace caso. Un
		// fetch pone "application/json" y una navegación de navegador pone "text/html": los
		// dos tienen camino propio, y el segundo solo se mira cuando el primero no ha dicho
		// nada, que es lo que pasa con los curl y con algunos clientes viejos.
		if (accept != null) {

			if (accept.contains(MediaType.TEXT_HTML_VALUE)) {
				return true;
			}

			if (accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
				return false;
			}
		}

		// Sin pista en la cabecera, la extensión de la ruta decide. Sin esto, al abrir
		// /factura-imprimir.html a pelo se vería un JSON en vez del login.
		return peticion.getRequestURI().endsWith(".html")
				|| "/".equals(peticion.getRequestURI());
	}

}