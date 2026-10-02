package edu.xtd.facturacion360.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Lo que envía el <strong>panel de administración</strong> para dar de alta una cuenta.
 *
 * <p>Es el hermano gemelo de {@link RegistroRequest}, y la única diferencia —que lo sea todo— es
 * que <strong>este sí tiene campo de rol</strong>. En el registro público ese campo no existe, y
 * su ausencia es la medida de seguridad: cualquiera que llegue a la pantalla de acceso puede
 * crearse una cuenta, pero nunca con permisos de administrador. Aquí, en cambio, quien llama ya
 * ha pasado la regla {@code /usuarios/** → ROLE_ADMIN} del servidor, así que puede crear
 * cuentas de los dos tipos a propósito.</p>
 *
 * <p>Nadie da de alta una cuenta con este record salvo el panel: {@code POST /auth/registro}
 * sigue leyendo {@link RegistroRequest} y sigue creando USUARIO.</p>
 *
 * <p>Las reglas de formato son las mismas que en {@link RegistroRequest}, y están repetidas aquí
 * a propósito. Un record no hereda de otro, así que o se repiten o se acepta cualquier cosa en
 * uno de los dos caminos; repetir dos líneas es más barato que un hueco por el que se puede
 * colar una cuenta con la contraseña vacía.</p>
 *
 * @param usuario nombre con el que entrará después
 * @param clave   contraseña en claro; se hashea antes de tocar la base de datos
 * @param nombre  nombre y apellidos de la persona
 * @param rol     perfil con el que nace la cuenta
 */
public record AltaAdminRequest(

		@NotBlank(message = "El usuario es obligatorio")
		@Size(min = 3, max = 50, message = "El usuario debe tener entre 3 y 50 caracteres")
		@Pattern(
				regexp = "^[A-Za-z0-9._-]+$",
				message = "El usuario solo puede llevar letras, números, punto, guion y guion bajo"
		)
		String usuario,

		// El mismo mínimo de 8 que el registro público y que el cambio de contraseña propia:
		// es el mínimo, no una receta. Lo que hace fuerte una contraseña es la longitud.
		@NotBlank(message = "La contraseña es obligatoria")
		@Size(min = 8, max = 200, message = "La contraseña debe tener entre 8 y 200 caracteres")
		String clave,

		@NotBlank(message = "El nombre es obligatorio")
		@Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
		String nombre,

		// @NotNull y no un valor por defecto en el record: si el campo no viene, el 400 dice
		// "el rol es obligatorio", que es lo que ha pasado. Poner USUARIO por defecto
		// devolvería un 201 con un rol que nadie pidió, y quien lo ha creado sería un
		// administrador sin querer.
		@NotNull(message = "El rol es obligatorio")
		Rol rol) {

}
