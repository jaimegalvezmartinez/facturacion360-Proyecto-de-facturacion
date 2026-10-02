package edu.xtd.facturacion360.seguridad;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * La puerta de entrada del portal: aquí se decide qué se puede ver sin haber iniciado sesión.
 *
 * <p>Es la única clase de la aplicación que sabe de Spring Security, y a propósito no hay
 * ninguna otra que compruebe permisos. La regla que se quiere dejar escrita es esta: <strong>un
 * endpoint no protegido no es un endpoint protegido por el que nadie pasa, es un endpoint
 * abierto</strong>. Por eso lo que hay a continuación usa {@code anyRequest().authenticated()}
 * como regla general, y las excepciones se van añadiendo hacia arriba. Una pantalla nueva
 * sale protegida por omisión, que es la única forma de que nadie se olvide de ella.</p>
 *
 * <h2>Qué se puede ver sin entrar</h2>
 * <ul>
 *   <li>La pantalla de acceso ({@code /login.html}) y su JavaScript y su hoja de estilos.</li>
 *   <li>Los recursos estáticos ({@code /js/**}, {@code /css/**}, {@code /img/**} y las
 *       hojas sueltas): sin ellos, ni el propio login se vería.</li>
 *   <li>Los dos endpoints de la puerta: iniciar sesión y registrarse.</li>
 * </ul>
 * <p>Nada más. En concreto, las páginas ({@code index.html}, {@code clientes.html},
 * {@code facturas.html}, {@code perfil.html}, {@code ayuda.html}), los PDF y
 * <strong>toda</strong> la API ({@code /cliente}, {@code /factura}, {@code /emisor})
 * exigen sesión iniciada.</p>
 *
 * <h2>Qué decide el rol</h2>
 * <p>Solo dos operaciones, y las dos por una misma razón: son las que tocan datos que no se
 * pueden recuperar:</p>
 * <ul>
 *   <li>{@code DELETE /cliente/{id}} → ADMIN. Borrar no se puede deshacer.</li>
 *   <li>{@code PUT /emisor} → ADMIN. Es la razón social, el NIF y el domicilio que salen
 *       impresos en las facturas: no es un dato cualquiera de un perfil, es un dato
 *       fiscales.</li>
 * </ul>
 *
 * <h2>Por qué no hay login por formulario de Spring</h2>
 * <p>El login es {@code POST /auth/login}, un endpoint JSON de este proyecto, porque todas las
 * pantallas son HTML estático servido por el propio Spring Boot y no hay servidor de
 * plantillas al que enviarle un formulario. Por eso el login por formulario y el acceso por
 * HTTP Basic están desactivados: si no, Spring montaría su propia página de login en
 * {@code /login} y su diálogo de usuario y contraseña, que nadie vería nunca y que además
 * devolverían 401 en formato HTML a unas llamadas que esperan JSON.</p>
 *
 * @author AngelDanielC0des
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

	/**
	 * Las hojas de estilo están sueltas en la raíz de {@code static} (no hay carpeta
	 * {@code css}) porque las usan también las plantillas del PDF.
	 */
	private static final String[] ESTATICOS = {
			"/js/**",
			"/css/**",
			"/img/**",
			"/*.css",
			"/*.js",
			"/*.ico",
			"/favicon.ico"
	};

	/** El acceso y el registro: son las dos únicas puertas abiertas. */
	private static final String[] ABIERTAS = {
			"/login.html",
			"/auth/login",
			"/auth/registro",
			"/auth/csrf",
			"/error"
	};

	@Autowired
	private ObjectMapper objectMapper;

	/**
	 * El codificador de contraseñas.
	 *
	 * <p>BCrypt, y no MD5, SHA-1 ni SHA-256, que es lo que suelen poner en tutorials: esos
	 * tres son rapidísimos y eso es exactamente lo contrario de lo que se busca, porque
	 * quien roba la tabla de usuarios puede probar miles de millones de contraseñas por
	 * segundo contra ellas. BCrypt es lento a propósito y lleva su propia sal, así que dos
	 * usuarios con la misma contraseña tienen hashes distintos y un ataque con tablas
	 * precalculadas no sirve.</p>
	 *
	 * @return el codificador que usarán el registro y la comprobación de acceso
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * El gestor de autenticaciones, que es quien sabe comprobar un usuario y una contraseña.
	 *
	 * <p>Lo declara Spring Boot por debajo cuando hay un {@code UserDetailsService} y un
	 * {@code PasswordEncoder} en el contexto, pero {@code AuthController} lo necesita
	 * inyectado para poder autenticarse, y no se puede inyectar algo que no es un bean. De
	 * aquí el bean explícito.</p>
	 *
	 * @param configuracion la configuración de autenticación de Spring
	 * @return el gestor de autenticaciones del contexto
	 * @throws Exception si la configuración de Spring no se puede construir
	 */
	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration configuracion)
			throws Exception {
		return configuracion.getAuthenticationManager();
	}

	/**
	 * La estrategia de sesión que hay que ejecutar a mano tras autenticar.
	 *
	 * <p>Su trabajo importante es el <em>rotado del identificador de sesión</em>: al entrar,
	 * la sesión vieja cambia de identificador en lugar de seguir siendo el mismo, para que uno
	 * robado antes de entrar no sirva después. Spring lo hace solo cuando el login es suyo;
	 * como aquí el login es un endpoint normal, hay que ejecutarlo a mano en el mismo
	 * momento en que se guarda la autenticación en el contexto.</p>
	 *
	 * <p>Es exactamente la misma de la que usa Spring por su cuenta
	 * ({@code ChangeSessionIdAuthenticationStrategy}, la que sale con la protección contra
	 * el secuestro de sesión), declarada aquí a mano y no rescatada de la cadena de
	 * seguridad porque esa todavía no está construida cuando se crean los beans.</p>
	 *
	 * @return la estrategia que hay que ejecutar al completar el acceso
	 */
	@Bean
	public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
		return new CompositeSessionAuthenticationStrategy(
				List.of(new ChangeSessionIdAuthenticationStrategy()));
	}

	/**
	 * La cadena de seguridad: el orden de filtros y las reglas de acceso.
	 *
	 * @param http la cadena en construcción
	 * @return la cadena ya montada
	 * @throws Exception si Spring no puede construir la cadena
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

		http
				.authorizeHttpRequests(reglas -> reglas
						// Las reglas de admin van ANTES que las de autenticación. Si fuera al
						// revés, /cliente/1 en DELETE caería en la regla general "tengo que
						// estar dentro", que se cumpliría, y la regla del rol no llegaría a
						// evaluarse: el borrado lo haría cualquier USUARIO.
						.requestMatchers(HttpMethod.DELETE, "/cliente/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/emisor", "/emisor/**").hasRole("ADMIN")

						.requestMatchers(ABIERTAS).permitAll()
						.requestMatchers(ESTATICOS).permitAll()

						// Todo lo demás, dentro. Incluye las páginas HTML, los PDF y la API
						// entera: una pantalla nueva nace protegida sin que nadie se acuerde
						// de añadirla a ninguna lista.
						.anyRequest().authenticated())

				.exceptionHandling(manejadores -> manejadores
						.authenticationEntryPoint(new PuntoEntradaNoAutenticada(objectMapper))
						.accessDeniedHandler(new AccesoDenegado(objectMapper)))

				// Sin formulario de login: el acceso es POST /auth/login. Ver la nota de la
				// clase antes de volver a activarlo.
				.formLogin(formulario -> formulario.disable())
				.httpBasic(httpBasico -> httpBasico.disable())

				.sessionManagement(sesion -> sesion
						.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

				// El contexto de seguridad se guarda a mano en AuthController, al final del
				// acceso, y no al terminar cada petición como se hacía antes. Es el
				// requireExplicitSave, y lo que permite es justamente eso: decidir el
				// momento en que se abre la sesión, que en un login propio es un momento
				// exacto y no el final de la petición.
				.securityContext(contexto -> contexto.requireExplicitSave(true))

				// CSRF activo. El token va en una cookie que el JavaScript puede leer y lo
				// devuelve GET /auth/csrf; luego cada petición que escribe lo manda en la
				// cabecera X-XSRF-TOKEN. Sin esto, cualquier página de Internet podría
				// mandar un POST a este servidor con la sesión de quien la tenga abierta.
				.csrf(csrf -> csrf
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
						// Sin la versión que XOR-enmascara el token: aquí el token no se
						// escribe en ninguna plantilla, se copia tal cual de la respuesta a
						// la cabecera, y enmascararlo solo añadiría una regla más que
						// recordar sin aportar nada.
						.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))

				// El cierre de sesión también lo pone Spring, para que no haya dos caminos
				// distintos de invalidar una sesión. Es un POST, y por eso lleva CSRF como
				// cualquier otra escritura. Responde un 204 sin cuerpo, que es lo que espera
				// el JavaScript antes de llevar a la persona al login.
				.logout(salir -> salir
						.logoutUrl("/auth/logout")
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies("JSESSIONID")
						.logoutSuccessHandler((peticion, respuesta, autenticacion) ->
								respuesta.setStatus(HttpServletResponse.SC_NO_CONTENT)));

		return http.build();
	}

}