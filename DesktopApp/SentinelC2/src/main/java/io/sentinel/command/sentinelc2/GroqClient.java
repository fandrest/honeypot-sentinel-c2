package io.sentinel.command.sentinelc2;

import java.awt.Color;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * Motor de Interfaz de Inteligencia Artificial (LLM Cloud) para SentinelC2.
 * 
 * Clase estática encargada de establecer la comunicación HTTP hacia
 * la API de Groq Cloud utilizando el modelo Llama-3.3-70B.
 * Este componente es el responsable de evaluar el estado táctico de la
 * plataforma C2 y retornar un análisis forense emulado para el operador.
 *
 * @version 2.1.0
 * @author Felipe Andrés
 */
public class GroqClient {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile";

    /**
     * Genera un informe forense basado en los registros actuales del C2.
     * @param apiKey La API Key de Groq.
     * @return El informe generado o un mensaje de error.
     */
    public static String generateReport(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "ERROR: API Key de Groq no proporcionada.";
        }

        try {
            URL url = new URL(GROQ_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);

            // Payload mínimo para el reporte
            String prompt = "Eres Sentinel AI. Genera un breve informe táctico de seguridad (estilo hacker ciberpunk) "
                    + "resumiendo que el sistema C2 está operativo, MySQL está activo y el Honeypot está listo para captura.";
            
            String jsonInput = "{"
                    + "\"model\": \"" + MODEL + "\","
                    + "\"messages\": [{\"role\": \"user\", \"content\": \"" + prompt + "\"}]"
                    + "}";

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes("utf-8");
                os.write(input, 0, input.length);           
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                Scanner s = new Scanner(conn.getInputStream(), "utf-8").useDelimiter("\\A");
                String response = s.hasNext() ? s.next() : "";
                // Extracción rudimentaria del contenido (simplificado para el demo)
                if (response.contains("\"content\":\"")) {
                    int start = response.indexOf("\"content\":\"") + 11;
                    int end = response.indexOf("\"}", start);
                    return response.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
                }
                return "Informe generado con éxito (Ver logs para detalles).";
            } else {
                return "Error en la nube Groq (Código: " + code + ")";
            }

        } catch (Exception e) {
            return "Fallo de conexión con Groq: " + e.getMessage();
        }
    }

    /**
     * @brief Generador de Reportes Tácticos via IA.
     * @details Envía el contexto forense (IPs, Fingerprints, Ataques) a la nube 
     * para que un modelo LLM genere un informe estilizado (Cyberpunk) con 
     * recomendaciones de seguridad.
     * @param apiKey Llave de autenticación de Groq Cloud.
     * @param context Datos tácticos del Honeypot.
     * @return El reporte generado o un mensaje de error detallado.
     */
    public static String generateReport(String apiKey, String context) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "ERROR: API Key de Groq no proporcionada.";
        }

        try {
            URL url = new URL(GROQ_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            
            // TIMEOUTS para evitar congelamientos si la API tarda
            conn.setConnectTimeout(5000); 
            conn.setReadTimeout(15000);

            String prompt = "Eres Sentinel AI, un motor de análisis forense avanzado. "
                    + "Analiza los siguientes datos de nuestro Honeypot (ataques, hardware y brechas de credenciales)."
                    + "Genera un informe táctico (estilo hacker/cyberpunk) destacando IPs peligrosas, "
                    + "vulnerabilidades en los diccionarios de contraseñas detectados, y telemetría de dispositivos (batería, modelo)."
                    + "Proporciona recomendaciones estratégicas concisas.\n\n"
                    + "Telemetría:\n" + context;
            
            // Escapado Robusto para el JSON
            prompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");

            String jsonInput = "{"
                    + "\"model\": \"" + MODEL + "\","
                    + "\"messages\": [{\"role\": \"user\", \"content\": \"" + prompt + "\"}]"
                    + "}";

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes("utf-8");
                os.write(input, 0, input.length);           
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                // Leer respuesta con codificación explícita
                java.io.InputStream in = conn.getInputStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                
                String rStr = response.toString();
                // Extracción Resiliente: Buscamos el contenido entre "content":" y el final del string literal
                if (rStr.contains("\"content\":\"")) {
                    int start = rStr.indexOf("\"content\":\"") + 11;
                    // Buscamos el último " antes del cierre del objeto choices
                    int end = rStr.lastIndexOf("\""); 
                    // Refinamiento: Groq suele terminar con "}], "usage":...
                    int possibleEnd = rStr.indexOf("\",\"role\"", start);
                    if (possibleEnd == -1) possibleEnd = rStr.indexOf("\"}", start);
                    
                    if (possibleEnd != -1) end = possibleEnd;
                    
                    return rStr.substring(start, end)
                               .replace("\\n", "\n")
                               .replace("\\\"", "\"")
                               .replace("\\\\", "\\");
                }
                return "Análisis Sentinel completado con éxito.";
            } else {
                return "Error Crítico Groq (HTTP " + code + "): Verifique su conexión o cuota de API.";
            }

        } catch (java.net.SocketTimeoutException te) {
            return "ERROR: Tiempo de espera agotado (Time-out). La IA está tardando demasiado en procesar la telemetría.";
        } catch (Exception e) {
            return "Fallo Sistémico de Comunicación: " + e.getMessage();
        }
    }
}
