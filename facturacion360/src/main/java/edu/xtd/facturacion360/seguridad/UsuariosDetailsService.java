package edu.xtd.facturacion360.seguridad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import edu.xtd.facturacion360.dto.Usuario;
import edu.xtd.facturacion360.repository.UsuarioRepository;

/**
 * Traduce una fila de {@code usuarios} a lo que Spring Security entiende por un usuario.
 *
 * <p>Este es el puente entre las dos capas: la aplicación trabaja con su propio
 * {@link Usuario} (con su {@code Rol} y su fecha de alta) y la seguridad de Spring trabaja con
 * authorities de la forma {@code ROLLO_XXX}. El nombre del usuario se entrega tal cual, sin
 * transformar, porque es la clave con la que luego se vuelve a buscar.</p>
 *
 * <p>Un usuario que no existe y una contraseña equivocada tienen que acabar igual: los dos
 * lanzan aquí la misma excepción, que Spring convierte en el mismo 401. Distinguirlos sería un
 * filtro de usuarios gratis: con el mensaje de la respuesta ya se sabe qué nombres hay dados de
 * alta.</p>
 */
@Service
public class UsuariosDetailsService implements UserDetailsService {

	private static final Logger log = LoggerFactory.getLogger(UsuariosDetailsService.class);

	private final UsuarioRepository usuarioRepository;

	public UsuariosDetailsService(UsuarioRepository usuarioRepository) {
		this.usuarioRepository = usuarioRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String usuario) throws UsernameNotFoundException {

		Usuario encontrado = usuarioRepository.findPorUsuario(usuario).orElse(null);

		if (encontrado == null) {

			log.warn("Intento de acceso con un usuario que no está dado de alta: {}", usuario);

			throw new UsernameNotFoundException("No existe ese usuario");
		}

		log.debug("Usuario cargado: {} ({})", encontrado.usuario(), encontrado.rol());

		// roles() y no authorities() a mano: Spring le pone delante el "ROLE_" que espera
		// hasRole("ADMIN"). Si se escribiera la autoridad a mano y se olvidara el prefijo,
		// el usuario entraría y no podría hacer nada, sin ningún error visible.
		return User
				.withUsername(encontrado.usuario())
				.password(encontrado.claveHash())
				.roles(encontrado.rol().name())
				// disabled() y no un if que lance aquí: Spring ya sabe responder a un usuario
				// desactivado con el mismo 401 que a una contraseña mala, y además lo deja
				// registrado en el log de seguridad.
				.disabled(!encontrado.activo())
				.build();
	}

}