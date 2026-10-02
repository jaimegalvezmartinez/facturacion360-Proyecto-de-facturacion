package edu.xtd.facturacion360.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import edu.xtd.facturacion360.dto.Rol;
import edu.xtd.facturacion360.dto.Usuario;

/**
 * Implementación del acceso a {@code usuarios} con {@link JdbcTemplate}.
 *
 * <p>Las consultas llevan el nombre de la base delante, como las del resto del proyecto
 * ({@code `bd_facturacion`.`usuarios`}). Es una manía del proyecto y no debería hacer falta
 * —la conexión ya va a esa base—, pero se respeta aquí para que estas sentencias se parezcan
 * a las de los demás repositorios y no parezcan venidas de otro sitio.</p>
 */
@Repository
public class UsuarioRepositoryJdbcImpl implements UsuarioRepository {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * Las columnas que lee el {@link RowMapper}, tal cual van en el SELECT.
	 *
	 * <p>Está en una constante y no repetida en cada consulta porque el mapper las nombra una
	 * por una: si el SELECT y el mapper dejaran de coincidir, el síntoma sería un
	 * {@code SQLException} diciendo que no existe tal columna, en la consulta equivocada, y no
	 * un aviso de que la lista de campos se quedó corta.</p>
	 */
	private static final String COLUMNAS = """
				`idusuario`,
				`usuario`,
				`clave_hash`,
				`nombre`,
				`rol`,
				`activo`,
				`fecha_alta`,
				`ultimo_acceso`
			""";

	/**
	 * Lee una fila de {@code usuarios}.
	 *
	 * <p>Se declara una vez y se reutiliza en las tres consultas, porque las tres leen las mismas
	 * ocho columnas y el mapa es idéntico. Duplicarlo tres veces era una forma de que un día
	 * añadiera una columna a un SELECT y no a los otros dos.</p>
	 *
	 * <p>Los dos {@code getTimestamp} se llaman dos veces cada uno porque el driver devuelve un
	 * {@code java.sql.Timestamp} y un {@code Optional} no es un {@code LocalDateTime} vacío: hay
	 * que comprobar el nulo a mano. Es la parte menos elegante de JDBC y no tiene arreglo.</p>
	 */
	private static final RowMapper<Usuario> MAPEADOR = (rs, fila) -> new Usuario(
			rs.getInt("idusuario"),
			rs.getString("usuario"),
			rs.getString("clave_hash"),
			rs.getString("nombre"),
			Rol.de(rs.getString("rol")),
			rs.getBoolean("activo"),
			rs.getTimestamp("fecha_alta") == null
					? null
					: rs.getTimestamp("fecha_alta").toLocalDateTime(),
			rs.getTimestamp("ultimo_acceso") == null
					? null
					: rs.getTimestamp("ultimo_acceso").toLocalDateTime());

	@Override
	public Optional<Usuario> findPorUsuario(String usuario) {

		String sql = """
				SELECT %s
				FROM `bd_facturacion`.`usuarios`
				WHERE `usuario` = ?
				""".formatted(COLUMNAS);

		return jdbcTemplate
				.query(sql, MAPEADOR, usuario)
				.stream()
				.findFirst();
	}

	@Override
	public List<Usuario> findTodos() {

		// El ORDER BY va por idusuario y no por nombre: la lista se enseña en orden de alta, que
		// es el orden en que las cuentas han ido apareciendo, y el que quiera otra cosa lo
		// ordena en el navegador sin volver a preguntárselo al servidor.
		String sql = """
				SELECT %s
				FROM `bd_facturacion`.`usuarios`
				ORDER BY `idusuario` ASC
				""".formatted(COLUMNAS);

		return jdbcTemplate.query(sql, MAPEADOR);
	}

	@Override
	public Optional<Usuario> findPorId(int idUsuario) {

		String sql = """
				SELECT %s
				FROM `bd_facturacion`.`usuarios`
				WHERE `idusuario` = ?
				""".formatted(COLUMNAS);

		return jdbcTemplate
				.query(sql, MAPEADOR, idUsuario)
				.stream()
				.findFirst();
	}

