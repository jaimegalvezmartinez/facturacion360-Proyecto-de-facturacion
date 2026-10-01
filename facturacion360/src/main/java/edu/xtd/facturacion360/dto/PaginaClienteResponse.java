package edu.xtd.facturacion360.dto;

import java.util.List;

/**
 * Lo que devuelve el endpoint de paginación ({@code GET /cliente/listar-pagina}) como JSON.
 * Además de los clientes de ESTA página, incluye metadatos para que el frontend sepa dónde
 * está y pueda activar o desactivar los botones sin recalcular nada.
 *
 * <p>Es un {@code record}: inmutable, y Java le genera el constructor, los accesores y
 * {@code equals}/{@code hashCode}/{@code toString}.</p>
 *
 * @param contenido      los clientes de esta página
 * @param paginaActual   índice de la página, empezando en 0
 * @param totalPaginas   número de páginas; techo de {@code totalElementos / tamano}
 * @param totalElementos cuántos clientes cumplen los criterios de búsqueda y filtrado.
 *                       <strong>No</strong> es el total de la tabla, salvo que no se haya
 *                       aplicado ningún filtro
 * @param hayAnterior    {@code true} si existe una página anterior
 * @param haySiguiente   {@code true} si existe una página siguiente
 *
 * @author AngelDanielC0des
 * @see CriteriosCliente
 * @see ClienteResponse
 */
public record PaginaClienteResponse(
		List<ClienteResponse> contenido,
		int paginaActual,
		int totalPaginas,
		long totalElementos,
		boolean hayAnterior,
		boolean haySiguiente) {
}
