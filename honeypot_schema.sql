-- =============================================================================
-- Sentinel C2 - Esquema de Base de Datos (DDL)
-- =============================================================================
-- PROYECTO ACADÉMICO - ENTORNO CONTROLADO DE LABORATORIO
-- Este archivo define exclusivamente la estructura de tablas y vistas
-- necesarias para ejecutar la plataforma Sentinel C2, SIN ningún dato
-- real, sin registros de telemetría de terceros ni historiales de intrusiones.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `honeypot_db` 
  DEFAULT CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

USE `honeypot_db`;

-- -----------------------------------------------------------------------------
-- 1. Tabla: bloqueos_login
-- Gestiona el control de intentos fallidos y bloqueo temporal por IP/Visitor ID
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `bloqueos_login`;
CREATE TABLE `bloqueos_login` (
  `identificador` varchar(255) NOT NULL,
  `intentos` int DEFAULT 0,
  `ultimo_intento` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `bloqueado_hasta` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`identificador`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 2. Tabla: capturas
-- Telemetría forense obtenida en el login simulado (Honeypot Web)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `capturas`;
CREATE TABLE `capturas` (
  `id` int NOT NULL AUTO_INCREMENT,
  `fecha` datetime DEFAULT CURRENT_TIMESTAMP,
  `ip_address` varchar(45) NOT NULL,
  `ip_privada` varchar(45) DEFAULT NULL,
  `usuario` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `password_en_plano` text,
  `is_bot` tinyint DEFAULT 0,
  `biometria` text,
  `intentos` int DEFAULT NULL,
  `ubicacion` varchar(150) DEFAULT NULL,
  `latitud` double DEFAULT NULL,
  `longitud` double DEFAULT NULL,
  `bateria` varchar(10) DEFAULT NULL,
  `modelo` varchar(255) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `consentimiento` tinyint(1) DEFAULT 0,
  `raw_json` longtext,
  `visitor_id` varchar(64) DEFAULT 'Desconocido',
  `perfil_ataque` varchar(100) DEFAULT 'Usuario Estandar',
  PRIMARY KEY (`id`),
  KEY `idx_visitor` (`visitor_id`),
  KEY `idx_ip` (`ip_address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. Tabla: challenge
-- Credenciales anzuelo / trampa configuradas para verificar intentos de acceso
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `challenge`;
CREATE TABLE `challenge` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(50) NOT NULL,
  `password_hash` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Opcional: Semilla básica de prueba para laboratorio (descomentar si se desea)
-- INSERT INTO `challenge` (`username`, `password`, `password_hash`) VALUES
-- ('admin', 'admin', SHA2('admin', 512)),
-- ('root', 'toor', SHA2('toor', 512));

-- -----------------------------------------------------------------------------
-- 4. Tabla: logs_trafico_ngrok
-- Registro de tráfico HTTP entrante a través de túneles o proxy reverso
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `logs_trafico_ngrok`;
CREATE TABLE `logs_trafico_ngrok` (
  `id` int NOT NULL AUTO_INCREMENT,
  `ip_origen` varchar(45) DEFAULT NULL,
  `metodo` varchar(45) DEFAULT NULL,
  `url_solicitada` text,
  `codigo_respuesta` int DEFAULT NULL,
  `user_agent` text,
  `fecha_hora` datetime DEFAULT NULL,
  `es_sospechoso` tinyint DEFAULT 0,
  `pais` varchar(100) DEFAULT NULL,
  `latitud` float DEFAULT NULL,
  `longitud` float DEFAULT NULL,
  `metodo_http` varchar(10) DEFAULT NULL,
  `fecha_log` datetime DEFAULT NULL,
  `es_heuristico` tinyint(1) DEFAULT NULL,
  `conteo_404_minuto` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ip_origen` (`ip_origen`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 5. Tabla: exitos_bots
-- Registro de infiltraciones simuladas por el módulo Ataque_Bots
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `exitos_bots`;
CREATE TABLE `exitos_bots` (
  `id` int NOT NULL AUTO_INCREMENT,
  `usuario` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `identity_used` text,
  `fecha` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 6. Vista: passwords
-- Vista de compatibilidad sobre la tabla de desafíos
-- -----------------------------------------------------------------------------
DROP VIEW IF EXISTS `passwords`;
CREATE VIEW `passwords` AS 
  SELECT `id`, `username`, `password` 
  FROM `challenge`;
