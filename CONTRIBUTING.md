# Guía de Contribución a SentinelC2

¡Gracias por tu interés en colaborar con el proyecto académico **SentinelC2**! Este documento establece las directrices para contribuir al ecosistema de forma segura y ética.

---

## 1. Principio Fundamental: Entorno Aislado y Ética

- **Uso estricto en laboratorio:** Cualquier funcionalidad, prueba o módulo que aportes debe estar concebido para ejecutarse en entornos locales, controlados y aislados (redes sandbox, contenedores o VMs de pruebas).
- **Prohibición de herramientas maliciosas:** No se aceptarán contribuciones destinadas a realizar ataques no autorizados, exploits activos contra servicios reales o evasiones diseñadas para eludir defensas en infraestructuras ajenas.

---

## 2. Política de Cero Secretos (Zero Hardcoded Secrets)

Antes de enviar cualquier contribución (Pull Request):
1. **Nunca incluyas credenciales reales:** No introduzcas claves de API (como Groq, OpenAI, etc.), usuarios/contraseñas de bases de datos, tokens de túneles (Ngrok) ni datos de telemetría reales.
2. **Usa variables de entorno:** Toda configuración sensible o dependiente del host debe leerse a través de variables de entorno con fallback documentado en `.env.example`.
3. **No incluyas datos personales:** No añadas volcados SQL con IPs reales, huellas biométricas o registros de navegación de terceros.

---

## 3. Estructura y Estándares del Código

- **Java (DesktopApp y LoginWeb):**
  - Compatible con Java 17 LTS o superior.
  - Gestión de dependencias mediante Maven (`pom.xml`).
  - Respetar la modularidad de los paquetes (`io.sentinel.command.sentinelc2`, `com.proyecto.loginweb`).
- **Node.js (Monitor_red y Ataque_Bots):**
  - Usar sintaxis ES6+ moderna con manejo riguroso de promesas y `try/catch`.
  - No commitear la carpeta `node_modules/` ni ficheros `.env` locales.
- **Base de Datos (MySQL):**
  - Cualquier cambio en la estructura relacional debe reflejarse en `honeypot_schema.sql` (solo DDL, sin `INSERT INTO` con datos personales).

---

## 4. Proceso de Envío (Pull Request)

1. **Haz un Fork** del repositorio en GitHub.
2. **Crea una rama descriptiva:**
   ```bash
   git checkout -b feature/mejora-modulo-forense
   ```
3. **Realiza tus cambios y verifica el estado de Git:**
   ```bash
   git status
   ```
   Asegúrate de que no haya ficheros `.env`, `.log` o `.properties` con contraseñas en el listado de cambios.
4. **Haz commit con mensajes claros y concisos:**
   ```bash
   git commit -m "feat(c2): añadir soporte para filtrado de eventos por severidad"
   ```
5. **Envía tu rama al fork y abre una Pull Request (PR)** explicando el propósito del cambio y confirmando que ha sido probado en un laboratorio aislado.
