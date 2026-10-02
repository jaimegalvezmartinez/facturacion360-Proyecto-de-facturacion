package edu.xtd.facturacion360.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.transaction.TransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import edu.xtd.facturacion360.repository.ClienteRepository;
import edu.xtd.facturacion360.repository.FacturaRepository;
import edu.xtd.facturacion360.repository.UsuarioRepository;
import edu.xtd.facturacion360.service.UsuarioService;

/**
 * El único sitio donde se decide qué responde la API cuando algo sale mal.
 *
 * Extiende {@link ResponseEntityExceptionHandler} a propósito, y no es un detalle: esa clase
 * ya resuelve las excepciones que lanza el propio Spring —JSON malformado, método no
 * soportado, ruta que no existe, parámetro que falta— y las devuelve ya como
 * {@link ProblemDetail}. Escribirlas a mano habría sido repetir lo que el framework hace
 * mejor. Aquí abajo solo van las que Spring no puede conocer porque son de este proyecto.
 *
 * <p>El cuerpo de TODOS los errores es un {@code ProblemDetail} (RFC 9457): el mismo formato
 * que Spring Boot devuelve por defecto, el que springdoc documenta solo en Swagger, y el que
 * el frontend lee en su campo {@code detail}. Antes convivían cuatro formas distintas —texto
 * plano, un mapa, un ApiResponseDto y, en quince sitios, un cuerpo vacío—, así que quien
 * consumía la API tenía que adivinar cuál le tocaba en cada endpoint.</p>
 *
 * <p>Regla que conviene no romper: el cliente recibe un motivo en su idioma y NUNCA una
 * traza. La traza va al log, que es donde sirve para algo.</p>
 */
