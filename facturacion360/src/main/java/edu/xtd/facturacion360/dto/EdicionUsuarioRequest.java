package edu.xtd.facturacion360.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lo que envía el panel de administración para cambiar el nombre, el rol o el estado de una
 * cuenta.
 *
 * <p><strong>El nombre de usuario no se puede cambiar desde aquí</strong>, y no por forgot: es la
 * clave con la que esa persona entra y con la que {@code UsuariosDetailsService} la busca, y
 * cambiarla por detrás dejaría las sesiones ya abiertas con un nombre que ya no corresponde a
 * ninguna fila. Si hay que renombrar a alguien, se le da un nombre nuevo en el alta y se
 * desactiva la cuenta vieja.</p>
 *
 * <p>Los tres campos son obligatorios, también {@code activo}. Un {@code PUT} que solo trae el
 * rol y se come el nombre y el estado pondría a la cuenta sin nombre y desactivada de paso,
 * que es una forma muy fácil de romper algo sin querer.</p>
 *
 * @param nombre nombre y apellidos de la persona
 * @param rol    perfil de permisos con el que se queda la cuenta
 * @param activo {@code false} deja al usuario existir pero sin poder entrar
 */
public record EdicionUsuarioRequest(

		@NotBlank(message = "El nombre es obligatorio")
		@Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
		String nombre,

		@NotNull(message = "El rol es obligatorio")
		Rol rol,

		@NotNull(message = "Hay que decir si la cuenta queda activa")
		Boolean activo) {

}
