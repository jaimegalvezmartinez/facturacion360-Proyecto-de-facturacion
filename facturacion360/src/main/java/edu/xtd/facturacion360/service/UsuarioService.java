package edu.xtd.facturacion360.service;

import java.util.Optional;

import edu.xtd.facturacion360.dto.RegistroRequest;
import edu.xtd.facturacion360.dto.Usuario;

/**
 * Alta y seguimiento de las cuentas del portal.
 *
 * <p>Del login en sí no se ocupa esta interfaz, y es una decisión: comprobar la contraseña es
 * trabajo de Spring Security, que ya trae lo suyo (comparación a tiempo constante, usuario
 * inexistente y contraseña incorrecta dan el mismo error, bloqueo de cuentas desactivadas...).
 * Que el servicio lo repita sería tener dos verdades.</p>
 */
public interface UsuarioService {

	/**
	 * Busca un usuario por su nombre de acceso.
	 *
	 * @param usuario el nombre con el que se inicia sesión
	 * @return el usuario, o vacío si no existe
	 */
	Optional<Usuario> buscar(String usuario);

	/**
	 * Da de alta una cuenta nueva con perfil USUARIO.
	 *
	 * @param registro lo que ha enviado la pantalla de registro
	 * @return el usuario creado, ya con su identificador
	 * @throws edu.xtd.facturacion360.repository.UsuarioRepository.UsuarioDuplicadoException
	 *         si ese nombre de usuario ya estaba dado de alta
	 */
	Usuario registrar(RegistroRequest registro);

	/**
	 * Anota el último acceso de un usuario.
	 *
	 * @param idUsuario identificador del usuario que acaba de entrar
	 */
	void registrarAcceso(int idUsuario);

	/**
	 * Cambia la contraseña de un usuario, pero solo si la que dice tener ahora es la buena.
	 *
	 * @param usuario     el usuario que cambia su contraseña (el de la sesión, nunca uno que
	 *                    venga en el cuerpo de la petición)
	 * @param claveActual la contraseña con la que se supone que está dentro
	 * @param nueva       la contraseña que quiere poner
	 * @return el hash de la contraseña nueva, que quien la cambia necesita para dejar la
	 *         sesión con las credenciales al día
	 * @throws ClaveActualIncorrectaException si la claveActual no es la que tiene guardada
	 */
	String cambiarClave(Usuario usuario, String claveActual, String nueva);

	/**
	 * La contraseña que se ha puesto no es la que tiene el usuario.
	 *
	 * <p>Es un 400 y no un 401: la sesión existe y es válida (por eso ha llegado hasta aquí),
	 * lo que está mal es lo que se ha enviado. Un 401 mandaría al login, que es
	 * desconcertante, porque en el login la contraseña es la correcta.</p>
	 */
	class ClaveActualIncorrectaException extends RuntimeException {

		public ClaveActualIncorrectaException() {
			super("La contraseña actual no es correcta");
		}
	}

}