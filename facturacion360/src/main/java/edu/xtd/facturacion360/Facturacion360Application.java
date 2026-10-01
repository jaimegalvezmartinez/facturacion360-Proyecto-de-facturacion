package edu.xtd.facturacion360;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase Main que Spring usa para levantar la aplicación, el servidor
 * y poner en marcha la conexión con la base de datos
 */
@SpringBootApplication
public class Facturacion360Application {

	public static void main(String[] args) {
		SpringApplication.run(Facturacion360Application.class, args);
	}

}