	@Override
	public Usuario insert(Usuario usuario) {

		String sql = """
				INSERT INTO `bd_facturacion`.`usuarios`
				(
					`usuario`,
					`clave_hash`,
					`nombre`,
					`rol`,
					`activo`,
					`fecha_alta`
				)
				VALUES (?, ?, ?, ?, ?, NOW())
				""";

		// El KeyHolder es lo único que devuelve el identificador que ha asignado MySQL. Sin
		// él habría que hacer un SELECT por el nombre justo después, y entre medias otro
		// INSERT con el mismo nombre se colaría.
		//
		KeyHolder clave = new GeneratedKeyHolder();

		try {

			jdbcTemplate.update(conexion -> {

				PreparedStatement sentencia = conexion.prepareStatement(
						sql, Statement.RETURN_GENERATED_KEYS);

				sentencia.setString(1, usuario.usuario());
				sentencia.setString(2, usuario.claveHash());
				sentencia.setString(3, usuario.nombre());
				sentencia.setString(4, usuario.rol().name());
				sentencia.setBoolean(5, usuario.activo());

				return sentencia;

			}, clave);

		} catch (DuplicateKeyException duplicado) {

			throw new UsuarioDuplicadoException(usuario.usuario(), duplicado);
		}

		// El identificador se saca del mapa de claves y no con clave.getKey(). La diferencia
		// no se ve hasta que se cambia de motor: MySQL devuelve una sola clave y getKey()
		// funciona, pero H2 devuelve como claves TODAS las columnas con valor por defecto
		// (aquí, idusuario y fecha_alta), y getKey() revienta diciendo que esperaba una y
		// hay varias. El mapa funciona en los dos, y solo hay que tener en cuenta que MySQL
		// llama a su clave GENERATED_KEY en lugar de poner el nombre de la columna.
		Map<String, Object> claves = clave.getKeys();

		Object valor = claves != null ? claves.get("idusuario") : null;

		if (valor == null) {
			valor = clave.getKey();
		}

		Number id = valor instanceof Number numero ? numero : null;

		return new Usuario(
				id != null ? id.intValue() : 0,
				usuario.usuario(),
				usuario.claveHash(),
				usuario.nombre(),
				usuario.rol(),
				usuario.activo(),
				null,
				null
		);
	}

	@Override
	public void registrarAcceso(int idUsuario) {

		String sql = """
				UPDATE `bd_facturacion`.`usuarios`
				SET `ultimo_acceso` = NOW()
				WHERE `idusuario` = ?
				""";

		jdbcTemplate.update(sql, idUsuario);
	}

	@Override
	public void cambiarClave(int idUsuario, String claveHash) {

		// El hash va ya calculado por el servicio. Lo que llega a esta sentencia es
		// $2a$10$... y no la contraseña: ni en un log ni en una traza de error de MySQL
		// puede aparecer la contraseña en claro, y eso depende de que nadie la pase por
		// aquí.
		String sql = """
				UPDATE `bd_facturacion`.`usuarios`
				SET `clave_hash` = ?
				WHERE `idusuario` = ?
				""";

		jdbcTemplate.update(sql, claveHash, idUsuario);
	}

	@Override
	public void actualizar(int idUsuario, String nombre, Rol rol, boolean activo) {

		// Solo tres columnas. Ni clave_hash ni usuario aparecen en el SET, y no por descuido:
		// son las dos cosas que se cambian por su propio camino, una porque necesita hashearse y
		// la otra porque es la clave con la que esa persona entra. Meterlas aquí sería una forma
		// de que una edición de nombre acabara con la contraseña de alguien en blanco.
		String sql = """
				UPDATE `bd_facturacion`.`usuarios`
				SET `nombre` = ?, `rol` = ?, `activo` = ?
				WHERE `idusuario` = ?
				""";

		jdbcTemplate.update(sql, nombre, rol.name(), activo, idUsuario);
	}

	@Override
	public void borrar(int idUsuario) {

		// Un DELETE de una sola fila por identificador, y no por nombre de usuario: el
		// identificador es lo que trae la ruta y es lo que no se puede cambiar desde la
		// aplicación, así que la fila que se borra es exactamente la que se ha pedido.
		String sql = """
				DELETE FROM `bd_facturacion`.`usuarios`
				WHERE `idusuario` = ?
				""";

		jdbcTemplate.update(sql, idUsuario);
	}

}