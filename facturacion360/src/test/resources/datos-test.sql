-- Las dos cuentas que usan los tests de seguridad. Las contraseñas son
-- "CambiarYa.2026" en las dos, hasheadas con BCrypt.
--
-- El hash va escrito a mano y es el mismo que trae docu/migracion-usuarios.sql,
-- a propósito: así los tests usan exactamente el mismo valor que se carga en la
-- base de datos de verdad, y si alguien cambia el codificador de contraseñas en
-- application.properties, estos datos dejan de servir y los tests avisan.
--
-- Con H2 el nombre de la base va SIN comillas invertidas porque H2 no acepta
-- "esquema.base" escrito así; los "bd_facturacion." que llevan las sentencias de
-- los repositorios sí, gracias al modo MySQL.

INSERT INTO bd_facturacion.usuarios (usuario, clave_hash, nombre, rol, activo) VALUES
('admin', '$2a$10$XLknJReh.BEubHm2F1uYjeV16cnuqAcvJIkT5ozT..x6bwhSEicdu', 'Administrador', 'ADMIN', 1),
('datos', '$2a$10$XLknJReh.BEubHm2F1uYjeV16cnuqAcvJIkT5ozT..x6bwhSEicdu', 'Usuario de pruebas', 'USUARIO', 1),
('baja', '$2a$10$XLknJReh.BEubHm2F1uYjeV16cnuqAcvJIkT5ozT..x6bwhSEicdu', 'Cuenta desactivada', 'USUARIO', 0);

-- Un cliente de sobra para probar el borrado sin que salte la clave ajena.
INSERT INTO clientes (nombre, nif_cif, direccion, poblacion, provincia, fecha_alta) VALUES
('Cliente de prueba', '12345678Z', 'Calle Mayor 1', 'Valencia', 'Valencia', CURRENT_DATE);