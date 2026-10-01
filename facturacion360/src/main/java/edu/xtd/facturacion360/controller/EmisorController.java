package edu.xtd.facturacion360.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import edu.xtd.facturacion360.dto.Emisor;
import edu.xtd.facturacion360.service.EmisorService;

/**
 * He tenido que hacer cambios sutiles en el controller, porque lo que hizo val esta bien, pero con los cambios en el JS el EMISOR no lo leia de la base de datos, pero con esta version de controller funciona todo (por lo menos en local)
 */

@RestController
@RequestMapping("/emisor")
public class EmisorController {

    private final EmisorService emisorService;

    public EmisorController(EmisorService emisorService) {
        this.emisorService = emisorService;
    }

    /**
     * Obtiene los datos actuales del emisor.
     */
    @GetMapping
    public ResponseEntity<Emisor> find() {

        Emisor emisor = emisorService.find();

        if (emisor == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Todavia no se han guardado los datos del emisor"
            );
        }

        return ResponseEntity.ok(emisor);
    }

    /**
     * Obtiene el logo del emisor.
     */
    @GetMapping("/logo")
    public ResponseEntity<byte[]> findLogo() {

        byte[] logo = emisorService.findLogo();

        return ResponseEntity
                .ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(logo);
    }

    /**
     * Crea o actualiza el emisor utilizando JSON.
     */
    @PutMapping
    public ResponseEntity<Emisor> save(@RequestBody Emisor emisor) {

        if (emisor == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se han recibido los datos del emisor"
            );
        }

        Emisor emisorGuardado = emisorService.save(emisor);

        if (emisorGuardado == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se han podido guardar los datos del emisor"
            );
        }

        return ResponseEntity.ok(emisorGuardado);
    }

    /**
     * Crea o actualiza el emisor utilizando multipart/form-data.
     *
     * Los datos del emisor llegan como campos del formulario
     * y la imagen llega como MultipartFile.
     *
     * El campo "foto" es opcional para permitir actualizar
     * únicamente los datos sin sustituir el logo existente.
     */
    @PutMapping(
            path = "/con-imagen",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Emisor> save(
            @ModelAttribute Emisor emisor,
            @RequestPart(value = "foto", required = false) MultipartFile foto)
            throws IOException {

        if (emisor == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se han recibido los datos del emisor"
            );
        }

        Emisor emisorCompleto = emisor;

        /*
         * Si el usuario ha seleccionado una nueva imagen,
         * la incorporamos al objeto Emisor.
         *
         * Si no se ha seleccionado ninguna imagen, mantenemos
         * emisorCompleto tal como llega.
         *
         * El Repository se encarga de conservar el logo existente
         * cuando emisor.logo() es null.
         */
        if (foto != null && !foto.isEmpty()) {

            emisorCompleto = new Emisor(
                    emisor.nombre(),
                    emisor.cif(),
                    emisor.direccion(),
                    emisor.email(),
                    emisor.telefono(),
                    foto.getBytes()
            );
        }

        Emisor emisorGuardado = emisorService.save(emisorCompleto);

        if (emisorGuardado == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se han podido guardar los datos del emisor"
            );
        }

        return ResponseEntity.ok(emisorGuardado);
    }
}