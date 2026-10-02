package edu.xtd.facturacion360.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.xtd.facturacion360.dto.AltaAdminRequest;
import edu.xtd.facturacion360.dto.BorradoUsuarioRequest;
import edu.xtd.facturacion360.dto.ClaveAdminRequest;
import edu.xtd.facturacion360.dto.EdicionUsuarioRequest;
import edu.xtd.facturacion360.dto.UsuarioAdminResponse;
import edu.xtd.facturacion360.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * El panel de administración de las cuentas: quién puede entrar y con qué permisos.
 *
 * <p>Es la contraparte de {@link AuthController}, que se ocupa de la puerta (entrar, salirse y
 * cambiarse la contraseña propia). Aquí no hay ninguna de esas operaciones: lo que hay es dar
 * de alta cuentas, cambiarles el rol o el estado, y ponerles una contraseña nueva. Las tres
 * cosas las hace un administrador y solo un administrador.</p>
 *
 * <h2>Por qué aquí no hay ni un {@code if} de permisos</h2>
 * <p>Porque el filtro ya ha comprobado eso. En {@code ConfiguracionSeguridad} hay una línea que
 * dice</p>
 * <pre>
 *   .requestMatchers("/usuarios/**").hasRole("ADMIN")
 * </pre>
 * <p>y es la que decide. Un {@code if (esAdmin())} aquí dentro sería una segunda copia de esa
 * regla que se puede quedar vieja sin que nadie se entere: alguien cambiaría la regla de
 * seguridad y estos controladores seguirían exigiendo (o dejando de exigir) lo de antes. Una
 * sola decisión, en un solo sitio, es lo que hace que las dos copias no puedan contradecirse.</p>
 *
 * <p>Lo que sí hay que tener presente es lo que esa regla <em>no</em> cubre: la sesión. Si un
 * USUARIO que ya estaba dentro se convierte en ADMIN, su sesión sigue siendo de USUARIO hasta
 * que vuelva a entrar. Y al revés: bajar a un ADMIN a USUARIO no le quita los permisos de la
 * sesión que tiene abierta, pero sí de la siguiente. Es el comportamiento normal de Spring
 * Security, que copia las autoridades a la autenticación en el momento del acceso, y es
 * preferible a invalidar la sesión entera en cada cambio de rol, que echaría a la gente de la
 * aplicación a mitad de un trabajo.</p>
 *
 * <p>Ninguna de las cuatro operaciones devuelve el hash de la contraseña: el DTO de salida
 * ({@link UsuarioAdminResponse}) no tiene ese campo, y por eso tampoco puede salir por error.
 * Lo que se hashea lo hashea el servicio, y aquí solo se pasan strings.</p>
 *
 * @author AngelDanielC0des
 * @see ConfiguracionSeguridad
 */
@Tag(
	name = "Administración de usuarios",
	description = "Alta de cuentas y cambio de rol, estado y contraseña. Solo ADMIN."
)
@RestController
@RequestMapping("/usuarios")
public class UsuarioAdminController {

	private static final Logger log = LoggerFactory.getLogger(UsuarioAdminController.class);

	private final UsuarioService usuarioService;

