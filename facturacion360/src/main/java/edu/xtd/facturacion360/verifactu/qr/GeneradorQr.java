package edu.xtd.facturacion360.verifactu.qr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import edu.xtd.facturacion360.dto.Factura;

/**
 * Construye la URL de verificación de la AEAT y la codifica como PNG.
 *
 * <p>El código QR de una factura Verifactu <strong>no cifra ni firma nada</strong>:
 * es únicamente una URL pública de la sede electrónica con cuatro parámetros.
 * Quien recibe la factura escanea el código y comprueba en la AEAT que está
 * declarada.</p>
 *
 * <h2>Requisitos técnicos de la Orden HAC/1177/2024</h2>
 * <ul>
 *   <li>Norma: <strong>ISO/IEC 18004:2015</strong>, nivel de corrección
 *       <strong>M</strong>.</li>
 *   <li>Tamaño impreso: entre <strong>30 × 30 mm y 40 × 40 mm</strong>.</li>
 *   <li>Zona de silencio: <strong>≥ 2 mm</strong>, recomendada 6 mm.</li>
 *   <li>Parámetros: {@code nif}, {@code numserie}, {@code fecha}
 *       ({@code dd-MM-yyyy}), {@code importe} (dos decimales,
 *       {@link RoundingMode#HALF_UP}).</li>
 *   <li>Codificación: UTF-8 con URL encoding para los valores.</li>
 * </ul>
 *
 * <h2>Decisión de diseño: tamaño en píxeles</h2>
 * <p>El PNG se genera a <strong>472 × 472 px</strong>, que equivale a 40 mm
 * a 300 dpi —el extremo superior del rango admitido—. El CSS de impresión
 * lo escala a {@code 35mm × 35mm} con unidades de papel, así que el número de
 * píxeles solo determina la resolución interna. El navegador es quien decide
 * el tamaño físico.</p>
 *
 * <h2>Decisión de diseño: {@code MARGIN = 4} módulos</h2>
 * <p>ZXing usa módulos (celdas del QR), no milímetros. Con 4 módulos de margen
 * y un QR de nivel M, la zona de silencio resultante supera los 2 mm exigidos
 * sin llegar a invadir el área de datos.</p>
 */
@Component
public class GeneradorQr {

	/** Formato de fecha que exige la Orden: {@code dd-MM-yyyy}. */
	private static final DateTimeFormatter FECHA_QR = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	/**
	 * Tamaño del lado del PNG en píxeles.
	 *
	 * <p>472 px ≈ 40 mm a 300 dpi. El CSS de impresión lo fija en
	 * {@code 35mm}, dentro del rango de 30–40 mm que exige la norma.</p>
	 */
	private static final int LADO_PX = 472;

	/**
	 * Zona de silencio en módulos QR.
	 *
	 * <p>Con 4 módulos y un símbolo QR de nivel M, el margen blanco supera
	 * los 2 mm obligatorios al imprimir a 300 dpi.</p>
	 */
	private static final int MARGEN_MODULOS = 4;

	/** Opciones de generación: nivel M, UTF-8 y zona de silencio. */
	private static final Map<EncodeHintType, Object> OPCIONES_QR = Map.of(
			EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
			EncodeHintType.CHARACTER_SET,    StandardCharsets.UTF_8.name(),
			EncodeHintType.MARGIN,           MARGEN_MODULOS);

	@Value("${verifactu.qr.base}")
	private String urlBase;

	/**
	 * Construye la URL de validación de la sede electrónica de la AEAT.
	 *
	 * <p>Los cuatro parámetros son obligatorios. {@link UriComponentsBuilder}
	 * los codifica en UTF-8, escapando caracteres como {@code &} y {@code +}
	 * que de otro modo romperían el análisis de la URL en el servidor de la AEAT.
	 * La barra {@code /} dentro del número de serie es legal en una
	 * <em>query string</em> y no necesita escaparse.</p>
	 *
	 * @param factura    la factura emitida cuyos datos formarán la URL
	 * @param nifEmisor  el NIF/CIF del emisor registrado en la AEAT
	 * @return la URL completa lista para codificar en el QR
	 */
	public String construirUrl(Factura factura, String nifEmisor) {
		String importeFormateado = factura.total()
				.setScale(2, RoundingMode.HALF_UP)
				.toPlainString();

		return UriComponentsBuilder.fromUriString(urlBase)
				.queryParam("nif",      nifEmisor)
				.queryParam("numserie", factura.numeroFactura())
				.queryParam("fecha",    factura.fechaEmision().format(FECHA_QR))
				.queryParam("importe",  importeFormateado)
				.build()
				.encode(StandardCharsets.UTF_8)
				.toUriString();
	}

	/**
	 * Genera el PNG con el código QR de la URL proporcionada.
	 *
	 * @param url la URL que codifica el QR; nunca {@code null}
	 * @return los bytes del PNG, listos para servir con
	 *         {@code Content-Type: image/png}
	 * @throws WriterException si ZXing no puede generar la matriz del QR
	 * @throws IOException     si falla la escritura en el buffer de memoria
	 */
	public byte[] generarPng(String url) throws WriterException, IOException {
		QRCodeWriter escritor = new QRCodeWriter();
		BitMatrix matriz = escritor.encode(url, BarcodeFormat.QR_CODE, LADO_PX, LADO_PX, OPCIONES_QR);

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		MatrixToImageWriter.writeToStream(matriz, "PNG", buffer);
		return buffer.toByteArray();
	}
}
