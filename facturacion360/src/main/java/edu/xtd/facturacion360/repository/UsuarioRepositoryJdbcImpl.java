package edu.xtd.facturacion360.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
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

	@Override
	public Optional<Usuario> findPorUsuario(String usuario) {

		String sql = """
				SELECT
					`idusuario`,
					`usuario`,
					`clave_hash`,
					`nombre`,
					`rol`,
					`activo`,
					`fecha_alta`,
					`ultimo_acceso`
				FROM `bd_facturacion`.`usuarios`
				WHERE `usuario` = ?
				""";

		return jdbcTemplate
				.query(sql, (rs, rowNum) -> new Usuario(
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
								: rs.getTimestamp("ultimo_acceso").toLocalDateTime()
				), usuario)
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

}