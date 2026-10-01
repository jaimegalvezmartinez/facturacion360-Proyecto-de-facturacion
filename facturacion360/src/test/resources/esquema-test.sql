-- Esquema mínimo para los tests: solo la tabla usuarios, que es la que usa la
-- capa de seguridad. Las demás (clientes, facturas, emisor...) no se crean aquí a
-- propósito: estos tests no las tocan, y mantener una copia de un esquema que
-- cambia en cada versión es una forma de que se quede vieja sin que nadie se entere.

-- Los repositorios escriben sus sentencias como `bd_facturacion`.`usuarios`, con el nombre
-- de la base delante. MySQL se lo come porque es el nombre de la base de datos; H2 lo toma
-- como un ESQUEMA, así que hay que crear ese esquema y poner las tablas dentro, o el nombre
-- de la base no aparece por ningún lado.
CREATE SCHEMA IF NOT EXISTS bd_facturacion;

DROP TABLE IF EXISTS bd_facturacion.usuarios;

CREATE TABLE bd_facturacion.usuarios (
  idusuario      INT AUTO_INCREMENT PRIMARY KEY,
  usuario        VARCHAR(50)  NOT NULL,
  clave_hash     VARCHAR(100) NOT NULL,
  nombre         VARCHAR(100) NOT NULL,
  rol            VARCHAR(20)  NOT NULL DEFAULT 'USUARIO',
  activo         TINYINT(1)   NOT NULL DEFAULT 1,
  fecha_alta     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  ultimo_acceso  TIMESTAMP    DEFAULT NULL,
  CONSTRAINT usuario_UNIQUE UNIQUE (usuario)
);

-- Para poder borrar clientes, que es de lo único que se ocupa un test de esta
-- clase. Las columnas son las que lee el repositorio al listar; no hace falta más.
--
-- SIN esquema delante a propósito, porque el repositorio de clientes escribe "FROM clientes"
-- a pelo (el de usuarios sí pone el nombre de la base, y por eso su tabla va en el esquema).
CREATE TABLE clientes (
  idcliente    INT AUTO_INCREMENT PRIMARY KEY,
  nombre       VARCHAR(60) NOT NULL,
  nif_cif      VARCHAR(10) NOT NULL,
  direccion    VARCHAR(90) NOT NULL,
  codigopostal VARCHAR(6),
  poblacion    VARCHAR(30) NOT NULL,
  provincia    VARCHAR(15) NOT NULL,
  telefono     VARCHAR(15),
  email        VARCHAR(30),
  fecha_alta   DATE
);
-- El repositorio del emisor sí pone el nombre de la base delante, así que su tabla va en el
-- esquema. Es la que usa el test de "los datos del emisor solo los cambia un ADMIN".
CREATE TABLE bd_facturacion.emisor (
  idemisor  INT AUTO_INCREMENT PRIMARY KEY,
  nombre    VARCHAR(60) NOT NULL,
  nif_cif   VARCHAR(10) NOT NULL,
  direccion VARCHAR(200) NOT NULL,
  email     VARCHAR(50),
  telefono  VARCHAR(15),
  logo      BLOB
);
