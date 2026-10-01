package edu.xtd.facturacion360.controller;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.google.zxing.WriterException;

import edu.xtd.facturacion360.dto.Emisor;
import edu.xtd.facturacion360.dto.Factura;
import edu.xtd.facturacion360.repository.FacturaRepository;
import edu.xtd.facturacion360.service.EmisorService;
import edu.xtd.facturacion360.verifactu.qr.GeneradorQr;

/**
 * Sirve el código QR de verificación fiscal exigido por la Orden HAC/1177/2024.
 *
 * <p>El endpoint produce un PNG listo para incrustar en la vista de impresión.
 * Solo se genera para facturas <strong>emitidas</strong>: un borrador no tiene
 * registro de facturación y por tanto no tiene URL en la sede de la AEAT.</p>
 *
 * <p>Los errores de generación (ZXing o IO) los captura
 * {@link ManejadorExcepciones#gestionarImprevisto(Exception)} y devuelve 500.
 * No hace falta añadir nada al manejador.</p>
 */
@RestController
@RequestMapping("/verifactu/qr")
public class VerifactuQrController {

	private static final Logger log = LoggerFactory.getLogger(VerifactuQrController.class);

	private final FacturaRepository facturaRepository;
	private final EmisorService     emisorService;
	private final GeneradorQr       generadorQr;

	public VerifactuQrController(FacturaRepository facturaRepository,
	                              EmisorService emisorService,
	                              GeneradorQr generadorQr) {
		this.facturaRepository = facturaRepository;
		this.emisorService     = emisorService;
		this.generadorQr       = generadorQr;
	}

	/**
	 * Genera y devuelve el PNG del código QR para una factura emitida.
	 *
	 * <p>Secuencia de guardianes, en orden:</p>
	 * <ol>
	 *   <li>{@code idFactura > 0} — valor de URL válido.</li>
	 *   <li>La factura existe en la base de datos.</li>
	 *   <li>La factura no es un borrador — los borradores no tienen registro
	 *       de facturación ni URL de la AEAT.</li>
	 *   <li>El emisor está configurado — su NIF forma parte de la URL del QR.</li>
	 * </ol>
	 *
	 * <p>El PNG se envía con {@code Cache-Control: max-age=3600, immutable}
	 * porque el QR de una factura emitida nunca cambia: los cuatro parámetros
	 * —NIF, número, fecha, importe— son inmutables una vez emitida.</p>
	 *
	 * @param idFactura identificador de la factura; debe ser mayor que cero
	 * @return 200 con el PNG, o 400/404 con un {@code ProblemDetail}
	 * @throws WriterException si ZXing no puede generar la matriz del QR
	 * @throws IOException     si falla la escritura del PNG en memoria
	 */
	@GetMapping(value = "/{idFactura}", produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> obtenerQr(@PathVariable int idFactura)
			throws WriterException, IOException {

		if (idFactura <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"El identificador de factura debe ser mayor que cero");
		}

		Factura factura = facturaRepository.buscarPorId(idFactura);
		if (factura == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					"La factura " + idFactura + " no existe");
		}

		if ("BORRADOR".equals(factura.estado())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					"Los borradores no tienen QR de verificación: "
					+ "el QR solo se genera cuando la factura es emitida");
		}

		Emisor emisor = emisorService.find();
		if (emisor == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					"Todavia no se han guardado los datos del emisor");
		}

		String url   = generadorQr.construirUrl(factura, emisor.cif());
		byte[] png   = generadorQr.generarPng(url);

		log.debug("QR generado para factura {} ({})", factura.numeroFactura(), url);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.IMAGE_PNG);
		headers.setCacheControl("max-age=3600, immutable");

		return new ResponseEntity<>(png, headers, HttpStatus.OK);
	}
}
