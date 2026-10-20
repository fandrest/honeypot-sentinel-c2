/**
 * @file ControlPanelFrame.java
 * @brief Panel de Análisis Táctico y Generación de Informes Forenses.
 * @details Esta clase se encarga de procesar los datos crudos de la base de datos 
 * para generar visualizaciones interactivas (mapas, gráficos) y reportes 
 * ejecutivos en formato HTML/JS.
 * 
 * @author Felipe Andrés
 * @version 5.2.0
 */

package io.sentinel.command.sentinelc2;

import javax.swing.JFrame;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.Map;
import java.util.List;
import java.io.FileWriter;
import java.io.File;

/**
 * Ventana emergente (Frame) utilizada para la visualización de reportes
 * tácticos y análisis forense generado por IA (Groq Cloud).
 *
 * @author Felipe Andrés
 * @version 3.0.0
 */
public class ControlPanelFrame extends JFrame {

    private String htmlContent;

    /**
     * Constructor del marco del Panel de Control.
     */
    public ControlPanelFrame(String iaReport, Map<String, Object> stats) {
        setTitle("SENTINEL C2 - TACTICAL REPORT");
        setSize(900, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");

        // Construir el HTML
        this.htmlContent = buildHtml(iaReport, stats);
        editorPane.setText(this.htmlContent);
        // Scroll al tope
        editorPane.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(editorPane);
        add(scroll, BorderLayout.CENTER);

        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pnlBottom.setBackground(new Color(10, 25, 47));

        JButton btnExport = new JButton("EXPORTAR INFORME (HTML)");
        btnExport.putClientProperty("FlatLaf.style", "arc: 0; borderWidth: 1; background: #00000000; borderColor: #00F2FF; foreground: #00F2FF; hoverForeground: #ffffff;");
        btnExport.addActionListener(e -> exportarHtml());

        pnlBottom.add(btnExport);
        add(pnlBottom, BorderLayout.SOUTH);
    }

    @SuppressWarnings("unchecked")
    private String escapeJs(Object obj) {
        if (obj == null) return "unknown";
        return String.valueOf(obj).replace("'", "\\'").replace("\"", "\\\"");
    }

    /**
     * MÉDODO ESTÁTICO: Genera el informe y lo abre inmediatamente sin mostrar
     * la ventana Swing (Headless Flow).
     */
    public static void generarYAbrirReporte(String iaReport, Map<String, Object> stats) {
        ControlPanelFrame frame = new ControlPanelFrame(iaReport, stats);
        frame.headlessExport();
    }

    private void headlessExport() {
        try {
            File reportsDir = new File("reportes");
            if (!reportsDir.exists()) reportsDir.mkdir();

            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new java.util.Date());
            File file = new File(reportsDir, "Sentinel_Report_" + timestamp + ".html");
            
            FileWriter fw = new FileWriter(file);
            fw.write(this.htmlContent);
            fw.close();
            
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(file.toURI());
            }
        } catch (Exception ex) {
            System.err.println("Error en exportación automática: " + ex.getMessage());
        }
    }

    private void exportarHtml() {
        try {
            String userHome = System.getProperty("user.home");
            File file = new File(userHome + "/Desktop/Sentinel_Report_" + System.currentTimeMillis() + ".html");
            FileWriter fw = new FileWriter(file);
            fw.write(this.htmlContent);
            fw.close();
            JOptionPane.showMessageDialog(this, "Informe exportado en:\n" + file.getAbsolutePath(), "Exportación Exitosa", JOptionPane.INFORMATION_MESSAGE);
            
            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(file.toURI());
                } else if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                    java.awt.Desktop.getDesktop().open(file);
                }
            } catch (Exception ignore) {
                // Ignore if it fails to open
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al exportar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @SuppressWarnings("unchecked")
    private String buildHtml(String iaReport, Map<String, Object> stats) {
        int totalIntentos = (int) stats.getOrDefault("total_intentos", 0);
        List<Map<String, Object>> topPwGlobal = (List<Map<String, Object>>) stats.get("top_passwords_global");
        List<Map<String, Object>> pwMatches = (List<Map<String, Object>>) stats.get("password_matches");
        List<Map<String, Object>> hwStats = (List<Map<String, Object>>) stats.get("hardware_stats");
        List<Map<String, Object>> heatMap = (List<Map<String, Object>>) stats.get("heatmap_data");

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<title>Sentinel C2 - Tactical Forensic Report</title>");
        
        // CDNs para Interactividad
        sb.append("<link rel='stylesheet' href='https://unpkg.com/leaflet/dist/leaflet.css' />");
        sb.append("<script src='https://unpkg.com/leaflet/dist/leaflet.js'></script>");
        sb.append("<script src='https://cdn.jsdelivr.net/npm/leaflet.heat@0.2.0/dist/leaflet-heat.js'></script>");
        sb.append("<script src='https://cdn.jsdelivr.net/npm/chart.js'></script>");
        sb.append("<link href='https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;700&display=swap' rel='stylesheet'>");

        sb.append("<style>");
        sb.append("body { background-color: #050b18; color: #00f2ff; font-family: 'Outfit', sans-serif; margin: 0; padding: 40px; overflow-x: hidden; }");
        sb.append("h1, h2 { color: #ffffff; text-transform: uppercase; letter-spacing: 2px; text-shadow: 0 0 15px rgba(0,242,255,0.5); border-left: 4px solid #00f2ff; padding-left: 15px; }");
        sb.append(".grid { display: grid; grid-template-columns: 1fr 1fr; gap: 25px; margin-bottom: 25px; }");
        sb.append(".card { background: linear-gradient(135deg, #0b1a30 0%, #050b18 100%); border: 1px solid #00f2ff33; padding: 25px; border-radius: 12px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); transition: 0.3s; position: relative; overflow: hidden; }");
        sb.append(".card:hover { border-color: #00f2ff; box-shadow: 0 0 20px rgba(0,242,255,0.2); }");
        sb.append(".card::before { content: ''; position: absolute; top:0; left:0; width:100%; height:2px; background: linear-gradient(90deg, transparent, #00f2ff, transparent); animation: scanLine 3s infinite; }");
        sb.append("@keyframes scanLine { 0% { transform: translateX(-100%); } 100% { transform: translateX(100%); } }");
        sb.append("#map { height: 400px; width: 100%; border-radius: 8px; border: 1px solid #00f2ff55; }");
        sb.append("table { width: 100%; border-collapse: separate; border-spacing: 0 8px; }");
        sb.append("th { color: #888; font-size: 11px; text-transform: uppercase; padding: 10px; text-align: left; }");
        sb.append("td { background: rgba(0,242,255,0.05); padding: 12px; font-size: 14px; border-top: 1px solid #00f2ff11; border-bottom: 1px solid #00f2ff11; }");
        sb.append("td:first-child { border-radius: 8px 0 0 8px; border-left: 1px solid #00f2ff11; }");
        sb.append("td:last-child { border-radius: 0 8px 8px 0; border-right: 1px solid #00f2ff11; }");
        sb.append(".status-indicator { display: inline-block; width: 10px; height: 10px; border-radius: 50%; background: #00f2ff; box-shadow: 0 0 10px #00f2ff; margin-right: 10px; }");
        sb.append(".danger-text { color: #ff4444; font-weight: bold; }");
        sb.append(".header-panel { display: flex; justify-content: space-between; align-items: center; margin-bottom: 40px; border-bottom: 1px solid #00f2ff22; padding-bottom: 20px; }");
        sb.append(".ticker { background: #ff444422; color: #ff4444; padding: 10px; font-family: monospace; font-size: 12px; border-radius: 4px; margin-top: 20px; overflow: hidden; white-space: nowrap; }");
        sb.append("</style></head><body>");

        // HEADER
        sb.append("<div class='header-panel'>");
        sb.append("<div><h1>SENTINEL C2 <span style='font-weight:300; opacity:0.6;'>TACTICAL INTELLIGENCE</span></h1>");
        sb.append("<p><span class='status-indicator'></span> STATUS: OPERATIONAL | NODES: ACTIVE | ENCRYPTION: SHA-512</p></div>");
        sb.append("<div style='text-align:right;'><p style='margin:0; opacity:0.5;'>VERSION 4.0.2 (ULTIMATE)</p>");
        sb.append("<p style='margin:0; font-weight:bold; color:#00f2ff;'>FORENSIC REPORT</p></div></div>");

        sb.append("<div class='grid'>");
            // 1. MAPA INTERACTIVO
            sb.append("<div class='card' style='grid-column: span 2;'>");
            sb.append("<h2>GLOBAL THREAT HEATMAP (INTERACTIVE)</h2>");
            sb.append("<div id='map'></div></div>");

            // 2. PASSWORD ANALYTICS
            sb.append("<div class='card'>");
            sb.append("<h2>DICT ATTACK FREQUENCY (ANALYTICS)</h2>");
            sb.append("<canvas id='passChart' style='max-height:300px;'></canvas></div>");

            // 3. SECURITY BREACHES (REAL USERS)
            sb.append("<div class='card'>");
            sb.append("<h2>CRITICAL BREACHES (IDENTIFIED)</h2>");
            sb.append("<table><tr><th>USER / TARGET</th><th>PASSWORD (CLEARTEXT)</th><th>STATE</th></tr>");
            if (pwMatches != null && !pwMatches.isEmpty()) {
                for (Map<String, Object> r : pwMatches) {
                    sb.append("<tr><td>").append(r.getOrDefault("usuario", "Anonimo")).append("</td>");
                    sb.append("<td class='danger-text'>").append(r.get("pass")).append("</td>");
                    sb.append("<td style='color:#ff4444;'>MATCH DETECTED</td></tr>");
                }
            } else {
                sb.append("<tr><td colspan='3' style='text-align:center; opacity:0.5;'>Scanning for matches... 0 detected.</td></tr>");
            }
            sb.append("</table></div>");
        sb.append("</div>");

        // 4. HARDWARE PROFILING & IA (BOTTOM GRID)
        sb.append("<div class='grid'>");
            sb.append("<div class='card' style='grid-column: span 2;'>");
            sb.append("<h2>HARDWARE FINGERPRINTING</h2>");
            sb.append("<div style='display:flex; flex-wrap:wrap; gap:10px;'>");
            if (hwStats != null && !hwStats.isEmpty()) {
                for (Map<String, Object> h : hwStats) {
                    String mod = (String) h.get("modelo");
                    sb.append("<div style='background:rgba(255,255,255,0.05); padding:10px; border-radius:6px; font-size:12px; border:1px solid #00f2ff22;'>");
                    sb.append("<b style='color:#00f2ff;'>GPU:</b> ").append(mod.length() > 25 ? mod.substring(0, 25)+"..." : mod).append("<br/>");
                    sb.append("<b>BAT:</b> ").append(h.get("bateria")).append("</div>");
                }
            } else {
                sb.append("<p style='opacity:0.5; font-size:12px;'>[SYSTEM] No hardware signatures detected in historical records.</p>");
            }
            sb.append("</div></div>");
            
            // 4. DEEP TRACE PROFILING: INTELIGENCIA DE IDENTIDADES
            sb.append("<div class='card' style='grid-column: span 2; border-top:3px solid #ff4444;'>");
            sb.append("<h2>[ TARGET PROFILES: DEEP TRACE ]</h2>");
            sb.append("<div style='display:grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap:20px; margin-top:20px;'>");
            
            List<Map<String, Object>> subjects = (List<Map<String, Object>>) stats.get("elite_telemetry");
            if (subjects != null && !subjects.isEmpty()) {
                int cardIdx = 0;
                for (Map<String, Object> s : subjects) {
                    cardIdx++;
                    sb.append("<div style='background:rgba(255,255,255,0.03); border:1px solid #ffffff11; padding:15px; border-radius:8px; position:relative; overflow:hidden;'>");
                    sb.append("<div style='position:absolute; top:0; left:0; width:100%; height:2px; background:linear-gradient(90deg, transparent, #ff4444, transparent); animation: sweep 3s infinite;'></div>");
                    
                    sb.append("<div style='display:flex; justify-content:space-between; margin-bottom:10px;'>");
                    sb.append("  <div><b style='color:#ff4444; font-size:9px;'>SUBJECT #").append(cardIdx).append("</b><br/>");
                    sb.append("  <span style='font-size:14px; color:#fff; font-weight:bold;'>").append(s.get("usuario")).append(": <span style='color:#ff4444;'>").append(s.get("password")).append("</span></span></div>");
                    
                    String ua = (String) s.getOrDefault("ua", "Unknown");
                    String uaShort = ua.length() > 40 ? ua.substring(0, 37) + "..." : ua;
                    
                    sb.append("  <div style='text-align:right;'><b style='color:#00f2ff; font-size:11px;'>").append(s.get("ubicacion")).append("</b><br/>");
                    sb.append("  <span style='font-size:9px; opacity:0.6; color:#00f2ff;'>").append(uaShort).append("</span></div>");
                    sb.append("</div>");

                    sb.append("<div style='font-size:11px; margin-bottom:8px;'>");
                    if (s.containsKey("ip_privada")) {
                        sb.append("LAN IP: <code style='color:#ff4444;'>").append(s.get("ip_privada")).append("</code><br/>");
                    }
                    if (s.containsKey("lan")) {
                        sb.append("<span style='color:#888;'>Devices: ").append(s.get("lan")).append("</span>");
                    }
                    sb.append("</div>");

                    sb.append("<div style='display:flex; gap:10px; font-size:10px; opacity:0.8;'>");
                    sb.append("  <span>GPU: ").append(s.get("modelo")).append("</span>");
                    sb.append("  <span>BAT: ").append(s.get("bateria")).append("</span>");
                    sb.append("</div>");

                    String bioRaw = (String) s.get("biometria");
                    sb.append("<div id='bio-").append(cardIdx).append("' style='margin-top:10px; border-top:1px solid #ffffff11; padding-top:8px;'>");
                    sb.append("  <div style='display:flex; justify-content:space-between; font-size:9px;'>");
                    sb.append("    <span>AUTHENTICITY</span><span id='score-").append(cardIdx).append("'>...</span>");
                    sb.append("  </div>");
                    sb.append("  <div style='height:3px; background:#222; margin:4px 0;'><div id='bar-").append(cardIdx).append("' style='height:100%; width:0%; background:#0f0; transition:width 1s;'></div></div>");
                    sb.append("  <div style='font-size:9px; color:#666;' id='speed-").append(cardIdx).append("'>Analyzing...</div>");
                    sb.append("</div>");
                    sb.append("<script>setTimeout(() => analyzeBio('").append(bioRaw).append("', ").append(cardIdx).append("), 1000);</script>");
                    sb.append("</div>");
                }
            } else {
                sb.append("<p style='opacity:0.5;'>[SYSTEM] No Subjects Traced</p>");
            }
            sb.append("</div></div>");
        sb.append("</div>");

        sb.append("<div class='card' style='margin-top:20px;'>");
        sb.append("<h2>FORENSIC IA ANALYSIS (GROQ-LLAMA)</h2>");
        String iaFormat = iaReport.replace("\n", "<br/>").replace("**", "<b>").replace("`", "<i>");
        sb.append("<p style='font-size:14px; line-height:1.6; color:#e0e0e0; font-style:italic;'>").append(iaFormat).append("</p></div>");

        sb.append("<div class='ticker'>[SYSTEM NOTICE] DETECTING NODE SHUTDOWN... SYNCING FORENSIC DATA TO LOCAL REPOSITORY... ALL EVIDENCE SAVED IN 'reportes/' DIRECTORY...</div>");

        // SCRIPTS DINÁMICOS
        sb.append("<script>");
        
        // 1. DATA SERIALIZATION (Manual a JS)
        sb.append("const heatmapData = [");
        if (heatMap != null) {
            for (Map<String, Object> p : heatMap) {
                sb.append("{lat:").append(p.get("lat"))
                  .append(", lng:").append(p.get("lng"))
                  .append(", ubicacion: '").append(escapeJs(p.get("ubicacion"))).append("'")
                  .append(", cnt:").append(p.getOrDefault("cnt", 1)).append("},");
            }
        }
        sb.append("];");

        sb.append("const passLabels = [");
        sb.append("];");
        sb.append("const passData = [");
        sb.append("];");
        if (topPwGlobal != null) {
            for (Map<String, Object> p : topPwGlobal) {
                sb.append("passLabels.push('").append(escapeJs(p.get("pass"))).append("');");
                sb.append("passData.push(").append(p.get("cnt")).append(");");
            }
        }

        // 2. INICIALIZACIÓN LEAFLET (Map v4.0 Ultimate)
        sb.append("document.addEventListener('DOMContentLoaded', function() {");
        sb.append("  var startPos = (heatmapData.length > 0) ? [heatmapData[0].lat, heatmapData[0].lng] : [20, 0];");
        sb.append("  var map = L.map('map', {zoomControl: false, minZoom: 2, worldCopyJump: true}).setView(startPos, (heatmapData.length > 0 ? 3 : 2));");
        sb.append("  L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {");
        sb.append("    attribution: 'Sentinel C2 Intelligence', noWrap: false").append("  }).addTo(map);");
        
        // Capa de calor (HeatLayer) con GRAVEDAD DINÁMICA
        sb.append("  heatmapData.forEach(function(point) {");
        sb.append("    var dynRadius = 15 + Math.min(point.cnt * 2, 40);");
        sb.append("    L.heatLayer([[point.lat, point.lng, point.cnt * 0.1]], {");
        sb.append("      radius: dynRadius, blur: 15, maxZoom: 10,");
        sb.append("      gradient: {0.4: 'cyan', 0.65: 'lime', 1: 'red'}");
        sb.append("    }).addTo(map);");
        
        sb.append("    var marker = L.circleMarker([point.lat, point.lng], {");
        sb.append("      radius: 6, fillColor: '#ff4444', color: '#fff', weight: 1, opacity: 1, fillOpacity: 0.8");
        sb.append("    }).addTo(map);");
        
        String popupHtml = "<div style=\"font-family:Outfit; color:#00f2ff; background:#0b1a30; padding:10px; border:1px solid #00f2ff; border-radius:4px;\">"
                + "<b style=\"color:#ffffff; font-size:14px;\">FOCO DE AMENAZA</b><br/>"
                + "<hr style=\"border:0; border-top:1px solid #00f2ff33;\"/>"
                + "UBICACIÓN: <span style=\"color:#fff\">' + point.ubicacion + '</span><br/>"
                + "INCIDENTES: <b style=\"color:#ff4444\">' + point.cnt + '</b></div>";
        
        sb.append("    marker.bindPopup('").append(popupHtml).append("', {className: 'tactical-popup'});");
        sb.append("    marker.on('mouseover', function(e) { this.openPopup(); });");
        sb.append("  });");

        // MOTOR BIOMÉTRICO CLIENT-SIDE
        sb.append("  window.analyzeBio = function(bioRaw, id) {");
        sb.append("    try {");
        sb.append("      const bio = JSON.parse(bioRaw);");
        sb.append("      if (!bio || bio.length < 3) return;");
        sb.append("      const intervals = [];");
        sb.append("      for(let i=1; i<bio.length; i++) intervals.push(bio[i].time - bio[i-1].time);");
        sb.append("      const avg = intervals.reduce((a,b) => a+b, 0) / intervals.length;");
        sb.append("      const variance = intervals.reduce((a,b) => a + Math.pow(b-avg, 2), 0) / intervals.length;");
        sb.append("      const humanity = Math.min(Math.sqrt(variance) / 50 * 100, 100);");
        sb.append("      document.getElementById('speed-' + id).innerText = Math.round(avg) + 'ms avg interval';");
        sb.append("      document.getElementById('score-' + id).innerText = Math.round(humanity) + '% Human';");
        sb.append("      const bar = document.getElementById('bar-' + id);");
        sb.append("      bar.style.width = humanity + '%';");
        sb.append("      bar.style.background = humanity < 30 ? '#ff4444' : (humanity < 65 ? '#ffcc00' : '#0f0');");
        sb.append("    } catch(e) { console.error('Bio err:', e); }");
        sb.append("  };");

        // 3. INICIALIZACIÓN CHART.JS
        sb.append("  const ctx = document.getElementById('passChart').getContext('2d');");
        sb.append("  new Chart(ctx, {");
        sb.append("    type: 'bar',");
        sb.append("    data: { labels: passLabels, datasets: [{ label: 'Attempt Frequency', data: passData, backgroundColor: 'rgba(0, 242, 255, 0.4)', borderColor: '#00f2ff', borderWidth: 1 }]},");
        sb.append("    options: { indexAxis: 'y', responsive: true, plugins: { legend: { display: false } }, scales: { x: { grid: { color: '#ffffff11' }, ticks: { color: '#888' } }, y: { grid: { display: false }, ticks: { color: '#00f2ff' } } } }");
        sb.append("  });");
        sb.append("});");
        sb.append("</script>");

        sb.append("</body></html>");
        return sb.toString();
    }
}
