# SentinelC2 - Distributed Honeypot & Command & Control Platform

**Versión 2.1.0**

> **AVISO IMPORTANTE DE SEGURIDAD Y ÉTICA (TRABAJO ACADÉMICO)**
>
> Este proyecto ha sido desarrollado con fines estrictamente **académicos, educativos y de investigación en ciberseguridad** dentro de un **laboratorio aislado y controlado**.
> 
> Las técnicas implementadas (Honeypot de alta interacción, perfilado de visitantes, evasión de bots, emulación de servicios y análisis forense con LLM) están diseñadas exclusivamente para comprender el comportamiento de los adversarios y evaluar mecanismos de defensa.
> 
> **Queda terminantemente prohibido su despliegue o utilización contra sistemas, redes, infraestructuras o personas sin autorización previa, expresa y por escrito.** El autor y los colaboradores no se hacen responsables del uso indebido, negligente o ilícito que terceros puedan dar al código o a las herramientas aquí contenidas.

---

## Descripción del Proyecto

**SentinelC2** es un ecosistema integral de ciberseguridad defensiva y ofensiva controlada. Centraliza en una aplicación de escritorio Command & Control (C2) la orquestación de señuelos (Honeypots web y SSH), un monitor de telemetría de red en tiempo real, un motor simulador de botnets con técnicas avanzadas de evasión (Stealth) para pruebas de carga y resiliencia, y un generador automatizado de ciberinteligencia forense asistido por modelos de lenguaje en la nube (**Llama 3.3 70B** a través de **Groq Cloud**).

---

## Arquitectura y Módulos del Sistema

El ecosistema está desacoplado en cuatro módulos interconectados mediante sockets, JDBC y APIs REST:

```
                          ┌────────────────────────────────────────┐
                          │         ATACANTE / ADVERSARIO          │
                          └───────────────────┬────────────────────┘
                                              │ (HTTP / SSH)
                                              ▼
                        ┌───────────────────────────────────────────┐
                        │          CAPA DE SEÑUELO (HONEYPOT)       │
                        │  - LoginWeb (Tomcat 10 / Jakarta EE)      │
                        │  - Telemetría Frontend (WebRTC, Canvas,  │
                        │    Biometría de tecleo, Batería, Audio)   │
                        │  - Sentinel-SSH (Paramiko SSH Honeypot)   │
                        └─────────────────────┬─────────────────────┘
                                              │
                      ┌───────────────────────┴───────────────────────┐
                      ▼                                               ▼
          ┌───────────────────────────┐                 ┌───────────────────────────┐
          │     MONITOR DE RED        │                 │    BASE DE DATOS MYSQL    │
          │   (Node.js + Socket.IO)   │                 │       (honeypot_db)       │
          │  - Ingesta logs Tomcat    ├────────────────►│  - Capturas forenses      │
          │  - Alertas en tiempo real │ (Persistencia)  │  - Bloqueos de fuerza bruta│
          └─────────────┬─────────────┘                 │  - Logs de tráfico        │
                        │ (WebSocket :3001)             └─────────────┬─────────────┘
                        │                                             │ (JDBC)
                        ▼                                             ▼
          ┌─────────────────────────────────────────────────────────────────────────┐
          │                    CENTRO DE MANDO: DESKTOPAPP (C2)                     │
          │  - Interfaz gráfica en Java 17 / Swing (FlatLaf Dark Theme)             │
          │  - Dashboard de telemetría, mapa térmico y gestión de servicios         │
          │  - Orquestador de subprocesos locales (Tomcat, Node, Python)            │
          │  - Generación de reportes forenses con IA (Groq Cloud API / Llama 3.3)   │
          └─────────────────────────────────────────────────────────────────────────┘
                                              ▲
                                              │ (Pruebas de estrés y evasión)
                        ┌─────────────────────┴─────────────────────┐
                        │          MÓDULO OFENSIVO (SIMULADO)       │
                        │  - Ataque_Bots (Node.js + Playwright)     │
                        │  - Stealth Plugin, Rotación de Proxies    │
                        │  - Fragmentación de diccionarios de login │
                        └───────────────────────────────────────────┘
```

### 1. `DesktopApp/SentinelC2` (Centro de Operaciones y Mando)
- Aplicación de escritorio desarrollada en **Java 17 (Swing)** con la suite moderna de interfaz **FlatLaf**.
- Visualiza la telemetría en vivo mediante un ticker SIEM conectado al Monitor de Red.
- Integra el cliente HTTP nativo `GroqClient.java` para compilar los eventos de ataque y solicitar informes tácticos ejecutivos al modelo **Llama-3.3-70B**.
- Incluye herramientas de auditoría de red, control de subprocesos y emulador SSH.

### 2. `LoginWeb` (Honeypot Web de Alta Interacción)
- Aplicación web para **Apache Tomcat 10 (Jakarta EE)**.
- Expone un portal administrativo señuelo con flujo de consentimiento legal.
- Recolecta telemetría profunda en el navegador del atacante:
  - Fuga de IP privada local y mDNS mediante WebRTC (`RTCPeerConnection`).
  - Dinámica de tecleo (Keystroke Dynamics) y cálculo de latencia inter-tecla.
  - Huella de hardware (GPU vendor/renderer, nivel de batería, AudioContext fingerprint).
  - Verificación de puertos locales y mapeo de subred LAN.

