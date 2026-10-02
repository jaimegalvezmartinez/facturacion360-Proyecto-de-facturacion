package edu.xtd.facturacion360.repository;

import java.util.List;
import java.util.Optional;

import edu.xtd.facturacion360.dto.Rol;
import edu.xtd.facturacion360.dto.Usuario;

/**
 * Acceso a la tabla {@code usuarios}.
 *
 * <p>Hasta que se añadió el panel de administración, lo único que se hacía aquí era buscar por
 * el nombre de usuario, porque es lo único que se teclea al entrar. No había listados ni
 * búsquedas porque nadie desde la aplicación necesitaba ver el censo de cuentas, y una
 * operación que no existe no se puede explotar.</p>
 *
 * <p>Los tres métodos que sí hay ahora ({@link #findTodos()}, {@link #findPorId(int)} y
 * {@link #actualizar(int, String, Rol, boolean)}) existen <strong>para el panel</strong>, y por
 * eso los usa un endpoint cerrado con {@code ROLE_ADMIN}. Volverían a ser un agujero si los
 * llamara cualquier endpoint autenticado, así que quien añada uno nuevo tiene que acordarse de
 * dónde está la regla: en {@code ConfiguracionSeguridad}.</p>
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
	 * Devuelve todas las cuentas, de la más antigua a la más reciente.
	 *
	 * <p>El orden es por {@code idusuario} y no alfabético por el nombre: el alta de una cuenta
	 * es un hecho que pasó en un momento concreto, y una lista de altas en orden inverso es más
	 * útil de leer que una lista alfabética donde la primera cuenta creada se ha ido al final.
	 * De todos modos, el que pinsa lo ordena en el navegador.</p>
	 *
	 * <p>Sin paginación a propósito. El número de cuentas de una aplicación de facturación es
	 * de decenas, no de millones, y una tabla con paginación aquí serían tres endpoints (listado,
	 * total, filtros) para algo que cabe en una pantalla.</p>
	 *
	 * @return todas las cuentas de la tabla
	 */
	List<Usuario> findTodos();

	/**
	 * Busca un usuario por su identificador.
	 *
	 * @param idUsuario el identificador que trae la ruta
	 * @return el usuario, o vacío si ese identificador no está dado de alta
	 */
	Optional<Usuario> findPorId(int idUsuario);

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
	 * Cambia el nombre, el rol y el estado de una cuenta.
	 *
	 * <p>No toca la contraseña ni el nombre de usuario, y no hay forma de que los toque: la
	 * sentencia solo nombra esas tres columnas. El hash se cambia por su propio camino
	 * ({@link #cambiarClave(int, String)}) y el nombre de usuario es la clave con la que esa
	 * persona entra.</p>
	 *
	 * @param idUsuario identificador de la cuenta a modificar
	 * @param nombre    nombre y apellidos
	 * @param rol       perfil de permisos
	 * @param activo    {@code false} para dejar la cuenta sin poder entrar
	 */
	void actualizar(int idUsuario, String nombre, Rol rol, boolean activo);

	/**
	 * Borra una cuenta entera.
	 *
	 * <p>Un DELETE y no un UPDATE que ponga {@code activo = 0}: desactivar deja a la persona
	 * fuera igual que borrarla, pero la fila se queda y el nombre de usuario no vuelve a
	 * quedar libre. Borrar es lo que se hace con una cuenta dada por perdida o con la que no
	 * se quiere que exista. Para "dejar de usar esta cuenta" está
	 * {@link #actualizar(int, String, Rol, boolean)}, que es lo reversible.</p>
	 *
	 * @param idUsuario identificador de la cuenta a borrar
	 */
	void borrar(int idUsuario);

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