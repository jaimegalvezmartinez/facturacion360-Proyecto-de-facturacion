package edu.xtd.facturacion360.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Que no se accede a nada sin haber iniciado sesión.
 *
 * <p>Es la prueba que respalda la promesa del portal: el front y la API están cerrados. No
 * prueba el login en sí (de eso se ocupa {@code AutenticacionTests}), sino la puerta: quién
 * entra, quién no, y qué pasa cuando alguien que no tiene permiso lo intenta.</p>
 *
 * <p>Levanta la aplicación entera contra una base de datos H2 en memoria, porque lo que se
 * comprueba aquí es cosa de Spring Security —los filtros, las reglas de acceso y los códigos
 * de respuesta— y no de MySQL.</p>
 *
 * @author AngelDanielC0des
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccesoSinSesionTests {

	private static final String CLAVE = "CambiarYa.2026";

	private static final String CABECERA_CSRF = "X-XSRF-TOKEN";

	private static final String COOKIE_CSRF = "XSRF-TOKEN";

	@Autowired
	private MockMvc mockMvc;

	// ── Sin sesión ───────────────────────────────────────────────────────────────

	@Test
	@DisplayName("La API responde 401 con el motivo en JSON si no hay sesión")
	void laApiSinSesionResponde401() throws Exception {

		mockMvc.perform(get("/cliente/listar-pagina").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail", containsString("sesión")));
	}

	@Test
	@DisplayName("Una página sin sesión no se ve: se va al login")
	void unaPaginaSinSesionVaAlLogin() throws Exception {

		mockMvc.perform(get("/clientes.html").accept(MediaType.TEXT_HTML))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login.html"));

		mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login.html"));
	}

	@Test
	@DisplayName("Ningún endpoint de la aplicación se salta la puerta")
	void todosLosEndpointsEstanCerrados() throws Exception {

		// Cada uno de los endpoints que hay, menos el login y el registro. Si mañana se
		// añade uno nuevo, esta lista no lo demuestra: lo que lo demuestra es que la regla
		// del servidor es anyRequest().authenticated(), que no deja pasar a nadie por
		// olvido. La lista está para tener a mano cuál es el conjunto.
		String[] cerrados = {
				"/cliente/listar-pagina",
				"/cliente/provincias",
				"/cliente/1",
				"/emisor",
				"/emisor/logo",
				"/factura/buscar",
				"/factura/trimestral",
				"/verifactu/qr/1",
				"/auth/yo",
				"/factura-imprimir.html"
		};

		for (String ruta : cerrados) {
			mockMvc.perform(get(ruta).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isUnauthorized());
		}
	}

	@Test
	@DisplayName("Escribir sin sesión también es 401, no 403")
	void escribirSinSesionResponde401() throws Exception {

		mockMvc.perform(post("/cliente").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	// ── Con sesión ───────────────────────────────────────────────────────────────

	@Test
	@DisplayName("Con sesión iniciada, la API deja pasar")
	void conSesionLaApiDejaPasar() throws Exception {

		mockMvc.perform(get("/cliente/provincias").session(iniciarSesion("datos")))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Borrar un cliente es cosa de ADMIN y no de USUARIO")
	void borrarSoloEsDeAdmin() throws Exception {

		// El token de CSRF va en las dos peticiones, y no por casualidad: sin él, el
		// borrado se quedaría en el filtro de CSRF y no llegaría nunca a la regla del rol,
		// que es justo lo que este test quiere comprobar.
		mockMvc.perform(conToken(delete("/cliente/1").session(iniciarSesion("datos"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("permiso")));

		MvcResult conAdmin = mockMvc.perform(
						conToken(delete("/cliente/1").session(iniciarSesion("admin"))))
				.andReturn();

		// No se comprueba el código exacto del que sale con ADMIN, sino que NO sea 403:
		// con la base de datos de los tests el borrado puede acabar en un 500 (el cliente
		// tiene facturas y esa tabla no existe aquí) y ese 500 sigue diciendo justo lo que
		// importa, que la diferencia entre los dos usuarios la ha hecho el rol.
		assertThat(conAdmin.getResponse().getStatus()).isNotEqualTo(403);
	}

	@Test
	@DisplayName("Los datos del emisor solo los cambia un ADMIN")
	void losDatosDelEmisorSoloLosCambiaAdmin() throws Exception {

		String cuerpo = """
				{"nombre":"Mi Empresa S.L","cif":"A12345678","direccion":"Calle Mayor 15",
				 "email":"miempresa@test.es","telefono":"900123456"}""";

		mockMvc.perform(conToken(put("/emisor").session(iniciarSesion("datos"))
						.contentType(MediaType.APPLICATION_JSON).content(cuerpo)))
				.andExpect(status().isForbidden());

		mockMvc.perform(conToken(put("/emisor").session(iniciarSesion("admin"))
						.contentType(MediaType.APPLICATION_JSON).content(cuerpo)))
				.andExpect(status().isOk());
	}

	// ── CSRF ─────────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("Una escritura sin token de CSRF se rechaza con 403")
	void escribirSinTokenCsrfSeRechaza() throws Exception {

		mockMvc.perform(post("/cliente").session(iniciarSesion("admin"))
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("pantalla")));
	}

	@Test
	@DisplayName("Con el token de CSRF, la misma escritura pasa de largo")
	void escribirConTokenCsrfPasa() throws Exception {

		mockMvc.perform(conToken(post("/cliente").session(iniciarSesion("admin"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Cliente CSRF","nifCif":"87654321X","direccion":"Calle Mayor 2",
								 "poblacion":"Madrid","provincia":"Madrid"}""")))
				.andExpect(status().isCreated());
	}

	// ── Utilidades ───────────────────────────────────────────────────────────────

	/**
	 * Inicia sesión de verdad (pasa por {@code POST /auth/login}) y devuelve la sesión con su
	 * cookie.
	 *
	 * <p>No se falsea la autenticación a mano con un mock de seguridad: si el login dejara de
	 * funcionar, los tests de la puerta tienen que enterarse, y la única forma es pasando por
	 * él de verdad.</p>
	 *
	 * @param usuario el nombre con el que se entra
	 * @return la sesión ya iniciada
	 */
	private MockHttpSession iniciarSesion(String usuario) throws Exception {

		MvcResult resultado = mockMvc
				.perform(conToken(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"usuario\":\"" + usuario + "\",\"clave\":\"" + CLAVE + "\"}")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario").value(usuario))
				.andReturn();

		return (MockHttpSession) resultado.getRequest().getSession(false);
	}

	/**
	 * Le pone a la petición el token de CSRF, en la cabecera y en la cookie que lo acompaña.
	 *
	 * @param peticion la petición a preparar
	 * @return la misma petición, con el token puesto
	 */
	private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion)
			throws Exception {

		MvcResult resultado = mockMvc
				.perform(get("/auth/csrf").session(new MockHttpSession()))
				.andExpect(status().isOk())
				.andReturn();

		String cuerpo = resultado.getResponse().getContentAsString();

		Matcher token = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(cuerpo);

		if (!token.find()) {
			throw new IllegalStateException("La respuesta de /auth/csrf no trae token: " + cuerpo);
		}

		return peticion
				.header(CABECERA_CSRF, token.group(1))
				.cookie(resultado.getResponse().getCookie(COOKIE_CSRF));
	}

}
