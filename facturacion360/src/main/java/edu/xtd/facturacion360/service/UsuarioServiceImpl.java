package edu.xtd.facturacion360.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import edu.xtd.facturacion360.dto.RegistroRequest;
import edu.xtd.facturacion360.dto.Rol;
import edu.xtd.facturacion360.dto.Usuario;
import edu.xtd.facturacion360.repository.UsuarioRepository;

/**
 * Alta de cuentas contra la tabla {@code usuarios}.
 *
 * <p>El único trabajo de verdad de esta clase es una cosa que no se puede dejar en el
 * controlador: <strong>hashear la contraseña antes de que llegue a la base de datos</strong>.
 * Todo lo que sale de aquí hacia abajo lleva ya un hash BCrypt, y la contraseña en claro no
 * vuelve a existir en la memoria del proceso más que en la petición entrante.</p>
 */
@Service
public class UsuarioServiceImpl implements UsuarioService {

	private static final Logger log = LoggerFactory.getLogger(UsuarioServiceImpl.class);

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder    passwordEncoder;

	public UsuarioServiceImpl(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder    = passwordEncoder;
	}

	@Override
	public Optional<Usuario> buscar(String usuario) {
		return usuarioRepository.findPorUsuario(usuario);
	}

	@Override
	public Usuario registrar(RegistroRequest registro) {

		// El rol se escribe aquí, no se lee de la petición: Rol.USUARIO, sin más. Es la
		// segunda vez que se dice (la primera, en el DTO) y se vuelve a decir porque es el
		// tipo de línea que un buen día alguien "flexibiliza" con un campo opcional.
		String claveHash = passwordEncoder.encode(registro.clave());

		log.info("Alta de usuario: {}", registro.usuario());

		return usuarioRepository.insert(new Usuario(
				0,
				registro.usuario(),
				claveHash,
				registro.nombre(),
				Rol.USUARIO,
				true,
				null,
				null
		));
	}

	@Override
	public void registrarAcceso(int idUsuario) {
		usuarioRepository.registrarAcceso(idUsuario);
	}

	@Override
	public String cambiarClave(Usuario usuario, String claveActual, String nueva) {

		// Se comprueba la actual contra el hash guardado ANTES de tocar nada. Sin esta
		// comprobación, tener una sesión abierta bastaría para quedarse con la cuenta.
		if (!passwordEncoder.matches(claveActual, usuario.claveHash())) {

			log.warn("Intento de cambio de contraseña con la clave actual equivocada: {}", usuario.usuario());

			throw new ClaveActualIncorrectaException();
		}

		// La contraseña en claro se hashea aquí y no baja de aquí. Al repositorio solo llega
		// un $2a$10$..., igual que en el alta.
		String claveHash = passwordEncoder.encode(nueva);

		usuarioRepository.cambiarClave(usuario.idUsuario(), claveHash);

		log.info("Contraseña cambiada: {}", usuario.usuario());

		// Se devuelve el hash para que quien llama lo meta en la sesión. Volver a leerlo de
		// la base de datos con un SELECT sería tirar una consulta por el mismo dato que
		// aquí ya está.
		return claveHash;
	}

}