/**
 * @file SentinelConsole.java
 * @brief Terminal de Eventos Tácticos del C2.
 * @details Componente gráfico especializado en la visualización de logs 
 * operacionales. Proporciona soporte para codificación de colores según 
 * la severidad del evento y gestión automática del foco de lectura.
 * 
 * @author Felipe Andrés
 * @version 2.1.5
 */

package io.sentinel.command.sentinelc2;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

/**
 * @class SentinelConsole
 * @brief Consola Dinámica con Estilizado de Texto.
 * @details Extiende JTextPane para proporcionar una interfaz de salida
 *          similar a una terminal de seguridad, con inyección de timestamps.
 */
public class SentinelConsole extends JTextPane {

    /**
     * @brief Constructor de la Consola.
     * @details Configura la paleta de colores "Void Black" y la tipografía
     *          monoespaciada para una lectura técnica óptima.
     */
    public SentinelConsole() {
        setEditable(false);
        setBackground(new Color(5, 5, 5));
        setFont(new Font("Monospaced", Font.PLAIN, 12));
    }

    /**
     * @brief Inserción de Mensajes Estilizados.
     * @details Inserta una cadena de texto en el documento asociado mediante
     *          el hilo de despacho de eventos (EDT), aplicando el color y
     *          timestamp.
     * @param msg   El contenido informativo.
     * @param color El color gráfico del texto.
     */
    public void log(String msg, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = getStyledDocument();
                Style style = addStyle("LogStyle", null);
                StyleConstants.setForeground(style, color);

                String timestamp = "[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + "] ";
                doc.insertString(doc.getLength(), timestamp + msg + "\n", style);

                // Autoscroll
                setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    public void log(String msg, String level) {
        Color c = switch (level.toUpperCase()) {
            case "ERROR" -> new Color(255, 85, 85); // Rojo suave
            case "WARN" -> new Color(255, 170, 0); // Naranja/Amarillo
            case "INFO" -> new Color(0, 255, 255); // Cian (Sentinel)
            case "SUCCESS" -> new Color(80, 250, 123); // Verde
            default -> Color.WHITE;
        };
        log("[" + level.toUpperCase() + "] " + msg, c);
    }
}
