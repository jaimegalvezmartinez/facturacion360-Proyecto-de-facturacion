package edu.xtd.facturacion360.repository;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import edu.xtd.facturacion360.dto.Cliente;

/**
 * Esta clase convierte un registro de la base de datos en un objeto Cliente.
 *
 * Con @Component, Spring crea automáticamente una instancia de esta clase
 * para poder utilizarla desde el repositorio.
 */
@Component
public class ClienteRowMapper implements RowMapper<Cliente> {

	/**
	 * Convierte la fila ACTUAL del {@link ResultSet} en un objeto {@link Cliente}.
	 * Spring lo llama automáticamente una vez por cada fila del resultado.
	 *
	 * @param rs     el ResultSet posicionado en la fila a convertir (de él leemos las columnas)
	 * @param rowNum el número de fila (0, 1, 2…); aquí no lo usamos
	 * @return el {@link Cliente} construido con los datos de esa fila
	 * @throws SQLException si falla el acceso a alguna columna del ResultSet
	 */
	@Override
	public Cliente mapRow(ResultSet rs, int rowNum) throws SQLException {
		// Spring llama a este método UNA VEZ POR CADA FILA del ResultSet. Leemos cada
		// columna por su nombre y construimos el objeto Cliente correspondiente.
		// (No ponemos log aquí a propósito: al ejecutarse por cada fila, inundaría la
		//  consola. Los logs van en el repositorio / servicio / controller.)

		// fecha_alta llega como java.sql.Date y el record usa LocalDate: convertimos
		// controlando el caso null (por si la columna viniera vacía).
		java.sql.Date fechaAlta = rs.getDate("fecha_alta");

		// Cada columna de la tabla 'clientes' -> su campo del record Cliente.
		Cliente cliente = new Cliente(
				rs.getInt("idcliente"),          // idcliente     -> idCliente
				rs.getString("nombre"),          // nombre        -> nombre
				rs.getString("nif_cif"),         // nif_cif       -> nifCif
				rs.getString("direccion"),       // direccion     -> direccion
				rs.getString("codigopostal"),    // codigopostal  -> codigoPostal
				rs.getString("poblacion"),       // poblacion     -> poblacion
				rs.getString("provincia"),       // provincia     -> provincia
				rs.getString("telefono"),        // telefono      -> telefono
				rs.getString("email"),           // email         -> email
				fechaAlta != null ? fechaAlta.toLocalDate() : null); // fecha_alta -> fechaAlta

		return cliente;
	}

}