package edu.xtd.facturacion360.repository;

import java.util.Optional;

import edu.xtd.facturacion360.dto.Usuario;

/**
 * Acceso a la tabla {@code usuarios}.
 *
 * <p>Solo se consulta por el nombre de usuario, que es lo único que se teclea al entrar. No
 * hay listados ni búsquedas: nadie desde la aplicación necesita ver el censo de cuentas, y
 * una operación que no existe no se puede explotar.</p>
 */
public interface UsuarioRepository {

	/**
	 * Busca un usuario por su nombre de acceso.
	 *
	 * @param usuario el nombre con el que se inicia sesión
	 * @return el usuario, o vacío si ese nombre no está dado de alta
	 */
	Optional<Usuario> findPorUsuario(String usuario);

	/**
	 * Inserta un usuario nuevo con la contraseña ya hasheada.
	 *
	 * @param usuario lo que hay que guardar
	 * @return el usuario guardado, ya con el identificador que le ha dado MySQL
	 * @throws UsuarioDuplicadoException si ese nombre de usuario ya estaba dado de alta
	 */
	Usuario insert(Usuario usuario);

	/**
	 * Anota el momento del último inicio de sesión correcto.
	 *
	 * <p>Se llama solo después de que la contraseña haya cuadrado; si no, el dato sería el
	 * último intento, no el último acceso.</p>
	 *
	 * @param idUsuario identificador del usuario que acaba de entrar
	 */
	void registrarAcceso(int idUsuario);

	/**
	 * Sustituye la contraseña de un usuario por su hash nuevo.
	 *
	 * @param idUsuario identificador de quien cambia la contraseña
	 * @param claveHash el hash BCrypt de la contraseña nueva, ya calculada
	 */
	void cambiarClave(int idUsuario, String claveHash);

	/**
	 * Solo representa la colisión del índice único de {@code usuarios.usuario}.
	 *
	 * <p>Existe para que el manejador global pueda devolver un 409 diciendo «ese usuario ya
	 * existe» en vez del 409 genérico de duplicados, que no nombra el campo.</p>
	 */
	class UsuarioDuplicadoException extends RuntimeException {

		public UsuarioDuplicadoException(String usuario, Throwable causa) {
			super("El usuario '" + usuario + "' ya está dado de alta", causa);
		}
	}

}