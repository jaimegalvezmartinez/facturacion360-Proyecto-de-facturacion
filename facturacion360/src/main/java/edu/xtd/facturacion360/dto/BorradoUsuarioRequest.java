package edu.xtd.facturacion360.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que envía el panel de administración para <strong>borrar una cuenta</strong>.
 *
 * <p>El cuerpo es solo la contraseña del propio administrador, y eso es lo que hace que el
 * borrado sea una operación más cuidadosa que las demás del panel:</p>
 *
 * <ul>
 *   <li><strong>No hay ningún campo de usuario.</strong> La cuenta que se borra va en la ruta
 *       ({@code DELETE /usuarios/&#123;id&#125;}), así que el cuerpo no puede pedir que se borre
 *       otra. Si el cuerpo dijera a quién, habría dos verdades y podrían discrepar.</li>
 *   <li><strong>La contraseña es la del que está haciendo el borrado</strong>, no la de la cuenta
 *       que se va a borrar. Nadie tiene por qué saber la de otra persona, y lo que se comprueba
 *       aquí es "eres tú de verdad y no alguien que se ha sentado delante de un navegador que
 *       estaba abierto": tener una sesión abierta no es lo mismo que estar delante, y con esto
 *       un administrador que se ausenta del puesto deja de poder hacer daño desde su propio
 *       equipo.</li>
 * </ul>
 *
 * <p>Sin ese segundo campo, borrar sería la operación más difícil de deshacer del panel y la
 * más fácil de hacer por accidente: un clic y fuera.</p>
 *
 * @param clave la contraseña del administrador que está dentro, en claro
 */
public record BorradoUsuarioRequest(

		@NotBlank(message = "Escribe tu contraseña para confirmar el borrado")
		@Size(max = 200, message = "La contraseña no puede superar 200 caracteres")
		String clave) {

}