@RestControllerAdvice
public class ManejadorExcepciones extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ManejadorExcepciones.class);

	/**
	 * Arma el cuerpo de un error.
	 *
	 * @param estado  el código HTTP que se devuelve
	 * @param detalle el motivo, redactado para que lo lea una persona
	 * @return el cuerpo, listo para devolver
	 */
	private static ProblemDetail problema(HttpStatusCode estado, String detalle) {
		ProblemDetail cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);

		// resolve() y no valueOf(): valueOf lanza IllegalArgumentException con un codigo que
		// no sea de los estandar, y lanzar DESDE AQUI es lo peor que puede pasar, porque se
		// pierde el error original y el contenedor acaba respondiendo con su pagina por
		// defecto. resolve() devuelve null y nos quedamos sin titulo, que no es grave.
		HttpStatus conocido = HttpStatus.resolve(estado.value());

		if (conocido != null) {
			cuerpo.setTitle(conocido.getReasonPhrase());
		}

		return cuerpo;
	}

	/**
	 * El NIF/CIF de un cliente que ya está en la base de datos.
	 *
	 * <p>Va antes que el manejador genérico de duplicados a propósito: los dos responden 409,
	 * pero este puede decir QUÉ campo ha chocado. El repositorio ya ha mirado el nombre del
	 * índice para poder lanzarlo, así que aquí solo hay que dar su motivo.</p>
	 *
	 * @param excepcion la colisión, ya traducida por el repositorio
	 * @return 409 nombrando el campo
	 */
	@ExceptionHandler(ClienteRepository.NifCifDuplicadoException.class)
	public ProblemDetail gestionarNifDuplicado(ClienteRepository.NifCifDuplicadoException excepcion) {
		log.warn("NIF/CIF repetido al guardar un cliente");
		return problema(HttpStatus.CONFLICT, excepcion.getMessage());
	}

	/**
	 * El cliente que no se puede borrar porque tiene facturas.
	 *
	 * @param excepcion el borrado bloqueado, ya traducido por el repositorio
	 * @return 409 diciendo que son facturas
	 */
	@ExceptionHandler(ClienteRepository.ClienteConFacturasException.class)
	public ProblemDetail gestionarClienteConFacturas(
			ClienteRepository.ClienteConFacturasException excepcion) {
		log.warn("Se ha intentado borrar un cliente que tiene facturas");
		return problema(HttpStatus.CONFLICT, excepcion.getMessage());
	}

	/**
	 * Se factura a un cliente que ya no está.
	 *
	 * <p>409 y no 400, y conviene dejar escrito por qué. Se puede argumentar que el problema
	 * está en lo enviado —el identificador no corresponde a nada— y responder 400. Pero quien
	 * factura no escribió ese número: lo eligió de una lista donde estaba. Lo que ha pasado es
	 * que alguien borró el cliente por debajo, y eso es un <em>conflicto con el estado
	 * actual</em>, que es exactamente lo que significa un 409.</p>
	 *
	 * <p>Y hay una razón práctica que pesa más: este caso <strong>ya salía como 409</strong>
	 * antes, por el manejador genérico de integridad, y el formulario de facturas lo trata como
	 * tal («…o el cliente ya no estar disponible»). Lo que esta clase arregla es el mensaje, que
	 * decía que sobraban datos relacionados cuando lo que falta es el cliente. Cambiar además
	 * el código rompería a quien ya lo consume sin ganar nada.</p>
	 *
	 * @param excepcion la clave ajena huérfana, ya traducida por el repositorio
	 * @return 409 diciendo que el cliente ya no existe
	 */
	@ExceptionHandler(FacturaRepository.ClienteInexistenteException.class)
	public ProblemDetail gestionarClienteInexistente(
			FacturaRepository.ClienteInexistenteException excepcion) {
		log.warn("Se ha intentado facturar a un cliente que ya no existe");
		return problema(HttpStatus.CONFLICT, excepcion.getMessage());
	}

	/**
	 * El acceso al portal con un usuario o una contraseña que no cuadran.
	 *
	 * <p>Un 401, y un motivo que no dice cuál de las dos cosas falló. Es la diferencia entre
	 * «tu contraseña está mal» y «ese usuario no está dado de alta»: la primera ayuda a quien
	 * se ha equivocado, y la segunda le dice a quien está probando nombres que ese nombre
	 * existe. Aquí no se da ese dato, y el motivo es el mismo en los dos casos.</p>
	 *
	 * <p>Sin este manejador, la excepción caería en el de abajo y saldría como un 500, que
	 * además de no ser verdad le diría a quien está probando que el fallo está en el
	 * servidor.</p>
	 *
	 * @param excepcion el rechazo de Spring Security
	 * @return 401 con un motivo neutro
	 */
	@ExceptionHandler(BadCredentialsException.class)
	public ProblemDetail gestionarCredenciales(BadCredentialsException excepcion) {
		log.warn("Intento de acceso rechazado: usuario o contraseña incorrectos");
		return problema(HttpStatus.UNAUTHORIZED, "El usuario o la contraseña no son correctos");
	}

	/**
	 * El acceso a una cuenta que existe pero está desactivada.
	 *
	 * <p>También 401 con el mismo motivo neutro que una contraseña incorrecta, y por el mismo
	 * motivo: si se dijera «esa cuenta está desactivada», el nombre quedaría confirmado. El
	 * log sí lo dice, que es donde hace falta.</p>
	 *
	 * @param excepcion el rechazo de Spring Security
	 * @return 401 con un motivo neutro
	 */
	@ExceptionHandler(DisabledException.class)
	public ProblemDetail gestionarCuentaDesactivada(DisabledException excepcion) {
		log.warn("Intento de acceso a una cuenta desactivada");
		return problema(HttpStatus.UNAUTHORIZED, "El usuario o la contraseña no son correctos");
	}

	/**
	 * Un usuario con el mismo nombre intenta darse de alta dos veces.
	 *
	 * <p>Va antes que el manejador genérico de duplicados, que también responde 409, pero sin
	 * decir qué campo ha chocado. Aquí sí se dice.</p>
	 *
	 * @param excepcion la colisión, ya traducida por el repositorio
	 * @return 409 nombrando el usuario
	 */
	@ExceptionHandler(UsuarioRepository.UsuarioDuplicadoException.class)
	public ProblemDetail gestionarUsuarioDuplicado(
			UsuarioRepository.UsuarioDuplicadoException excepcion) {
		log.warn("Alta de usuario repetida");
		return problema(HttpStatus.CONFLICT, excepcion.getMessage());
	}

	/**
	 * Quién está dentro no tiene permiso para lo que pide.
	 *
	 * <p>403 y no 401: la sesión existe, lo que no es permiso suficiente. Casi nunca llega
	 * aquí — las reglas de rol están en la cadena de seguridad y las resuelve
	 * {@code AccesoDenegado}— pero un {@code @PreAuthorize} en un servicio sí lanzaría esta
	 * excepción dentro del controlador, y sin este manejador caería en el de abajo y saldría
	 * como 500.</p>
	 *
	 * @param excepcion el rechazo
	 * @return 403 con un motivo neutro
	 */
	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail gestionarAccesoDenegado(AccessDeniedException excepcion) {
		log.warn("Acceso denegado: {}", excepcion.getMessage());
		return problema(HttpStatus.FORBIDDEN,
				"No tienes permiso para hacer esta operación");
	}

	/**
	 * Un cambio de contraseña con la contraseña actual equivocada.
	 *
	 * <p>400 y no 401: la sesión es válida (si no, la petición ni siquiera habría llegado al
	 * servicio), lo que está mal es lo que se ha enviado. Un 401 aquí mandaría al login, que
	 * sería desconcertante, porque en el login la contraseña que se teclea sí es la
	 * correcta.</p>
	 *
	 * @param excepcion el rechazo del servicio, ya traducido
	 * @return 400 diciendo que la actual no cuadra
	 */
	@ExceptionHandler(UsuarioService.ClaveActualIncorrectaException.class)
	public ProblemDetail gestionarClaveActualIncorrecta(
			UsuarioService.ClaveActualIncorrectaException excepcion) {
		log.warn("Cambio de contraseña rechazado: la contraseña actual no es la correcta");
		return problema(HttpStatus.BAD_REQUEST, excepcion.getMessage());
	}

	/**
	 * El panel de administración ha pedido una cuenta que no existe.
	 *
	 * <p>Un 404 y no un 400: lo que no encaja no es lo que se ha enviado en el cuerpo, es el
	 * identificador de la ruta, que apunta a una fila que no está. Es el mismo criterio que
	 * aplica {@link #gestionarTipoQueNoEncaja} a los {@code @PathVariable} del resto de
	 * controladores: mandar a quien depura al sitio donde está el problema, que aquí es la
	 * base de datos.</p>
	 *
	 * @param excepcion la cuenta que no se ha encontrado
	 * @return 404 diciendo qué identificador no existe
	 */
	@ExceptionHandler(UsuarioService.UsuarioNoEncontradoException.class)
	public ProblemDetail gestionarUsuarioNoEncontrado(
			UsuarioService.UsuarioNoEncontradoException excepcion) {
		log.warn("El panel ha pedido modificar una cuenta que no existe");
		return problema(HttpStatus.NOT_FOUND, excepcion.getMessage());
	}

	/**
	 * El panel de administración ha intentado dejar la aplicación sin administradores.
	 *
	 * <p>Un 409 y no un 400, aunque lo que se ha enviado no sea válido: el identificador de la
	 * ruta sí existe y el cuerpo sí tiene los datos correctos. Lo que hay es un
	 * <em>conflicto con el estado actual</em> de la aplicación —no hay otro ADMIN con el que
	 * seguir—, que es lo que un 409 quiere decir. Además, un 400 dejaría la puerta abierta a
	 * "reinténtalo": reintentar es exactamente lo que no va a funcionar nunca.</p>
	 *
	 * <p>Sin este manejador caería en el genérico de abajo y saldría como un 500, que además de
	 * no ser verdad obligaría a ir a mirar el log para descubrir que no era un fallo del
	 * servidor sino una regla del panel.</p>
	 *
	 * @param excepcion el rechazo del servicio, ya traducido
	 * @return 409 explicando que no se puede quedar sin administradores
	 */
	@ExceptionHandler(UsuarioService.UsuarioInalterableException.class)
	public ProblemDetail gestionarUsuarioInalterable(
			UsuarioService.UsuarioInalterableException excepcion) {
		log.warn("Se ha rechazado un cambio que dejaría la aplicación sin administradores");
		return problema(HttpStatus.CONFLICT, excepcion.getMessage());
	}

	/**
	 * La contraseña de confirmación del borrado no es la de quien está dentro.
	 *
	 * <p>Un 400 y no un 401 por el mismo motivo que en
	 * {@link #gestionarClaveActualIncorrecta}: la sesión es válida —si no, la petición ni
	 * siquiera habría llegado al servicio—, lo que está mal es lo que se ha escrito en el
	 * formulario de confirmación.</p>
	 *
	 * @param excepcion el rechazo del servicio, ya traducido
	 * @return 400 diciendo que la contraseña no es la correcta
	 */
	@ExceptionHandler(UsuarioService.ClaveConfirmacionIncorrectaException.class)
	public ProblemDetail gestionarClaveConfirmacionIncorrecta(
			UsuarioService.ClaveConfirmacionIncorrectaException excepcion) {
		log.warn("Borrado de una cuenta rechazado: la contraseña de confirmación no es correcta");
		return problema(HttpStatus.BAD_REQUEST, excepcion.getMessage());
	}

	/**
	 * Gestiona el error que se produce cuando intentamos guardar un dato que ya existe.
	 *
	 * @param excepcion excepción producida por tener un dato duplicado
	 * @return respuesta 409 indicando que el registro ya existe
	 */
	@ExceptionHandler(DuplicateKeyException.class)
	public ProblemDetail gestionarDatoDuplicado(DuplicateKeyException excepcion) {
		log.error("Se ha intentado guardar un dato que ya existe", excepcion);
		return problema(HttpStatus.CONFLICT, "Ya existe un registro con ese dato");
	}

	/**
	 * Gestiona el error que se produce cuando no se puede borrar un registro porque tiene
	 * otros datos relacionados.
	 *
	 * @param excepcion excepción producida por la relación entre los datos
	 * @return respuesta 409 indicando que no se puede realizar la operación
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail gestionarIntegridadDatos(DataIntegrityViolationException excepcion) {
		log.error("La operación incumple una relación de la base de datos", excepcion);
		return problema(HttpStatus.CONFLICT,
				"No se puede realizar la operación porque hay datos relacionados");
	}

	/**
	 * Gestiona los errores generales al consultar o modificar la base de datos.
	 *
	 * @param excepcion excepción producida al acceder a la base de datos
	 * @return respuesta 500 indicando que existe un error de base de datos
	 */
	@ExceptionHandler(DataAccessException.class)
	public ProblemDetail gestionarBaseDatos(DataAccessException excepcion) {
		log.error("Error al acceder a la base de datos", excepcion);
		return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error al acceder a la base de datos");
	}

	/**
	 * Gestiona los errores que se pueden producir al completar una transacción.
	 *
	 * @param excepcion excepción producida durante la transacción
	 * @return respuesta 500 indicando que no se pudo completar la operación
	 */
	@ExceptionHandler(TransactionException.class)
	public ProblemDetail gestionarTransaccion(TransactionException excepcion) {
		log.error("Error al completar la operación en la base de datos", excepcion);
		return problema(HttpStatus.INTERNAL_SERVER_ERROR, "No se ha podido completar la operación");
	}

	/**
	 * Gestiona las excepciones que ya traen dentro su código de estado, que es como los
	 * servicios de este proyecto cuentan un 404 o un 409 de negocio.
	 *
	 * @param excepcion excepción que contiene el estado y el mensaje del error
	 * @return respuesta con el código y el mensaje de la excepción
	 */
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ProblemDetail> gestionarEstadoHttp(ResponseStatusException excepcion) {
		log.warn("No se ha podido realizar la operación solicitada: {}", excepcion.getReason());

		HttpStatusCode estado = excepcion.getStatusCode();
		String motivo = excepcion.getReason() != null
				? excepcion.getReason()
				: "No se ha podido completar la operación";

		return ResponseEntity.status(estado).body(problema(estado, motivo));
	}

	/**
	 * Gestiona un valor de la URL que no encaja con el tipo que espera el método.
	 *
	 * <p>Aquí está el matiz que costó semanas de despiste y que quedó documentado en
	 * {@code docu/fallos_master.txt}: cuando el que no encaja es un {@link PathVariable}, la
	 * respuesta honesta es un <strong>404</strong> y no un 400. Pedir
	 * {@code /cliente/listar-ultimos} cuando solo existe {@code /cliente/&#123;id&#125;} no es
	 * mandar mal un parámetro: es pedir una ruta que no está. Un 400 manda a quien depura a
	 * revisar el JavaScript, donde no hay nada que arreglar; un 404 le manda al backend, que
	 * es donde está el problema de verdad.</p>
	 *
	 * <p>Si el que falla es un parámetro de consulta ({@code ?limite=abc}), entonces sí es
	 * culpa de lo que se ha enviado, y el 400 es lo correcto.</p>
	 *
	 * @param excepcion el valor que no se pudo convertir
	 * @return 404 si venía en la ruta, 400 si venía en la consulta
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ProblemDetail> gestionarTipoQueNoEncaja(
			MethodArgumentTypeMismatchException excepcion) {

		boolean vieneEnLaRuta = excepcion.getParameter().hasParameterAnnotation(PathVariable.class);

		if (vieneEnLaRuta) {
			log.warn("Ruta inexistente: {} no es un valor válido para {}",
					excepcion.getValue(), excepcion.getName());

			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(problema(HttpStatus.NOT_FOUND, "No existe el recurso solicitado"));
		}

		log.warn("Parámetro {} con un valor no válido: {}", excepcion.getName(), excepcion.getValue());

		// Se dice QUE se esperaba, no solo que lo enviado no vale: "limite no tiene un valor
		// valido" deja igual de perdido que no decir nada. El tipo se saca del propio metodo,
		// asi que no hay que mantener una lista a mano.
		Class<?> esperado = excepcion.getRequiredType();
		String tipo = esperado != null ? esperado.getSimpleName() : "otro tipo";

		return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST,
				"El parámetro " + excepcion.getName() + " esperaba un valor de tipo " + tipo
						+ " y se ha recibido " + excepcion.getValue()));
	}

	/**
	 * La red final: cualquier cosa que no hayan recogido ni Spring ni los manejadores de
	 * arriba acaba aquí.
	 *
	 * <p>Devuelve un mensaje genérico a propósito. Un fallo imprevisto no se le explica a
	 * quien usa la aplicación con el nombre de una clase de Java, y una traza en la respuesta
	 * cuenta de más a quien no debería saberlo. El detalle entero va al log.</p>
	 *
	 * @param excepcion lo que nadie esperaba
	 * @return respuesta 500 con un motivo que se pueda leer
	 */
	@ExceptionHandler(Exception.class)
	public ProblemDetail gestionarImprevisto(Exception excepcion) {
		log.error("Error no contemplado", excepcion);
		return problema(HttpStatus.INTERNAL_SERVER_ERROR,
				"Se ha producido un error inesperado. Vuelve a intentarlo en unos segundos");
	}

	/**
	 * Los errores de validación de un {@code @Valid @RequestBody}.
	 *
	 * <p>Se sobrescribe el de la clase base solo para añadir el mapa de campo a motivo en la
	 * propiedad {@code errores}: con el cuerpo que viene de serie, el frontend sabe QUE algo
	 * está mal pero no CUÁL de los ocho campos, y tendría que adivinarlo.</p>
	 *
	 * @param excepcion los campos que no han pasado la validación
	 * @param cabeceras las cabeceras que propone la clase base
	 * @param estado    el código que propone la clase base
	 * @param peticion  la petición que falló
	 * @return respuesta 400 con el motivo de cada campo
	 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException excepcion, HttpHeaders cabeceras,
			HttpStatusCode estado, WebRequest peticion) {

		// LinkedHashMap y putIfAbsent: se conserva el orden de los campos del formulario y, si
		// un campo incumple dos reglas a la vez, se cuenta la primera y no una al azar.
		Map<String, String> errores = new LinkedHashMap<>();
		for (FieldError error : excepcion.getBindingResult().getFieldErrors()) {
			errores.putIfAbsent(error.getField(), error.getDefaultMessage());
		}

		log.warn("Datos no válidos: {}", errores);

		ProblemDetail cuerpo = problema(HttpStatus.BAD_REQUEST, "Revisa los campos marcados");
		cuerpo.setProperty("errores", errores);

		return ResponseEntity.badRequest().body(cuerpo);
	}

}
