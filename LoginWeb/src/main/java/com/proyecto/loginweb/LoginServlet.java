/**
 * @file LoginServlet.java
 * @brief Servlet de Login Honeypot - Capa de Recolección Forense y Perfilado de Amenazas.
 * @details Este servlet actúa como el "Endpoint de Captura" central del Sentinel C2. 
 * Su función principal es interceptar intentos de acceso y procesar la telemetría "Elite" 
 * enviada desde el frontend, realizando un análisis de capas (IP, Hardware, Biometría) 
 * para identificar y persistir la identidad del atacante.
 * 
 * @author Felipe Andrés
 * @version 2.1.0
 */

package com.proyecto.loginweb;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.io.PrintWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.json.JSONObject;

/**
 * @class LoginServlet
 * @brief Clase controladora del flujo de autenticación y captura forense.
 * @details Implementa la interfaz HttpServlet para gestionar peticiones POST que contienen
 * credenciales y volcados JSON de telemetría avanzada. Utiliza inyección de recursos JDBC.
 */
public class LoginServlet extends HttpServlet {

    /** 
     * @brief DataSource gestionado por el contenedor (Apache Tomcat).
     * @details Proporciona el pool de conexiones configurado en context.xml (jdbc/honeypot_db).
     */
    @Resource(name = "jdbc/honeypot_db")
    private DataSource dataSource;

    /**
     * @brief Procesa peticiones HTTP comunes.
     * @param request Petición del cliente.
     * @param response Respuesta del servidor.
     * @throws ServletException Si ocurre un error interno del servlet.
     * @throws IOException Si ocurre un error de E/S.
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html><body><h1>¡Conexión Exitosa!</h1></body></html>");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * @brief Ciclo de vida: Inicialización del Servlet.
     * @details Verifica la disponibilidad del pool de conexiones inyectado por el servidor.
     * @throws ServletException Si el servlet falla al iniciar.
     */
    @Override
    public void init() throws ServletException {
        System.out.println("[SENTINEL_SERVLET] >>> INIT: Servlet Inicializado <<<");
        if (dataSource == null) {
            System.err.println("[SENTINEL_SERVLET] ERROR: DataSource jdbc/honeypot_db NO INYECTADO en init()");
        } else {
            System.out.println("[SENTINEL_SERVLET] OK: DataSource inyectado correctamente.");
        }
    }