	public UsuarioAdminController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	/**
	 * La lista de todas las cuentas.
	 *
	 * <p>Devuelve el DTO de administración, que lleva {@code activo} y las dos fechas, y no el
	 * de {@code /auth/yo}: aquí no se pregunta «quién está dentro» sino «quién existe», y para
	 * eso hace falta saber si una cuenta está desactivada y cuándo entró por última vez.</p>
	 *
	 * @return todas las cuentas, de la más antigua a la más reciente
	 */
	@Operation(
		summary = "Lista todas las cuentas",
		description = "Devuelve nombre, rol, estado y fechas de alta y último acceso de cada cuenta. "
				+ "Nunca el hash de la contraseña."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Lista de cuentas"),
		@ApiResponse(responseCode = "401", description = "No hay sesión iniciada"),
		@ApiResponse(responseCode = "403", description = "La cuenta no es de administrador")
	})
	@GetMapping
	public List<UsuarioAdminResponse> listar() {

		log.info("Listado de cuentas del panel");

		return usuarioService.listar();
	}

	/**
	 * Da de alta una cuenta nueva.
	 *
	 * <p>Es el único punto de toda la aplicación donde una cuenta puede nacer con rol ADMIN. El
	 * registro público ({@code POST /auth/registro}) no tiene campo de rol precisamente para que
	 * esto siga siendo cierto; aquí el rol viene en el cuerpo y es que quien llama es
	 * administrador.</p>
	 *
	 * @param alta usuario, contraseña, nombre y rol
	 * @return 201 con la cuenta creada, sin su hash
	 */
	@Operation(
		summary = "Da de alta una cuenta",
		description = "Crea una cuenta con el rol que se indique. La contraseña se guarda hasheada "
				+ "con BCrypt y no sale nunca por la API."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Cuenta creada"),
		@ApiResponse(responseCode = "400", description = "Datos no válidos"),
		@ApiResponse(responseCode = "401", description = "No hay sesión iniciada"),
		@ApiResponse(responseCode = "403", description = "La cuenta no es de administrador"),
		@ApiResponse(responseCode = "409", description = "Ese usuario ya está dado de alta")
	})
	@PostMapping
	public ResponseEntity<UsuarioAdminResponse> crear(@Valid @RequestBody AltaAdminRequest alta) {

		UsuarioAdminResponse creado = UsuarioAdminResponse.de(usuarioService.alta(alta));

		log.info("Cuenta creada desde el panel: {}", creado.usuario());

		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(creado);
	}

	/**
	 * Cambia el nombre, el rol o el estado de una cuenta.
	 *
	 * <p>El nombre de usuario no se cambia aquí y no hay ningún campo para pedirlo: es la clave
	 * con la que esa persona entra. La contraseña tampoco, que va por su propio endpoint.</p>
	 *
	 * @param idUsuario identificador de la cuenta a modificar
	 * @param edicion  el nombre, el rol y si queda activa
	 * @return la cuenta ya modificada
	 */
	@Operation(
		summary = "Edita una cuenta",
		description = "Cambia el nombre, el rol y el estado (activa o desactivada) de una cuenta. "
				+ "No cambia ni el nombre de usuario ni la contraseña."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Cuenta modificada"),
		@ApiResponse(responseCode = "400", description = "Datos no válidos"),
		@ApiResponse(responseCode = "401", description = "No hay sesión iniciada"),
		@ApiResponse(responseCode = "403", description = "La cuenta no es de administrador, "
				+ "o el cambio dejaría la aplicación sin administradores"),
		@ApiResponse(responseCode = "404", description = "No hay ninguna cuenta con ese identificador")
	})
	@PutMapping("/{idUsuario}")
	public UsuarioAdminResponse editar(@PathVariable int idUsuario,
									   @Valid @RequestBody EdicionUsuarioRequest edicion) {

		UsuarioAdminResponse guardado = usuarioService.editar(idUsuario, edicion);

		log.info("Cuenta editada desde el panel: {}", guardado.usuario());

		return guardado;
	}

	/**
	 * Le pone una contraseña nueva a una cuenta.
	 *
	 * <p>No se pide la anterior, y no es un descuido: quien está dentro ya ha demostrado ser
	 * administrador con su propia sesión, y esto es la operación que existe justamente para
	 * abrirle la cuenta a quien la ha perdido. Para cambiarse la contraseña a uno mismo está
	 * {@code PUT /auth/clave}, que sí la pide.</p>
	 *
	 * <p>No se cierra la sesión de esa persona: si la tenía abierta, sigue abierta. Lo que sí
	 * cambia es que con la contraseña antigua ya no puede volver a entrar.</p>
	 *
	 * @param idUsuario identificador de la cuenta
	 * @param clave     la contraseña nueva
	 * @return la cuenta ya modificada
	 */
	@Operation(
		summary = "Pone una contraseña nueva a una cuenta",
		description = "Sustituye la contraseña de la cuenta indicada sin pedir la anterior. "
				+ "Lo que se guarda es un hash BCrypt nuevo."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Contraseña cambiada"),
		@ApiResponse(responseCode = "400", description = "La contraseña no cumple los requisitos"),
		@ApiResponse(responseCode = "401", description = "No hay sesión iniciada"),
		@ApiResponse(responseCode = "403", description = "La cuenta no es de administrador"),
		@ApiResponse(responseCode = "404", description = "No hay ninguna cuenta con ese identificador")
	})
	@PutMapping("/{idUsuario}/clave")
	public UsuarioAdminResponse ponerClave(@PathVariable int idUsuario,
										   @Valid @RequestBody ClaveAdminRequest clave) {

		UsuarioAdminResponse guardado = usuarioService.ponerClave(idUsuario, clave.nueva());

		log.info("Contraseña puesta desde el panel a la cuenta {}", guardado.usuario());

		return guardado;
	}

	/**
	 * Borra una cuenta.
	 *
	 * <p>El cuerpo es la <strong>contraseña de quien está haciendo el borrado</strong>, no la de
	 * la cuenta que se borra. Es lo único que separa «estoy delante del teclado» de «he dejado el
	 * navegador abierto»: con la sesión sola, cualquiera que se sentara en ese equipo podría
	 * borrar cuentas de la aplicación.</p>
	 *
	 * <p>Y no se puede borrar la propia cuenta. Quien lo hace se queda sin sesión en el mismo
	 * instante, y si además era el último administrador, fuera del panel. Hay un endpoint para
	 * todo lo demás que se le pueda ocurrir hacer con su cuenta, y este no está entre ellos.</p>
	 *
	 * <p>Un 204 sin cuerpo, que es lo que espera el JavaScript antes de repintar la tabla.
	 * Borrar no se puede deshacer, así que no devuelve la cuenta que ha borrado: es que no la
	 * hay.</p>
	 *
	 * @param idUsuario identificador de la cuenta a borrar
	 * @param borrado   la contraseña de quien está dentro
	 * @param sesion    la autenticación de la petición, de donde sale ese nombre
	 */
	@Operation(
		summary = "Borra una cuenta",
		description = "Elimina la cuenta indicada. Hay que enviar la contraseña del administrador "
				+ "que está haciendo el borrado. No se puede borrar la propia cuenta ni la última "
				+ "que quede con permisos de administrador."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "Cuenta borrada"),
		@ApiResponse(responseCode = "400", description = "La contraseña de confirmación no es correcta"),
		@ApiResponse(responseCode = "401", description = "No hay sesión iniciada"),
		@ApiResponse(responseCode = "403", description = "La cuenta no es de administrador"),
		@ApiResponse(responseCode = "404", description = "No hay ninguna cuenta con ese identificador"),
		@ApiResponse(responseCode = "409", description = "Sería la última cuenta con permisos de "
				+ "administrador, o es la propia cuenta de quien lo intenta")
	})
	@DeleteMapping("/{idUsuario}")
	public ResponseEntity<Void> borrar(@PathVariable int idUsuario,
									   @Valid @RequestBody BorradoUsuarioRequest borrado,
									   Authentication sesion) {

		usuarioService.borrar(idUsuario, sesion.getName(), borrado.clave());

		log.info("Cuenta borrada desde el panel: {}", idUsuario);

		return ResponseEntity.noContent().build();
	}

}