### 3. `Monitor_red` (Motor de Ingesta y WebSockets)
- Servicio en **Node.js** que realiza lectura continua (*tailing*) de los registros de acceso de Tomcat.
- Normaliza los eventos HTTP, detecta ráfagas anómalas (código 404/403) y los persiste en la base de datos MySQL.
- Emite eventos instantáneos por **Socket.IO** hacia el C2 de escritorio.

### 4. `Ataque_Bots` (Simulador de Botnet y Estrés Táctico)
- Motor ofensivo automatizado con **Playwright** y **puppeteer-extra-plugin-stealth**.
- Simula ataques de fuerza bruta distribuidos sobre el login del Honeypot:
  - Parcheo del motor Chromium para ocultar firmas automatizadas (`navigator.webdriver`).
  - Rotación dinámica de User-Agents y resoluciones de pantalla para evadir firmas de Canvas.
  - Soporte de enrutamiento a través de proxies HTTP/SOCKS.

---

## Tecnologías y Librerías Utilizadas

| Componente | Tecnologías Clave |
| :--- | :--- |
| **C2 Desktop** | Java 17+, Swing, FlatLaf, MySQL Connector/J, Paramiko (Python 3.11) |
| **IA / Ciberinteligencia** | Groq Cloud API, Llama 3.3 70B Versatile, JSON Schema |
| **Honeypot Web** | Jakarta EE 10, Apache Tomcat 10, JavaScript (ES6+), WebRTC API, Canvas API |
| **Monitor de Red** | Node.js (v18+ / v20+), Socket.IO, MySQL2, Dotenv |
| **Simulador de Ataques** | Node.js, Playwright Extra, Puppeteer Stealth Plugin |
| **Persistencia** | MySQL 8.0+, DDL relacional con índices optimizados |

---

## Guía de Instalación y Puesta en Marcha

### Prerrequisitos
- **Java Development Kit (JDK) 17 o superior** instalado y configurado en el `PATH`.
- **Node.js (v18+ o v20+)** y gestor `npm`.
- **Python 3.10+** (opcional, para módulos complementarios de Pentbox/SSH).
- **MySQL Server 8.0+** en ejecución local.
- **Apache Tomcat 10.1+** (para desplegar `LoginWeb.war`).

---

### Paso 1: Configurar la Base de Datos (Solo Esquema)
1. Inicia sesión en tu servidor MySQL local.
2. Ejecuta el script con la estructura de tablas limpia provisto en el repositorio:
```bash
mysql -u root -p < honeypot_schema.sql
```
*(Nota: Este script crea la base de datos `honeypot_db` y todas sus tablas sin ningún registro).*

---

### Paso 2: Configuración de Variables de Entorno (`.env`)
El proyecto no contiene credenciales fijas en el código. Para configurarlo:

1. Revisa la plantilla central [`.env.example`](.env.example) situada en la raíz del proyecto:
```ini
# --- Inteligencia Artificial y Análisis Forense (SentinelC2) ---
GROQ_API_KEY=tu_groq_api_key_aqui

# --- Conexión a Base de Datos Central (MySQL) ---
DB_HOST=localhost
DB_PORT=3306
DB_NAME=honeypot_db
DB_USER=root
DB_PASSWORD=tu_contraseña_mysql

# --- Módulos Auxiliares ---
WS_PORT=3001
BOTS_DB_NAME=bots_infiltradores
```

2. Configura las variables en tu entorno de sistema operativo o copia el archivo correspondiente en los módulos:
   - En **`Monitor_red/`**:
     ```bash
     cd Monitor_red
     cp .env.example .env
     npm install
     ```
   - En **`Ataque_Bots/`**:
     ```bash
     cd ../Ataque_Bots
     cp .env.example .env
     npm install
     npx playwright install chromium
     ```

---

### Paso 3: Despliegue del Honeypot Web (`LoginWeb`)
1. Empaqueta el archivo `.war` desde el directorio `LoginWeb`:
```bash
cd LoginWeb
mvn clean package
```
2. Despliega el artefacto resultante `LoginWeb.war` (ubicado en `LoginWeb/target/`) dentro del directorio `webapps/` de tu servidor Apache Tomcat 10.
3. Verifica la configuración de tu DataSource en `conf/context.xml` o en `META-INF/context.xml` asegurándote de que coincida con las credenciales de tu MySQL local.

---

### Paso 4: Ejecución del C2 Desktop (`SentinelC2`)
Puedes compilar y ejecutar la aplicación de escritorio mediante Maven:
```bash
cd DesktopApp/SentinelC2
mvn clean package
java -jar target/SentinelC2-2.1.0.jar
```
*Si tienes definida la variable de entorno `GROQ_API_KEY`, SentinelC2 la reconocerá automáticamente para habilitar la generación de reportes con IA.*

---

## Advertencia Legal y Ética (Disclaimer)

1. **Uso Exclusivo en Laboratorio:** Este software ha sido creado como trabajo de fin de ciclo / asignatura universitaria para la experimentación en ingeniería de seguridad informática. Su uso está acotado a entornos controlados (*sandbox*, máquinas virtuales o redes locales de prueba).
2. **Cumplimiento Normativo:** La recopilación de telemetría de visitantes en entornos no controlados puede estar sujeta a regulaciones de protección de datos (como el RGPD europeo). En el módulo `LoginWeb` se incluye una pantalla interactiva de consentimiento informado (`index.html`) con fines didácticos para ilustrar el principio de transparencia.
3. **Exención de Responsabilidad:** Ni el autor ni las instituciones académicas vinculadas avalan el uso de este proyecto para actividades ilícitas, acceso no autorizado a sistemas informáticos o ataques de denegación de servicio.