    /**
     * @brief Manejador de intentos de login (Core del Honeypot).
     * @details Realiza los siguientes pasos:
     * 1. Extracción de IP real detectando Proxies (NGROK/Cloudflare).
     * 2. Deserialización parcial de telemetría para obtención de VisitorID.
     * 3. Verificación de bloqueos temporales por fuerza bruta.
     * 4. Validación de credenciales contra el Challenge DB.
     * 5. Disparo del motor de registro forense dinámico.
     * 
     * @param request Petición POST con datos de login y telemetría.
     * @param response Redirección táctica (éxito o reintento).
     * @throws ServletException Error de contenedor.
     * @throws IOException Error de red.
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        System.out.println("[SENTINEL_SERVLET] >>> ENTRY: doPost iniciado <<<");
        
        // --- 1. DETECCIÓN DE IP REAL (Proxy-Awareness) ---
        String ipCliente = request.getRemoteAddr();
        if (ipCliente == null || ipCliente.equals("127.0.0.1") || ipCliente.equals("0:0:0:0:0:0:0:1")) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                ipCliente = xForwardedFor.split(",")[0].trim();
            }
        }

        // --- 2. CAPTURA DE PARÁMETROS TÁCTICOS ---
        String usuario = request.getParameter("usuario");
        String passOriginal = request.getParameter("password");
        String jsonString = request.getParameter("todo_el_json_de_js");
        String visitorId = "Desconocido";

        // Pre-procesamiento de telemetría para identificar al sujeto
        if (jsonString != null && !jsonString.trim().isEmpty()) {
            try {
                JSONObject data = new JSONObject(jsonString);
                visitorId = data.optJSONObject("meta") != null
                        ? data.optJSONObject("meta").optString("visitor_id", "Desconocido")
                        : "Desconocido";
            } catch (Exception e) {
                System.err.println("[SENTINEL_SERVLET] Error en Parsing Pre-Forense: " + e.getMessage());
            }
        }

        String idBloqueo = ipCliente + "_" + visitorId;
        String passParaValidar = generarHashSHA512(passOriginal);

        try (Connection conn = dataSource.getConnection()) {

            // --- 3. GESTIÓN DE BLOQUEOS ANTI-FUERZA BRUTA ---
            String sqlCheckBloqueo = "SELECT intentos, bloqueado_hasta FROM bloqueos_login WHERE identificador = ?";
            try (PreparedStatement pseudoCheck = conn.prepareStatement(sqlCheckBloqueo)) {
                pseudoCheck.setString(1, idBloqueo);
                try (ResultSet rsB = pseudoCheck.executeQuery()) {
                    if (rsB.next()) {
                        java.sql.Timestamp bloqHasta = rsB.getTimestamp("bloqueado_hasta");
                        if (bloqHasta != null && bloqHasta.getTime() > System.currentTimeMillis()) {
                            response.sendRedirect("espera.html");
                            return;
                        }
                    }
                }
            }

            // --- 4. VALIDACIÓN DE CREDENCIALES ---
            boolean esCorrecto = false;
            String sqlDesafio = "SELECT 1 FROM challenge WHERE username = ? AND password_hash = ?";
            try (PreparedStatement pstmtD = conn.prepareStatement(sqlDesafio)) {
                pstmtD.setString(1, usuario);
                pstmtD.setString(2, passParaValidar);
                try (ResultSet rsD = pstmtD.executeQuery()) {
                    if (rsD.next()) esCorrecto = true;
                }
            }

            // --- 5. PERSISTENCIA DE ESTADO DE ATAQUE ---
            int intentosActuales = 0;
            if (esCorrecto) {
                // Éxito: Limpieza de registros de bloqueo
                try (PreparedStatement pr = conn.prepareStatement("DELETE FROM bloqueos_login WHERE identificador = ?")) {
                    pr.setString(1, idBloqueo);
                    pr.executeUpdate();
                }
            } else {
                // Fallo: Incremento de intentos (Persistente en BD)
                String sqlUpdate = "INSERT INTO bloqueos_login (identificador, intentos, ultimo_intento) VALUES (?, 1, CURRENT_TIMESTAMP) " +
                                   "ON DUPLICATE KEY UPDATE intentos = intentos + 1, ultimo_intento = CURRENT_TIMESTAMP";
                try (PreparedStatement pu = conn.prepareStatement(sqlUpdate)) {
                    pu.setString(1, idBloqueo);
                    pu.executeUpdate();
                }

                // Verificación de umbral de bloqueo (3 intentos)
                try (PreparedStatement pr = conn.prepareStatement(sqlCheckBloqueo)) {
                    pr.setString(1, idBloqueo);
                    try (ResultSet rsR = pr.executeQuery()) {
                        if (rsR.next()) {
                            intentosActuales = rsR.getInt("intentos");
                            if (intentosActuales >= 3) {
                                try (PreparedStatement pf = conn.prepareStatement("UPDATE bloqueos_login SET bloqueado_hasta = DATE_ADD(NOW(), INTERVAL 1 DAY) WHERE identificador = ?")) {
                                    pf.setString(1, idBloqueo);
                                    pf.executeUpdate();
                                }
                                response.sendRedirect("espera.html");
                                return;
                            }
                        }
                    }
                }
            }

            // --- 6. REGISTRO FORENSE PROFUNDO ---
            registrarIntento(conn, ipCliente, usuario, passParaValidar, (esCorrecto ? 1 : intentosActuales), request);

            // Redirección final según resultado
            if (esCorrecto) {
                response.sendRedirect("portal_admin_dashboard.html");
            } else {
                response.sendRedirect("admin.html?error=" + intentosActuales);
            }

        } catch (Exception e) {
            System.err.println("[SENTINEL_SERVLET] ERROR CRITICO en doPost");
            e.printStackTrace(System.err);
            response.sendRedirect("admin.html?error=sys");
        }
    }

    /**
     * @brief Consulta externa de geolocalización de alta precisión.
     * @details Utiliza HttpClient de Java para consultar APIs de terceros (ipwho.is). 
     * Esta consulta se realiza desde el servidor para evitar bloqueos por parte del navegador del atacante.
     * @param ip Dirección IP pública a geolocalizar.
     * @return JSONObject con los datos geográficos o null si falla.
     */
    private JSONObject consultGeoAPI(String ip) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://ipwho.is/" + ip))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) return new JSONObject(response.body());
        } catch (Exception e) {
            System.err.println("[SERVER_GEO] Error en consulta: " + e.getMessage());
        }
        return null;
    }

    /**
     * @brief Mapea y guarda la telemetría "Elite" en la base de datos.
     * @details Realiza el desempaquetado del JSON forense enviado desde el cliente, realizando
     * un perfilado automático del atacante basado en el hardware y la red detectada.
     * @param conn Conexión SQL activa.
     * @param ip IP Pública detectada.
     * @param user Nombre de usuario intentado.
     * @param pass Hash de la contraseña.
     * @param numIntento Número de intento actual.
     * @param req Petición HTTP para extracción de cabeceras crudas.
     * @throws Exception Si ocurre un fallo en la persistencia.
     */
    private void registrarIntento(Connection conn, String ip, String user, String pass, int numIntento, HttpServletRequest req) throws Exception {
        String jsonString = req.getParameter("todo_el_json_de_js");
        if (jsonString == null || jsonString.trim().isEmpty()) return;

        try {
            JSONObject data = new JSONObject(jsonString);
            JSONObject hardware = data.optJSONObject("hardware", new JSONObject());
            JSONObject flujo = data.optJSONObject("analisis_flujo", new JSONObject());
            JSONObject red = data.optJSONObject("red", new JSONObject());
            JSONObject elite = data.optJSONObject("elite", new JSONObject());

            String visitorId = data.optJSONObject("meta") != null ? data.optJSONObject("meta").optString("visitor_id", "Desconocido") : "Desconocido";
            String biometriaRaw = data.optJSONObject("meta") != null && data.optJSONObject("meta").optJSONArray("biometria_teclado") != null 
                                ? data.optJSONObject("meta").optJSONArray("biometria_teclado").toString() : "N/A";

            // Limpieza de JSON para log persistente
            if (data.optJSONObject("meta") != null) data.optJSONObject("meta").remove("biometria_teclado");
            String jsonLimpio = data.toString();

            // --- MOTOR DE PERFILADO INTELIGENTE (Atacante vs Bot) ---
            String perfilAtaque = "Usuario Estandar";
            String honeyField = req.getParameter("honeypot_check");
            int esBot = (honeyField != null && !honeyField.isEmpty()) ? 1 : 0;

            if (esBot == 1) {
                perfilAtaque = "Bot / Automatizacion (Honeypot Triggered)";
            } else {
                boolean esMovil = hardware.optJSONObject("entorno_movil") != null && hardware.optJSONObject("entorno_movil").optBoolean("touch", false);
                if (elite.optJSONArray("puertos_abiertos") != null && elite.optJSONArray("puertos_abiertos").length() > 0 && !esMovil) {
                    perfilAtaque = "Atacante Tecnico (Analisis de Puertos)";
                } else if (esMovil) {
                    perfilAtaque = "Atacante Movil (Touch Device)";
                }
            }

            // --- GEOLOCALIZACIÓN AUTORITATIVA (Server-Side) ---
            String finalUbicacion = "Desconocido";
            double finalLat = 0, finalLong = 0;
            JSONObject geo = consultGeoAPI(ip);
            if (geo != null && geo.optBoolean("success", false)) {
                finalUbicacion = geo.optString("city") + ", " + geo.optString("country") + " (SVR)";
                finalLat = geo.optDouble("latitude", 0);
                finalLong = geo.optDouble("longitude", 0);
            }

            // PERSISTENCIA FINAL
            String sql = "INSERT INTO capturas(ip_address, ip_privada, usuario, password, password_en_plano, biometria, user_agent, ubicacion,"
                    + "latitud, longitud, bateria, modelo, is_bot, intentos, consentimiento, visitor_id, perfil_ataque, raw_json) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

            try (PreparedStatement p = conn.prepareStatement(sql)) {
                p.setString(1, ip); 
                p.setString(2, elite.optString("ip_privada", "N/A")); 
                p.setString(3, user);
                p.setString(4, pass); 
                p.setString(5, req.getParameter("password")); 
                p.setString(6, biometriaRaw);
                // --- RESOLUCIÓN DEL "CHURRO" DE USER AGENT ---
                // Priorizamos el UA capturado por el JS (Forense) sobre el del Header (que puede venir con basura de proxies)
                String uaFinal = (hardware != null) ? hardware.optString("ua", req.getHeader("User-Agent")) : req.getHeader("User-Agent");
                p.setString(7, uaFinal);
                p.setString(8, finalUbicacion);
                p.setDouble(9, finalLat);
                p.setDouble(10, finalLong);
                p.setString(11, hardware.optJSONObject("bateria") != null ? hardware.optJSONObject("bateria").optString("nivel", "N/A") : "N/A");
                p.setString(12, hardware.optString("gpu", "Desconocido")); 
                p.setInt(13, esBot);
                p.setInt(14, numIntento);
                p.setInt(15, flujo.optBoolean("aceptado", false) ? 1 : 0);
                p.setString(16, visitorId);
                p.setString(17, perfilAtaque); 
                p.setString(18, jsonLimpio); 
                p.executeUpdate();
            }
        } catch (Exception ex) {
            System.err.println("[SENTINEL_SERVLET] Error en volcado forense: " + ex.getMessage());
        }
    }

    /**
     * @brief Generador de hash SHA-512.
     * @details Convierte una cadena de texto en un hash irreversible para almacenamiento seguro.
     * @param password Texto plano a hashear.
     * @return Cadena hexadecimal de 128 caracteres.
     */
    private String generarHashSHA512(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-512");
            byte[] bytes = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Error Algoritmo SHA-512", e);
        }
    }

    @Override
    public String getServletInfo() {
        return "Servidor Forense Honeypot v3.0 - Sentinel Intelligence";
    }
}
