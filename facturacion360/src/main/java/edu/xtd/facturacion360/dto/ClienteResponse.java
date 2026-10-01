package edu.xtd.facturacion360.dto;

import java.time.LocalDate;

/**
 * Datos de cliente que la API devuelve en las respuestas HTTP.
 *
 * @param idCliente identificador único del cliente en base de datos
 * @param nombre nombre comercial o razón social del cliente
 * @param nifCif identificador fiscal del cliente
 * @param direccion dirección postal del cliente
 * @param codigoPostal código postal del cliente
 * @param poblacion municipio o localidad del cliente
 * @param provincia provincia del cliente
 * @param telefono teléfono de contacto del cliente
 * @param email correo electrónico de contacto del cliente
 * @param fechaAlta fecha en la que se registró el cliente
 */
public record ClienteResponse(
		int idCliente, 
		String nombre,
		String nifCif,
		String direccion,
		String codigoPostal,
		String poblacion,
		String provincia,
		String telefono,
		String email,
		LocalDate fechaAlta) {

}
