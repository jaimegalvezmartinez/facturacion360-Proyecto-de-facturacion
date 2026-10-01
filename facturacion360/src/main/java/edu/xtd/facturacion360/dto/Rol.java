package edu.xtd.facturacion360.dto;

import java.util.Arrays;

/**
 * Los dos perfiles de acceso que conoce la aplicación.
 *
 * <p>El nombre va en mayúsculas y sin acentos porque es lo que se guarda en la columna
 * {@code usuarios.rol} y lo que Spring Security convierte en autoridad {@code ROLE_ADMIN} y
 * {@code ROLE_USUARIO}. Los dos son <em>exactamente</em> los mismos: la columna es
 * {@code varchar(20)} y el <code>enum</code> es cerrado, así que los dos sitios tienen que
 * decir lo mismo y no hay forma de que uno acepte un valor que el otro no.</p>
 *
 * <p>Solo hay dos a propósito. Un sistema de permisos finos se crece cuando aparece la
 * necesidad, no antes: con veinte roles medio usados nadie sabe ya quién puede qué, y el
 * único que responde es el fichero de configuración de la seguridad, que es el más caro de
 * tocar.</p>
 */
public enum Rol {

	/**
	 * Puede hacer todo lo que hace un USUARIO y además lo que es destructivo o fiscal.
	 *
	 * <p>Concretamente, lo que hoy exige ADMIN:</p>
	 * <ul>
	 *   <li>Borrar clientes ({@code DELETE /cliente/{id}}).</li>
	 *   <li>Modificar los datos del emisor ({@code PUT /emisor}), que son los que salen
	 *       impresos en la factura y en el QR de la AEAT.</li>
	 * </ul>
	 *
	 * <p>Los usuarios de alta por el registro público (<code>POST /auth/registro</code>)
	 * SIEMPRE se crean como USUARIO. El endpoint ni siquiera lee un campo de rol: así no
	 * existe forma de pedir un ADMIN por la API, ni por error ni,a propósito.</p>
	 */
	ADMIN,

	/**
	 * Puede consultar y mantener clientes y facturas, pero no borrar clientes ni tocar los
	 * datos fiscales del emisor.
	 *
	 * <p>Es el perfil por defecto de todo el que se da de alta desde la pantalla de registro.</p>
	 */
	USUARIO;

	/**
	 * Convierte el texto de la base de datos en el rol correspondiente.
	 *
	 * <p>Lanza {@link IllegalArgumentException} si el texto no es ninguno de los dos. Un rol
	 * desconocido NO se degrada a USUARIO: se rompe a propósito, porque el primer síntoma de
	 * una tabla manipulada a mano sería un usuario con menos permisos de los que
	 * deberia tener, y eso no se ve hasta que pasa algo gordo.</p>
	 *
	 * @param texto el valor de la columna {@code usuarios.rol}
	 * @return el rol que le corresponde
	 * @throws IllegalArgumentException si el texto no es un rol de esta enumeración
	 */
	public static Rol de(String texto) {

		return Arrays.stream(values())
				.filter(rol -> rol.name().equalsIgnoreCase(texto))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"El rol '" + texto + "' no existe. Los validos son ADMIN y USUARIO"));
	}

}