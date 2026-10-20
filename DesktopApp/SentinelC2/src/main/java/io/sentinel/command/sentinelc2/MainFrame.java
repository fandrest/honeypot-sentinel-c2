
package io.sentinel.command.sentinelc2;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.File;
import java.util.logging.Level;

import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.ImageIcon;
import javax.swing.JScrollPane;
import java.awt.Image;
import java.awt.AlphaComposite;
import java.awt.CardLayout;
import java.util.Map;
import java.io.IOException;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * Orquestador Táctico (MainFrame) del Ecosistema SentinelC2.
 * 
 * Esta clase actúa como el Centro de Mando ("Command & Control") del honeypot,
 * integrando la interfaz gráfica principal desarrollada con Swing y FlatLaf.
 * Centraliza la administración del servidor Apache Tomcat, bases de datos
 * MySQL,
 * monitorización de amenazas en tiempo real a través de Node.js / WebSockets,
 * y el despliegue del motor nativo HoneyPot (Sentinel_Suite).
 *
 * 
 * /**
 * 
 * @class MainFrame
 * @brief GUI y Motor de Orquestación del Sentinel C2.
 * @details Gestiona el ciclo de vida de los servicios backend (Database, Web
 *          Server, Proxy Tunnel) y actúa como el visor central de la actividad
 *          del atacante.
 */
public class MainFrame extends javax.swing.JFrame {
    private int xMouse, yMouse;
    private static final Logger logger = Logger.getLogger(MainFrame.class.getName());

    // 1. Configuración de iconos
    private final FlatSVGIcon sunIcon = new FlatSVGIcon("icons/theme-light.svg", 20, 20);
    private final FlatSVGIcon moonIcon = new FlatSVGIcon("icons/theme-dark.svg", 20, 20);
    private final FlatSVGIcon midIcon = new FlatSVGIcon("icons/theme-mid.svg", 20, 20);
    private final FlatSVGIcon startIcon = new FlatSVGIcon("icons/start-hacker.svg", 48, 48);

    private int currentThemeMode = 2; // 0: Light, 1: Mid Blue, 2: Deep Space

    private int uiScaleLevel = 1; // 0: Pequeño, 1: Normal, 2: Grande
    private boolean isCrtActivate = false;
    private javax.swing.JPanel crtOverlay;
    private javax.swing.Timer crtTimer;

    private String detectedOS = "UNKNOWN";

    private String groqApiKey = (System.getenv("GROQ_API_KEY") != null) ? System.getenv("GROQ_API_KEY").trim() : ""; // Persistent Groq API Key
    private boolean isPythonReady = false;
    private boolean isNodeReady = false;
    private boolean isAdmin = false;

    // Ruta configurada en memoria si falta la variable de entorno
    private String savedCatalinaHome = null;

    private boolean isTomcatRunning = false;
    private java.lang.Process tomcatProcess; // Referencia persistente del servidor
    private java.lang.Process networkMonitorProcess; // Referencia del monitor de red
    private boolean isTransitioningService = false; // Bloqueo de sincronización durante arranques/paradas
    

    private Timer monitorTimer;

    private Timer blinkTimer;
    private boolean isBlinkOn = false;

    private boolean isNgrokReady = false;
    private boolean isNgrokRunning = false;

    
    private ThreatMonitorPanel threatMonitor;
    private GeoLocationPanel geoMap;

    // Botones extraidos a nivel de clase para sincronización de Theme
    private LiveActivityPanel liveActivityTicker;

    // Parche temporal en memoria para evitar el loop de Tanner

    // --- NUEVAS VARIABLES SPRINT 3: PERSISTENCIA Y TOASTS ---
    private boolean isMysqlRunning = false;
    private boolean isDatabaseReady = false;
    private int connectionCounter = 0;

    private List<GhostToast> activeToasts = new ArrayList<>();

    // Labels de UI para actualización de tema
    private javax.swing.JLabel lblTicker;

    public MainFrame() {

        // 1. Leer qué prefiere el usuario
        loadConfig();

        setUndecorated(true);
        try {
            setIconImage(
                    java.awt.Toolkit.getDefaultToolkit().getImage(getClass().getResource("/icons/hacker_logo.png")));
        } catch (Exception e) {
        }
        initComponents();

        // 1.4 Branding & Hacker Graphics
        setupHackerBranding();

        // 1.5. Configurar el fondo dinámico envolviendo el ContentPane
        setupDynamicBackground();

        // Iniciamos la secuencia de carga una vez que los componentes están listos
        startSystemSequence();
        setResizable(true); // Permitir redimensionado libre

        setMinimumSize(new java.awt.Dimension(1100, 800));
        setSize(getMinimumSize()); // Iniciar en tamaño mínimo
        setLocationRelativeTo(null); // Centrar en pantalla

        initButtons();
        initCrtOverlay();

        // Timer de parpadeo (LEDs ciberneticos)
        blinkTimer = new Timer(500, e -> {
            isBlinkOn = !isBlinkOn;
            Color accent = getThemeAccentColor();
            Color c = isBlinkOn ? accent : accent.darker().darker();

            if (isTomcatRunning) {
                lblStatusService.setForeground(c);
                lblStatusPort.setForeground(c);
            }
            if (isMysqlRunning) {
                if (lblStatusMysql != null)
                    lblStatusMysql.setForeground(c);
            }
            if (isNgrokRunning) {
                if (lblStatusNgrok != null)
                    lblStatusNgrok.setForeground(c);
            } else {
                if (lblStatusNgrok != null)
                    lblStatusNgrok.setForeground(new Color(100, 100, 100));
            }
        });
        blinkTimer.start();

        // Iniciamos el monitor de red para que sincronice Tomcat externo
        iniciarMonitoreoRed();

        // 2. Aplicar lo cargado de forma consistente
        switchTheme(currentThemeMode);
        float currentScale = (uiScaleLevel == 0) ? 0.75f : (uiScaleLevel == 1) ? 1.0f : 1.5f;
        updateButtonSizes(currentScale);

        String scaleLabel = (uiScaleLevel == 0) ? "SMALL" : (uiScaleLevel == 1) ? "NORMAL" : "HUGE";
        btnUiScale.setText("UI : " + scaleLabel);

        // Si el CRT estaba activo, lo aplicamos
        if (isCrtActivate) {
            setGlassPane(crtOverlay);
            getGlassPane().setVisible(true);
            crtTimer.start();
            btnCtrFilter.setText("CRT: ON");
        } else {
            btnCtrFilter.setText("CRT: OFF");
        }

        // Habilitar el redimensionado para ventana undecorated
        new WindowResizer(this);

        setupDynamicOperacionesLayout();
        setupChallengePanel();
        updateFooterNav("EXIT", e -> startExitAnimation());
    }

    private void navigateTo(String panelName) {
        System.out.println("[NAV] ===> Cambiando a: " + panelName);
        CardLayout cl = (CardLayout) contentArea.getLayout();
        cl.show(contentArea, panelName);
        
        contentArea.revalidate();
        contentArea.repaint();

        // Forzar transparencia absoluta en la navegación para ver el grid
        contentArea.setOpaque(false);
        if (getContentPane() instanceof javax.swing.JPanel) {
            ((javax.swing.JPanel) getContentPane()).setOpaque(false);
        }

        // Gestión del botón Footer
        if ("card2".equals(panelName)) {
            updateFooterNav("EXIT", e -> startExitAnimation());
        } else {
            updateFooterNav("BACK", e -> navigateTo("card2"));
        }

        // Configuraciones específicas
        if ("monitor".equals(panelName)) {
            panelMonitor.revalidate();
            panelMonitor.repaint();
        } else if ("ops".equals(panelName)) {
            setupDynamicOperacionesLayout(); 
            panelOperaciones.revalidate();
            panelOperaciones.repaint();
        } else if ("console".equals(panelName)) {
            // Asegurar vínculo y refresco profundo
            monitorScroll2.setViewportView(monitorConsole2);
            monitorScroll2.revalidate();
            monitorScroll2.repaint();
        }
    }

    /**
     * Sincroniza los logs en ambas consolas (Monitor y Mini) usando color directo
     */
    private void log(String msg, Color color) {
        if (monitorConsole != null) monitorConsole.log(msg, color);
        if (monitorConsole2 != null) monitorConsole2.log(msg, color);
    }

    /**
     * Sincroniza los logs en ambas consolas (Monitor y Mini) usando nivel (INFO, ERROR, etc)
     */
    private void log(String msg, String level) {
        if (monitorConsole != null) monitorConsole.log(msg, level);
        if (monitorConsole2 != null) monitorConsole2.log(msg, level);
    }

    private void iniciarMonitoreoRed() {
        monitorTimer = new Timer(3000, e -> {
            if (isTransitioningService)
                return; // Ignorar sincronización durante cambios manuales

            // 1. NIVEL CRÍTICO: MYSQL
            isMysqlRunning = isPortInUse(3306);
            isDatabaseReady = checkDatabases();

            updateMysqlUI();

            // 2. NIVEL ESTRUCTURAL: TOMCAT & NGROK (Dependen de MySQL)
            boolean infraAllowed = isMysqlRunning && isDatabaseReady;

            if (btnLanzarTomcat != null)
                btnLanzarTomcat.setEnabled(infraAllowed);
            if (btnNgrok != null)
                btnNgrok.setEnabled(infraAllowed);

            // Verificar Tomcat
            isTomcatRunning = isPortInUse(8080);
            java.awt.Color accent = getThemeAccentColor();
            String hexAccent = toHexString(accent);
            String hexBg = toHexString(
                    UIManager.getColor("Button.background") != null ? UIManager.getColor("Button.background")
                            : new java.awt.Color(10, 25, 47));

            if (isTomcatRunning) {
                lblStatusService.setText("● TOMCAT: ACTIVE");
                lblStatusPort.setText("● PORT: 8080");
                lblStatusService.setForeground(accent);
                lblStatusPort.setForeground(accent);
                btnLanzarTomcat.setText("STOP TOMCAT");
                btnLanzarTomcat.putClientProperty("FlatLaf.style",
                        "arc: 0; borderWidth: 1; background: " + hexBg + "; borderColor: " + hexAccent
                                + "; foreground: " + hexAccent + "; hoverForeground: #ffffff;");
            } else {
                lblStatusService.setText("○ TOMCAT: INACTIVE");
                lblStatusPort.setText("○ PORT: 8080");
                lblStatusService.setForeground(new Color(150, 0, 0));
                lblStatusPort.setForeground(new Color(150, 0, 0));
                btnLanzarTomcat.setText("START TOMCAT");
                btnLanzarTomcat.putClientProperty("FlatLaf.style",
                        "arc: 0; borderWidth: 1; background: " + hexBg
                                + "; borderColor: #FF4444; foreground: #FF4444; hoverForeground: #ffffff;");
            }

            // Verificar NGROK
            isNgrokRunning = isPortInUse(4040);
            if (lblStatusNgrok != null) {
                lblStatusNgrok.setText(isNgrokRunning ? "● NGROK: ACTIVE" : "○ NGROK: INACTIVE");
                lblStatusNgrok.setForeground(isNgrokRunning ? accent : new Color(150, 0, 0));
                if (btnNgrok != null) {
                    btnNgrok.setText(isNgrokRunning ? "STOP NGROK" : "LANZAR NGROK");
                    btnNgrok.putClientProperty("FlatLaf.style",
                            "arc: 0; borderWidth: 1; background: " + hexBg + "; borderColor: "
                                    + (isNgrokRunning ? hexAccent : "#FF4444") + "; foreground: "
                                    + (isNgrokRunning ? hexAccent : "#FF4444") + "; hoverForeground: #ffffff;");
                }
            }

            if (btnTanner != null)
                btnTanner.setEnabled(infraAllowed);
            if (btnBots != null)
                btnBots.setEnabled(infraAllowed);
            if (btnShowQR != null)
                btnShowQR.setEnabled(infraAllowed);
        });
        monitorTimer.start();
    }

    private boolean checkDatabases() {
        if (!isMysqlRunning)
            return false;
        try (Connection conn = DriverManager.getConnection(DBConfig.getInstance().getServerUrl(),
                DBConfig.getInstance().getUser(), DBConfig.getInstance().getPassword())) {
            java.sql.ResultSet rs = conn.getMetaData().getCatalogs();
            boolean hasHoneypotDb = false;
            while (rs.next()) {
                String db = rs.getString(1);
                if (db.equalsIgnoreCase("honeypot_db"))
                    hasHoneypotDb = true;
            }
            return hasHoneypotDb;
        } catch (SQLException e) {
            return false;
        }
    }

    private void updateMysqlUI() {
        if (lblStatusMysql == null)
            return;

        java.awt.Color accent = getThemeAccentColor();
        String hexAccent = toHexString(accent);
        String hexBg = toHexString(
                UIManager.getColor("Button.background") != null ? UIManager.getColor("Button.background")
                        : new java.awt.Color(10, 25, 47));

        if (isMysqlRunning) {
            lblStatusMysql.setText("● MYSQL: ACTIVE");
            lblStatusMysql.setForeground(accent);
            lblStatusDb.setText(isDatabaseReady ? "● DB: SYNCED" : "○ DB: MISSING");
            lblStatusDb.setForeground(isDatabaseReady ? accent : Color.YELLOW);
            btnLanzarMysql.setText("STOP MYSQL");
            btnLanzarMysql.putClientProperty("FlatLaf.style",
                    "arc: 0; borderWidth: 1; background: " + hexBg + "; borderColor: " + hexAccent + "; foreground: "
                            + hexAccent + "; hoverForeground: #ffffff;");
        } else {
            lblStatusMysql.setText("○ MYSQL: OFFLINE");
            lblStatusMysql.setForeground(Color.RED);
            lblStatusDb.setText("○ DB: DISCONNECTED");
            lblStatusDb.setForeground(Color.GRAY);
            btnLanzarMysql.setText("START MYSQL");
            btnLanzarMysql.putClientProperty("FlatLaf.style",
                    "arc: 0; borderWidth: 1; background: " + hexBg
                            + "; borderColor: #FF4444; foreground: #FF4444; hoverForeground: #ffffff;");
        }
    }

    private boolean checkDependency(String command) {
        // 1. Buscar en PATH del sistema (instalación estándar)
        try {
            String[] cmd = detectedOS.toLowerCase().contains("win")
                    ? new String[] { "cmd", "/c", "where", command }
                    : new String[] { "which", command };
            Process p = Runtime.getRuntime().exec(cmd);
            if (p.waitFor() == 0)
                return true;
        } catch (Exception ignored) {
        }

        // 2. Fallback: buscar en ubicaciones conocidas del instalador de Sentinel
        return findToolPath(command) != null;
    }

    /**
     * Devuelve la ruta absoluta del ejecutable, buscando primero en PATH
     * y luego en las carpetas donde nuestro instalador lo extrae.
     * Retorna null si no lo encuentra en ningún sitio.
     */
    private String findToolPath(String command) {
        // Intentar obtener la ruta desde PATH
        try {
            String[] cmd = detectedOS.toLowerCase().contains("win")
                    ? new String[] { "cmd", "/c", "where", command }
                    : new String[] { "which", command };
            Process p = Runtime.getRuntime().exec(cmd);
            if (p.waitFor() == 0) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()));
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty())
                    return line.trim();
            }
        } catch (Exception ignored) {
        }

        // Candidatos en disco donde nuestro instalador suele dejar el exe
        boolean isWin = detectedOS.toLowerCase().contains("win");
        String ext = isWin ? ".exe" : "";
        String down = System.getProperty("user.home") + File.separator + "Downloads";

        java.util.List<String> candidates = new java.util.ArrayList<>();
        candidates.add(down + File.separator + command + ext);
        candidates.add(down + File.separator + command + "-installer" + File.separator + command + ext);
        candidates.add(System.getProperty("user.dir") + File.separator + command + ext);

        // Rutas de instalación estándar conocidas (no siempre en PATH)
        if ("node".equals(command)) {
            if (isWin) {
                candidates.add("C:\\Program Files\\nodejs\\node.exe");
                candidates.add("C:\\Program Files (x86)\\nodejs\\node.exe");
            } else {
                candidates.add("/usr/local/bin/node");
                candidates.add("/usr/bin/node");
            }
        }

        for (String path : candidates) {
            if (new File(path).exists())
                return path;
        }
        return null;
    }

    private java.awt.Color getThemeAccentColor() {
        switch (currentThemeMode) {
            case 0:
                return new java.awt.Color(0, 82, 204); // Light Mode: Azul Intenso
            case 1:
                return new java.awt.Color(0, 102, 204); // Mid Blue: Azul Brillante
            default:
                return new java.awt.Color(0, 242, 255); // Deep Space: Neon Cyan
        }
    }

    private void checkPython() {
        // 1. Verificar si el binario de Python existe
        boolean pythonExists = checkDependency("python") || checkDependency("python3");

        if (pythonExists) {
            // 2. Verificar librerías críticas para los módulos de Sentinel
            boolean hasParamiko = checkLibrary("paramiko");
            boolean hasRequests = checkLibrary("requests");

            // Solo marcamos Python como listo si tiene las librerías necesarias
            isPythonReady = hasParamiko && hasRequests;

            if (!isPythonReady) {
                logger.warning("Python detectado pero faltan librerías críticas (Paramiko/Requests).");
            }
        } else {
            isPythonReady = false;
        }
    }

    /**
     * Verifica si una librería específica de Python está instalada.
     */
    private boolean checkLibrary(String library) {
        try {
            // Intentamos importar la librería en un proceso silencioso
            String pythonCmd = checkDependency("python") ? "python" : "python3";
            ProcessBuilder pb = new ProcessBuilder(pythonCmd, "-c", "import " + library);
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void checkAdminPrivileges() {
        try {
            if (detectedOS.equals("WINDOWS")) {
                // En Windows, 'net session' falla si no eres admin (más fiable que 'whoami')
                Process process = Runtime.getRuntime().exec("net session");
                isAdmin = (process.waitFor() == 0);
            } else {
                // En Linux/Mac, el ID de usuario 0 es root
                Process process = Runtime.getRuntime().exec("id -u");
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()));
                String uid = reader.readLine();
                isAdmin = "0".equals(uid);
            }
        } catch (Exception e) {
            isAdmin = false;
        }
    }

    private void requestAdminElevation() {
        if (isAdmin)
            return;

        try {
            if (detectedOS.equals("WINDOWS")) {
                // Obtener ruta del proceso actual
                java.net.URL location = MainFrame.class.getProtectionDomain().getCodeSource().getLocation();
                File currentFile = new File(location.toURI());
                String absolutePath = currentFile.getAbsolutePath();

                // MODO DESARROLLADOR: Si no es un archivo (es una carpeta de clases de IDE)
                if (currentFile.isDirectory() || !absolutePath.toLowerCase().endsWith(".jar")
                        && !absolutePath.toLowerCase().endsWith(".exe")) {
                    JOptionPane.showMessageDialog(this,
                            "⚠️ MODO DESARROLLADOR DETECTADO\n\n" +
                                    "No se puede relanzar el entorno de clases del IDE automáticamente.\n" +
                                    "Para que Sentinel pueda gestionar servicios de sistema, por favor:\n" +
                                    "1. Cierra tu IDE (NetBeans/IntelliJ/VSCode).\n" +
                                    "2. Ejecuta el IDE como ADMINISTRADOR.\n" +
                                    "3. Vuelve a ejecutar el proyecto.",
                            "ELEVACIÓN MANUAL REQUERIDA", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                log("[SENTINEL] Intentando autoelevación: " + currentFile.getName(), Color.YELLOW);

                String cmd;
                if (absolutePath.endsWith(".jar")) {
                    cmd = "powershell -Command \"Start-Process javaw -ArgumentList '-jar \\\"" + absolutePath
                            + "\\\"' -Verb RunAs\"";
                } else {
                    cmd = "powershell -Command \"Start-Process -FilePath \\\"" + absolutePath + "\\\" -Verb RunAs\"";
                }

                Runtime.getRuntime().exec(cmd);
                System.exit(0);
            } else {
                String sudoCmd = "sudo java -jar "
                        + MainFrame.class.getProtectionDomain().getCodeSource().getLocation().getFile();
                JOptionPane.showMessageDialog(this,
                        "⚠️ ELEVACIÓN REQUERIDA (RTFM)\n\n" +
                                "Para gestionar servicios en " + detectedOS
                                + ", Sentinel requiere privilegios de ROOT.\n" +
                                "Por favor, reinicia la aplicación desde la terminal usando:\n" + sudoCmd,
                        "SISTEMA PROTEGIDO", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception e) {
            log("[ERROR] Fallo al elevar privilegios: " + e.getMessage(), Color.RED);
            JOptionPane.showMessageDialog(this,
                    "No se pudo realizar la autoelevación.\nEjecuta el programa manualmente como Administrador.",
                    "Error de Sistema", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void promptInstallation(String os, String tool) {
        String urlDescarga = "";
        String nombreArchivo = "";

        if ("PYTHON".equals(tool)) {
            if (os.toLowerCase().contains("win")) {
                urlDescarga = "https://www.python.org/ftp/python/3.11.0/python-3.11.0-amd64.exe";
                nombreArchivo = "python-installer.exe";
            } else {
                urlDescarga = "https://www.python.org/ftp/python/3.11.0/Python-3.11.0.tgz";
                nombreArchivo = "python-installer.tgz";
            }
        } else if ("TANNER".equals(tool)) {
            urlDescarga = "https://github.com/mushorg/tanner/archive/refs/heads/master.zip";
            nombreArchivo = "tanner-master.zip";
        } else if ("NGROK".equals(tool)) {
            if (os.toLowerCase().contains("win")) {
                // Caso especial Windows: instalar vía winget (Microsoft Store), sin ZIP
                instalarNgrokConWinget();
                return;
            } else if (os.toLowerCase().contains("mac")) {
                urlDescarga = "https://bin.equinox.io/c/b342Pmq6Ez7/ngrok-v3-stable-darwin-amd64.zip";
                nombreArchivo = "ngrok-installer.zip";
            } else {
                urlDescarga = "https://bin.equinox.io/c/b342Pmq6Ez7/ngrok-v3-stable-linux-amd64.tgz";
                nombreArchivo = "ngrok-installer.tgz";
            }
        } else if ("NODE".equals(tool)) {
            if (os.toLowerCase().contains("win")) {
                urlDescarga = "https://nodejs.org/dist/v20.11.1/node-v20.11.1-x64.msi";
                nombreArchivo = "node-v20-x64.msi";
            } else {
                urlDescarga = "https://nodejs.org/dist/v20.11.1/node-v20.11.1-linux-x64.tar.xz";
                nombreArchivo = "node-v20-linux.tar.xz";
            }
        }

        String rutaDestino = System.getProperty("user.home") + File.separator + "Downloads" + File.separator
                + nombreArchivo;
        log("Iniciando descarga de requisitos para " + tool + ": " + nombreArchivo, "INFO");
        DescargarProceso downloader = new DescargarProceso();
        if (downloader.hayInternet()) {
            downloader.iniciarDescarga(this, urlDescarga, rutaDestino);
        } else {
            log("Error: No se pudo establecer conexión para la descarga.", "ERROR");
            JOptionPane.showMessageDialog(this, "Sentinel requiere conexión a internet para descargar dependencias.",
                    "Error de Red", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showGroqOnboarding() {
        javax.swing.JDialog dialog = new javax.swing.JDialog(this, "SENTINEL - CLOUD AI CONFIG", true);
        dialog.setSize(450, 230);
        dialog.setUndecorated(true);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(new Color(10, 25, 47));
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(getThemeAccentColor(), 2));
        dialog.getRootPane().putClientProperty("FlatLaf.style", "arc: 12");

        JPanel main = new JPanel(new java.awt.GridLayout(0, 1, 10, 10));
        main.setOpaque(false);
        main.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        javax.swing.JLabel lblT = new javax.swing.JLabel(
                "<html><b>NÚCLEO IA NO VINCULADO</b><br><font size='3' color='#888888'>Sentinel requiere una Groq API Key para el análisis táctico.</font></html>");
        lblT.setForeground(getThemeAccentColor());
        main.add(lblT);

        javax.swing.JTextField txtKey = new javax.swing.JTextField();
        txtKey.putClientProperty("FlatLaf.placeholderText", "Pega aquí tu API Key (gsk_...)");
        txtKey.setBackground(new Color(7, 18, 34));
        txtKey.setForeground(Color.WHITE);
        main.add(txtKey);

        JPanel pnlBtns = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        pnlBtns.setOpaque(false);

        javax.swing.JButton btnGet = new javax.swing.JButton("OBTENER LLAVE");
        btnGet.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: #00000000; borderColor: #FFD700; foreground: #FFD700; hoverForeground: #ffffff;");
        btnGet.addActionListener(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://console.groq.com/keys"));
            } catch (Exception ex) {
            }
        });

        javax.swing.JButton btnSave = new javax.swing.JButton("VINCULAR NÚCLEO");
        btnSave.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: #00000000; borderColor: #00F2FF; foreground: #00F2FF; hoverForeground: #ffffff;");
        btnSave.addActionListener(e -> {
            String k = txtKey.getText().trim();
            if (!k.isEmpty()) {
                groqApiKey = k;
                saveConfig();
                dialog.dispose();
            } else {
                showMapToast("LLAVE INVÁLIDA", "ALTA");
            }
        });

        pnlBtns.add(btnGet);
        pnlBtns.add(btnSave);

        dialog.add(main, BorderLayout.CENTER);
        dialog.add(pnlBtns, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showInstallerDialog() {
        javax.swing.JDialog installer = new javax.swing.JDialog(this, "SENTINEL C2 - DEPENDENCY CHECK", true);
        installer.setUndecorated(true);
        installer.getRootPane().putClientProperty("FlatLaf.style", "arc: 15");
        installer.getContentPane().setBackground(new Color(10, 25, 47));
        installer.setLayout(new java.awt.BorderLayout());
        installer.getRootPane().setBorder(javax.swing.BorderFactory.createLineBorder(new Color(0, 242, 255), 2));

        JPanel pnlCenter = new JPanel(new java.awt.GridLayout(0, 1, 10, 10));
        pnlCenter.setOpaque(false);
        pnlCenter.setBorder(javax.swing.BorderFactory.createEmptyBorder(20, 20, 20, 20));

        javax.swing.JLabel lblTitle = new javax.swing.JLabel(
                "<html><b>INICIALIZACIÓN DE ENTORNO</b><br><font size='3' color='#888888'>Selecciona los módulos a instalar</font></html>");
        lblTitle.setForeground(new Color(0, 242, 255));
        pnlCenter.add(lblTitle);

        javax.swing.JCheckBox chkPython = new javax.swing.JCheckBox(
                "Python Engine & Libraries " + (isPythonReady ? "[Instalado]" : "[Falta/Incompleto]"));
        chkPython.setSelected(!isPythonReady);
        chkPython.setForeground(isPythonReady ? Color.GREEN : Color.RED);

        chkPython.setOpaque(false);
        pnlCenter.add(chkPython);

        boolean hasNgrok = checkDependency("ngrok");
        javax.swing.JCheckBox chkNgrok = new javax.swing.JCheckBox(
                "Ngrok Client " + (hasNgrok ? "[Instalado]" : "[Falta]"));
        chkNgrok.setSelected(!hasNgrok);
        chkNgrok.setForeground(hasNgrok ? Color.GREEN : Color.RED);
        chkNgrok.setOpaque(false);
        pnlCenter.add(chkNgrok);

        javax.swing.JCheckBox chkNode = new javax.swing.JCheckBox(
                "Node.js Runtime " + (isNodeReady ? "[Instalado]" : "[Falta]"));
        chkNode.setSelected(!isNodeReady);
        chkNode.setForeground(isNodeReady ? Color.GREEN : Color.RED);
        chkNode.setOpaque(false);
        pnlCenter.add(chkNode);

        installer.add(pnlCenter, java.awt.BorderLayout.CENTER);

        JPanel pnlSouth = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        pnlSouth.setOpaque(false);
        pnlSouth.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 20, 20, 20));

        javax.swing.JButton btnProc = new javax.swing.JButton("INSTALAR / CONTINUAR");
        btnProc.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: #00000000; borderColor: #00F2FF; foreground: #00F2FF; hoverForeground: #ffffff;");
        btnProc.setBackground(new Color(10, 25, 47));
        btnProc.setForeground(new Color(0, 242, 255));
        btnProc.addActionListener(e -> {
            installer.dispose();
            if (chkPython.isSelected())
                promptInstallation(detectedOS, "PYTHON");
            if (chkNgrok.isSelected())
                promptInstallation(detectedOS, "NGROK");
            if (chkNode.isSelected())
                promptInstallation(detectedOS, "NODE");

        });

        javax.swing.JButton btnSkip = new javax.swing.JButton("OMITIR (SOLO READ-ONLY)");
        btnSkip.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: #00000000; borderColor: #555555; foreground: #555555; hoverForeground: #ffffff;");
        btnSkip.addActionListener(e -> installer.dispose());

        pnlSouth.add(btnProc);
        pnlSouth.add(btnSkip);
        installer.add(pnlSouth, java.awt.BorderLayout.SOUTH);

        installer.setSize(450, 250);
        installer.setLocationRelativeTo(this);
        installer.setVisible(true);
    }

    private int targetProgress = 0;
    private float currentProgress = 0;
    private javax.swing.Timer smoothTimer;

    private void startSystemSequence() {
        // 0. Reiniciar progreso
        targetProgress = 0;
        currentProgress = 0;
        prgBar.setValue(0);

        // 1. Mostrar la carta de carga
        java.awt.CardLayout cl = (java.awt.CardLayout) contentArea.getLayout();
        cl.show(contentArea, "loading");

        // 2. Timer de actualización fluida (Interpolación)
        if (smoothTimer != null)
            smoothTimer.stop();
        smoothTimer = new Timer(30, e -> {
            if (currentProgress < targetProgress) {
                currentProgress += 0.8f; // Velocidad de subida fluida
                if (currentProgress > targetProgress)
                    currentProgress = targetProgress;
                prgBar.setValue((int) currentProgress);
            }
        });
        smoothTimer.start();

        // 3. Timer principal de secuencia (1500ms por paso)
        Timer mainTimer = new Timer(1500, null);
        final int[] step = { 0 };

        mainTimer.addActionListener(e -> {
            switch (step[0]) {
                case 0:
                    lblLoadingStatus.setText("Detecting HOST OS...");
                    detectOperatingSystem();
                    targetProgress = 20;
                    break;

                case 1:
                    lblLoadingStatus.setText("SYSTEM DETECTED: " + detectedOS);
                    lblOsIcon.setIcon(new FlatSVGIcon("icons/" + detectedOS.toLowerCase() + ".svg", 60, 60));
                    targetProgress = 40;
                    break;

                case 2:
                    lblLoadingStatus.setText("INITIALIZING CLOUD AI ENGINE...");
                    targetProgress = 60;
                    break;

                case 3:
                    lblLoadingStatus.setText("CHECKING NGROK & NODE.JS...");
                    isNgrokReady = checkDependency("ngrok");
                    isNodeReady = checkDependency("node");
                    targetProgress = 70;
                    break;

                case 4:
                    lblLoadingStatus.setText("CHECKING PYTHON ENVIRONMENT...");
                    checkPython();
                    if (!isPythonReady) {
                        lblLoadingStatus
                                .setText("<html><font color='#FF4444'>WARNING: PYTHON ENGINE MISSING</font></html>");
                    }
                    targetProgress = 80;
                    break;

                case 5:
                    lblLoadingStatus.setText("VERIFYING SECURITY PRIVILEGES...");
                    checkAdminPrivileges();
                    if (!isAdmin) {
                        lblLoadingStatus.setText("<html><font color='#FFAA00'>RUNNING IN NON-ADMIN MODE</font></html>");
                    }
                    targetProgress = 95;
                    break;

                case 6:
                    lblLoadingStatus.setText("SYSTEM CORE READY");
                    targetProgress = 100;
                    break;

                case 7:
                    mainTimer.stop();
                    smoothTimer.stop();
                    if (!isNgrokReady || !isPythonReady || !isNodeReady) {
                        showInstallerDialog();
                    }
                    cl.show(contentArea, "card2");

                    break;
            }
            step[0]++;
        });
        mainTimer.start();
    }

    private void setupDynamicOperacionesLayout() {
        panelOperaciones.removeAll();
        panelOperaciones.setLayout(new java.awt.BorderLayout(10, 10));

        // --- JUNIT PANEL 1 REORGANIZATION ---
        jPanel1.removeAll();
        jPanel1.setLayout(new javax.swing.BoxLayout(jPanel1, javax.swing.BoxLayout.Y_AXIS));

        // Etiquetas en grupo arriba
        if (lblStatusMysql != null) {
            lblStatusMysql.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            jPanel1.add(lblStatusMysql);
        }
        if (lblStatusDb != null) {
            lblStatusDb.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            jPanel1.add(lblStatusDb);
        }
        jPanel1.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, 5)));
        if (lblStatusService != null) {
            lblStatusService.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            jPanel1.add(lblStatusService);
        }
        if (lblStatusPort != null) {
            lblStatusPort.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            jPanel1.add(lblStatusPort);
        }
        jPanel1.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, 5)));
        if (lblStatusNgrok != null) {
            lblStatusNgrok.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            jPanel1.add(lblStatusNgrok);
        }

        jPanel1.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, 15)));

        // Botones homogeneizados
        java.awt.Dimension btnSize = new java.awt.Dimension(160, 32);

        // ====================== NAVEGACIÓN LIMPIA ======================
        // Solo limpiamos y reconfiguramos botones que realmente necesitamos controlar
        // dinámicamente
        // NO tocamos btnStart, btnGraphics, btnLogs porque ya tienen listeners
        // correctos desde initComponents()

        // Re-asignamos listeners solo si es necesario o para asegurar persistencia

        // Re-asignamos listeners a los botones importantes (por seguridad)
        btnStart.addActionListener(e -> navigateTo("ops"));
        btnGraphics.addActionListener(e -> navigateTo("settings"));
        btnLogs.addActionListener(e -> navigateTo("console"));
        btnMonitorRed.addActionListener(e -> navigateTo("monitor"));

        if (btnLanzarMysql != null) {
            btnLanzarMysql.setMaximumSize(btnSize);
            btnLanzarMysql.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            jPanel1.add(btnLanzarMysql);
            jPanel1.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, 10)));
        }
        if (btnLanzarTomcat != null) {
            btnLanzarTomcat.setMaximumSize(btnSize);
            btnLanzarTomcat.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            jPanel1.add(btnLanzarTomcat);
            jPanel1.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, 10)));
        }
        System.out.println("[DEBUG] Iniciando setupDynamicOperacionesLayout...");
        System.out.println("[DEBUG] panelOperaciones size: " + panelOperaciones.getSize());

        // Aseguramos que panelOperaciones tenga BorderLayout (por si acaso)
        panelOperaciones.setLayout(new java.awt.BorderLayout());

        // Re-vinculamos los contenedores principales al panel base (por seguridad)
        panelOperaciones.add(panelDashboardCenter, java.awt.BorderLayout.CENTER);
        panelOperaciones.add(panelRightSidebar, java.awt.BorderLayout.EAST);

        // Ajuste de anchos para consistencia
        panelRightSidebar.setPreferredSize(new java.awt.Dimension(220, panelOperaciones.getHeight()));

        // --- 1. LIMPIEZA DE PANELES (Nivel Senior) ---
        // Aseguramos transparencia absoluta para ver el grid de fondo
        panelOperaciones.setOpaque(false);
        panelOperaciones.setBackground(null);
        
        panelDashboardCenter.setOpaque(false);
        panelDashboardCenter.setBackground(null);
        
        panelRightSidebar.setOpaque(false);
        panelRightSidebar.setBackground(null);
        
        jPanel1.setOpaque(false);
        jPanel1.setBackground(null);
        panelGraphContainer.setOpaque(false);
        panelTimelineControls.setOpaque(false);
        panelActivityFeed.setOpaque(false);
        
        // --- 2. RECURSIÓN DE TRANSPARENCIA (Solución Definitiva) ---
        // Forzamos a que TODOS los subpaneles sean transparentes
        applyDeepTransparency(panelOperaciones);

        panelGraphContainer.removeAll();
        panelTimelineControls.removeAll();
        panelActivityFeed.removeAll();

        // --- 3. INICIALIZACIÓN DE COMPONENTES ---
        if (threatMonitor == null) {
            System.out.println("[DEBUG] Creando nuevo ThreatMonitorPanel");
            threatMonitor = new ThreatMonitorPanel();
        }
        if (liveActivityTicker == null) {
            liveActivityTicker = new LiveActivityPanel();
        }

        // --- 4. INTEGRACIÓN EN PLACEHOLDERS ---

        // Gráfico al Centro
        panelGraphContainer.add(threatMonitor, java.awt.BorderLayout.CENTER);

        // Botones de Rango Temporal (Extraídos del constructor del panel para mejor control)
        String[] rangos = { "15 MIN", "1 HORA", "1 DÍA", "1 SEM", "1 MES" };
        java.awt.Color accent = getThemeAccentColor();
        java.awt.Color bg = UIManager.getColor("Panel.background");
        if (bg == null) bg = new java.awt.Color(10, 25, 47);

        String hexAccent = toHexString(accent);
        String hexBg = toHexString(bg);

        for (String r : rangos) {
            javax.swing.JButton btnPrec = new javax.swing.JButton(r);
            btnPrec.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 10));
            btnPrec.setFocusPainted(false);
            btnPrec.putClientProperty("FlatLaf.style",
                    "arc: 0; borderWidth: 1; background: " + hexBg + "; borderColor: " + hexAccent + "; foreground: "
                            + hexAccent + "; hoverForeground: #ffffff;");
            btnPrec.addActionListener(e -> {
                threatMonitor.setCurrentRangeState(r);
                threatMonitor.fetchDataFromMySQL();
            });
            panelTimelineControls.add(btnPrec);
        }

        // Live Activity Feed al Sidebar (Debajo de los botones)
        lblTicker = new javax.swing.JLabel(" LIVE ACTIVITY FEED");
        lblTicker.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 10));
        lblTicker.setForeground(accent.darker());
        lblTicker.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        panelActivityFeed.add(lblTicker, java.awt.BorderLayout.NORTH);

        javax.swing.JScrollPane tickerScroll = new javax.swing.JScrollPane(liveActivityTicker);
        tickerScroll.setOpaque(false);
        tickerScroll.getViewport().setOpaque(false);
        tickerScroll.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 242, 255, 40), 1));
        tickerScroll.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        panelActivityFeed.add(tickerScroll, java.awt.BorderLayout.CENTER);

        // --- 4. RECONSTRUCCIÓN DINÁMICA DEL SIDEBAR (Para evitar conflictos del Designer) ---
        jPanel1.removeAll();
        jPanel1.setLayout(new javax.swing.BoxLayout(jPanel1, javax.swing.BoxLayout.Y_AXIS));

        // Estilos base
        java.awt.Dimension gap5 = new java.awt.Dimension(0, 5);
        java.awt.Dimension gap10 = new java.awt.Dimension(0, 10);
        java.awt.Dimension gap15 = new java.awt.Dimension(0, 15);
        java.awt.Dimension sideBtnSize = new java.awt.Dimension(200, 30);

        // Función auxiliar para añadir componentes centrados
        java.util.function.Consumer<java.awt.Component> addCentered = (c) -> {
            if (c != null) {
                if (c instanceof javax.swing.JComponent) {
                    ((javax.swing.JComponent) c).setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
                }
                jPanel1.add(c);
            }
        };

        // 1. BLOQUE DE ESTADOS (Todos juntos arriba)
        addCentered.accept(lblStatusMysql);
        addCentered.accept(lblStatusDb);
        addCentered.accept(lblStatusService);
        addCentered.accept(lblStatusPort);
        addCentered.accept(lblStatusNgrok);

        addCentered.accept(javax.swing.Box.createRigidArea(gap15));

        // 2. BLOQUE DE ACCIONES (Todos los botones juntos)
        addCentered.accept(btnLanzarMysql);
        addCentered.accept(javax.swing.Box.createRigidArea(gap5));
        addCentered.accept(btnLanzarTomcat);
        addCentered.accept(javax.swing.Box.createRigidArea(gap5));
        addCentered.accept(btnNgrok);
        addCentered.accept(javax.swing.Box.createRigidArea(gap5));
        addCentered.accept(btnShowQR);
        addCentered.accept(javax.swing.Box.createRigidArea(gap5));
        addCentered.accept(btnTanner);
        addCentered.accept(javax.swing.Box.createRigidArea(gap5));
        addCentered.accept(btnBots);
        addCentered.accept(javax.swing.Box.createRigidArea(gap5));
        addCentered.accept(btnInforme);
        addCentered.accept(javax.swing.Box.createRigidArea(gap5));
        addCentered.accept(btnMonitorRed);

        // Aplicar estilos y tamaños a todos los botones
        javax.swing.JButton[] sideButtons = {
                btnLanzarMysql, btnLanzarTomcat, btnNgrok,
                btnShowQR, btnTanner, btnBots, btnInforme, btnMonitorRed
        };

        for (javax.swing.JButton b : sideButtons) {
            if (b != null) {
                b.setMaximumSize(sideBtnSize);
                b.setPreferredSize(sideBtnSize);
                b.setMargin(new java.awt.Insets(2, 2, 2, 2));
            }
        }

        jPanel1.revalidate();
        jPanel1.repaint();

        // --- 5. ESTILOS DE FLATLAF ---
        String styleBase = "arc: 0; borderWidth: 1; background: " + hexBg + "; borderColor: " + hexAccent
                + "; foreground: " + hexAccent + "; hoverForeground: #ffffff;";

        btnLanzarMysql.putClientProperty("FlatLaf.style", styleBase);
        btnLanzarTomcat.putClientProperty("FlatLaf.style", styleBase);
        btnNgrok.putClientProperty("FlatLaf.style", styleBase);
        btnShowQR.putClientProperty("FlatLaf.style", styleBase);
        btnMonitorRed.putClientProperty("FlatLaf.style", styleBase);

        // --- 6. EVENTOS ESPECIALES ---
        // Reconectar botón QR (se pierde al reconstruir el panel dinámicamente)
        for (java.awt.event.ActionListener al : btnShowQR.getActionListeners())
            btnShowQR.removeActionListener(al);
        btnShowQR.addActionListener(e -> showNgrokDialog());

        btnTanner.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: " + hexBg
                        + "; borderColor: #FFD700; foreground: #FFD700; hoverForeground: #ffffff;");
        btnBots.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: " + hexBg
                        + "; borderColor: #FF4444; foreground: #FF4444; hoverForeground: #ffffff;");
        btnInforme.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: " + hexBg
                        + "; borderColor: #2ECC71; foreground: #2ECC71; hoverForeground: #ffffff;");

        // --- 5. DASHBOARD MONITOR RED (GeoMap) ---
        if (geoMap == null)
            geoMap = new GeoLocationPanel();
        panelMonitor.removeAll();
        panelMonitor.setLayout(new java.awt.BorderLayout());
        panelMonitor.add(geoMap, java.awt.BorderLayout.CENTER);

        // Panel de control para el botón Admin (siempre visible sobre los logs)
        javax.swing.JPanel pnlAdmin = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER));
        pnlAdmin.setOpaque(false);
        btnAdminChallenges.setText("ADMIN CHALLENGES");
        btnAdminChallenges.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; borderColor: " + hexAccent + "; background: " + hexBg + "; foreground: "
                        + hexAccent + ";");
        pnlAdmin.add(btnAdminChallenges);

        javax.swing.JPanel pnlSouth = new javax.swing.JPanel(new java.awt.BorderLayout());
        pnlSouth.setOpaque(false);
        pnlSouth.add(pnlAdmin, java.awt.BorderLayout.NORTH);

        monitorScroll.setBorder(javax.swing.BorderFactory.createTitledBorder(
                javax.swing.BorderFactory.createLineBorder(accent),
                "SYSTEM LOGS", 0, 0, null, accent));
        pnlSouth.add(monitorScroll, java.awt.BorderLayout.CENTER);
        pnlSouth.setPreferredSize(new java.awt.Dimension(800, 250));

        panelMonitor.add(pnlSouth, java.awt.BorderLayout.SOUTH);

        // Refrescar layouts
        panelOperaciones.revalidate();
        panelOperaciones.repaint();
        panelMonitor.revalidate();
        panelMonitor.repaint();

        System.out.println("[DEBUG] Componentes en panelGraphContainer: " + panelGraphContainer.getComponentCount());
        System.out.println("[DEBUG] Componentes en panelRightSidebar: " + panelRightSidebar.getComponentCount());
    }

    private void applyDeepTransparency(java.awt.Container container) {
        if (container == null) return;
        for (java.awt.Component c : container.getComponents()) {
            if (c instanceof javax.swing.JPanel) {
                ((javax.swing.JPanel) c).setOpaque(false);
                ((javax.swing.JPanel) c).setBackground(null);
                applyDeepTransparency((java.awt.Container) c);
            } else if (c instanceof javax.swing.JScrollPane) {
                ((javax.swing.JScrollPane) c).setOpaque(false);
                ((javax.swing.JScrollPane) c).getViewport().setOpaque(false);
                ((javax.swing.JScrollPane) c).getViewport().setBackground(null);
                applyDeepTransparency((java.awt.Container) c);
            }
        }
    }

    public void showMapToast(String message, String dangerLevel) {
        javax.swing.JLabel toast = new javax.swing.JLabel(message, javax.swing.SwingConstants.CENTER);
        toast.setOpaque(true);
        toast.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 12));

        if ("ALTA".equalsIgnoreCase(dangerLevel)) {
            toast.setBackground(new java.awt.Color(255, 0, 0, 200));
            toast.setForeground(java.awt.Color.WHITE);
        } else if ("MEDIA".equalsIgnoreCase(dangerLevel)) {
            toast.setBackground(new java.awt.Color(255, 165, 0, 200));
            toast.setForeground(java.awt.Color.BLACK);
        } else {
            toast.setBackground(new java.awt.Color(0, 242, 255, 200));
            toast.setForeground(java.awt.Color.BLACK);
        }

        toast.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.WHITE, 1));
        toast.setSize(300, 35);
        // Posicionamiento centrado en la parte superior
        int x = (this.getWidth() - 300) / 2;
        toast.setLocation(x, 50);

        java.awt.Component gp = getGlassPane();
        gp.setVisible(true);
        
        if (gp instanceof java.awt.Container) {
            java.awt.Container glass = (java.awt.Container) gp;
            // Si el glass pane no tiene layout (null), usamos setLocation
            // Si es el panel del CRT, solemos tener layout null
            glass.add(toast, 0); // Añadir al frente (index 0)
            
            toast.revalidate();
            glass.revalidate();
            glass.repaint();

            new Timer(3000, e -> {
                glass.remove(toast);
                glass.revalidate();
                glass.repaint();
                ((Timer) e.getSource()).stop();
            }).start();
        }
    }

    private void detectOperatingSystem() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            detectedOS = "WINDOWS";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            detectedOS = "LINUX";
        } else if (os.contains("mac")) {
            detectedOS = "MACOS";
        } else {
            detectedOS = "UNKNOWN";
        }
        logger.info("Sentinel C2 Kernel: " + detectedOS + " detected...");
    }

    private void initButtons() {

        // 1. Estética profesional "Red Alert" para el botón START (más agresivo)
        startIcon.setColorFilter(
                new com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter(color -> new java.awt.Color(255, 0, 0)));
        btnStart.putClientProperty("FlatLaf.style",
                "arc: 0; " +
                        "borderWidth: 2; " +
                        "borderColor: #FF0000; foreground: #FF0000; " +
                        "hoverBackground: #3d0000; " +
                        "hoverForeground: #FF0000; " +
                        "focusedBorderColor: #FF0000; " +
                        "iconTextGap: 15");
        btnStart.setBackground(new java.awt.Color(20, 0, 0));
        btnStart.setForeground(new java.awt.Color(255, 0, 0));
        btnStart.setIcon(startIcon);
        btnStart.setText(null);

        // 2. Estilos para otros botones
        btnGraphics.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: #00000000; borderColor: #FFD700; foreground: #FFD700; hoverForeground: #ffffff;");
        btnLogs.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: #00000000; borderColor: #2ECC71; foreground: #2ECC71; hoverForeground: #ffffff;");

        // Estilo para el botón del footer
        btnFooterNav.putClientProperty("FlatLaf.style", "arc: 0; borderWidth: 1; background: #00000000;");

        // 3. Listener de estado de ventana para sincronizar texto de Fullscreen
        this.addWindowStateListener(e -> {
            boolean isMaximized = (e.getNewState() & java.awt.Frame.MAXIMIZED_BOTH) != 0;
            java.awt.GraphicsDevice gd = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice();
            boolean isFullScreen = gd.getFullScreenWindow() == this;

            if (isFullScreen || isMaximized) {
                btnFullscreen.setText("WINDOWED");
            } else {
                btnFullscreen.setText("FULLSCREEN");
            }
        });

        // 4. Configuración extra
        titleBar.setVisible(true); // Restauramos visibilidad para permitir mover/cerrar la ventana
        footerPanel.setOpaque(false);

        // Estilo para los botones de control de ventana (Windows Style)
        String btnTitleStyle = "arc: 0; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; background: #00000000; ";

        btnMinimize.putClientProperty("FlatLaf.style",
                btnTitleStyle + "hoverBackground: #3a3a3a; pressedBackground: #4a4a4a;");
        btnMaximize.putClientProperty("FlatLaf.style",
                btnTitleStyle + "hoverBackground: #3a3a3a; pressedBackground: #4a4a4a;");
        btnClose.putClientProperty("FlatLaf.style",
                btnTitleStyle + "hoverBackground: #E81123; hoverForeground: #ffffff; pressedBackground: #c40a1b;");

        // Aseguramos que se dibuje el fondo transparente al inicio
        btnMinimize.setContentAreaFilled(false);
        btnMaximize.setContentAreaFilled(false);
        btnClose.setContentAreaFilled(false);

        if (com.formdev.flatlaf.util.SystemInfo.isWindows) {
            getRootPane().putClientProperty("FlatLaf.style", "arc: 15");
        }
    }

    public void showToast(String message) {
        JWindow toast = new JWindow();
        toast.setLayout(new BorderLayout());
        JPanel p = new JPanel();
        p.setBorder(BorderFactory.createLineBorder(Color.CYAN, 2));
        p.setBackground(new Color(10, 25, 47));
        p.add(new JLabel("<html><font color='white'>" + message + "</font></html>"));
        toast.add(p);
        toast.pack();

        // Posicionamiento (Abajo a la derecha)
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        toast.setLocation(scr.width - toast.getWidth() - 20, scr.height - toast.getHeight() - 60);

        toast.setVisible(true);

        // Timer para que desaparezca solo
        new Timer(3000, e -> toast.dispose()).start();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated
    // <editor-fold defaultstate="collapsed" desc="Generated
    // <editor-fold defaultstate="collapsed" desc="Generated
    // <editor-fold defaultstate="collapsed" desc="Generated
    // <editor-fold defaultstate="collapsed" desc="Generated
    // <editor-fold defaultstate="collapsed" desc="Generated
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        contentArea = new javax.swing.JPanel();
        panelLoading = new javax.swing.JPanel();
        lblOsIcon = new javax.swing.JLabel();
        lblLoadingStatus = new javax.swing.JLabel();
        prgBar = new javax.swing.JProgressBar();
        panelInicio = new javax.swing.JPanel();
        btnStart = new javax.swing.JButton();
        btnGraphics = new javax.swing.JButton();
        btnLogs = new javax.swing.JButton();
        panelSettings = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        btnFullscreen = new javax.swing.JButton();
        btnUiScale = new javax.swing.JButton();
        btnCtrFilter = new javax.swing.JButton();
        sliderBrightness = new javax.swing.JSlider();
        lblBrightness = new javax.swing.JLabel();
        consoleContainer = new javax.swing.JPanel();
        monitorScroll2 = new javax.swing.JScrollPane();
        monitorConsole2 = new io.sentinel.command.sentinelc2.SentinelConsole();
        panelMonitor = new javax.swing.JPanel();
        panelMonitorCenter = new javax.swing.JPanel();
        btnAdminChallenges = new javax.swing.JButton();
        panelMapPlaceholder = new javax.swing.JPanel();
        panelMonitorSouth = new javax.swing.JPanel();
        monitorScroll = new javax.swing.JScrollPane();
        monitorConsole = new io.sentinel.command.sentinelc2.SentinelConsole();
        panelChallenge = new javax.swing.JPanel();
        challengeScroll = new javax.swing.JScrollPane();
        tblChallenge = new javax.swing.JTable();
        panelChallengeControls = new javax.swing.JPanel();
        radioStandard = new javax.swing.JRadioButton();
        btnSyncChallenge = new javax.swing.JButton();
        radioSecurity = new javax.swing.JRadioButton();
        btnDeleteChallenge = new javax.swing.JButton();
        panelOperaciones = new javax.swing.JPanel();
        panelDashboardCenter = new javax.swing.JPanel();
        panelGraphContainer = new javax.swing.JPanel();
        panelTimelineControls = new javax.swing.JPanel();
        panelRightSidebar = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        lblStatusMysql = new javax.swing.JLabel();
        lblStatusDb = new javax.swing.JLabel();
        btnLanzarMysql = new javax.swing.JButton();
        lblStatusService = new javax.swing.JLabel();
        lblStatusPort = new javax.swing.JLabel();
        btnLanzarTomcat = new javax.swing.JButton();
        lblStatusNgrok = new javax.swing.JLabel();
        btnNgrok = new javax.swing.JButton();
        btnShowQR = new javax.swing.JButton();
        btnTanner = new javax.swing.JButton();
        btnBots = new javax.swing.JButton();
        btnInforme = new javax.swing.JButton();
        btnMonitorRed = new javax.swing.JButton();
        panelActivityFeed = new javax.swing.JPanel();
        titleBar = new javax.swing.JPanel();
        lblTitleIcon = new javax.swing.JLabel();
        btnMaximize = new javax.swing.JButton();
        btnMinimize = new javax.swing.JButton();
        btnClose = new javax.swing.JButton();
        footerPanel = new javax.swing.JPanel();
        themeToggle = new javax.swing.JToggleButton();
        btnFooterNav = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        contentArea.setMinimumSize(new java.awt.Dimension(0, 0));
        contentArea.setLayout(new java.awt.CardLayout());

        panelLoading.setLayout(new java.awt.GridBagLayout());

        lblOsIcon.setMaximumSize(new java.awt.Dimension(100, 100));
        lblOsIcon.setMinimumSize(new java.awt.Dimension(100, 100));
        lblOsIcon.setPreferredSize(new java.awt.Dimension(100, 100));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        panelLoading.add(lblOsIcon, gridBagConstraints);

        lblLoadingStatus.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        lblLoadingStatus.setForeground(new java.awt.Color(153, 153, 153));
        lblLoadingStatus.setText("ANALYZING SYSTEM KERNEL...");
        panelLoading.add(lblLoadingStatus, new java.awt.GridBagConstraints());

        prgBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        panelLoading.add(prgBar, gridBagConstraints);

        contentArea.add(panelLoading, "loading");

        panelInicio.setLayout(new java.awt.GridBagLayout());

        btnStart.setFont(new java.awt.Font("SansSerif", 1, 24)); // NOI18N
        btnStart.setMaximumSize(new java.awt.Dimension(120, 70));
        btnStart.setMinimumSize(new java.awt.Dimension(120, 70));
        btnStart.setPreferredSize(new java.awt.Dimension(120, 70));
        btnStart.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnStartActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 15);
        panelInicio.add(btnStart, gridBagConstraints);

        btnGraphics.setBackground(new java.awt.Color(255, 215, 0));
        btnGraphics.setText("GRAPHICS");
        btnGraphics.setMaximumSize(new java.awt.Dimension(104, 50));
        btnGraphics.setMinimumSize(new java.awt.Dimension(104, 50));
        btnGraphics.setPreferredSize(new java.awt.Dimension(104, 50));
        btnGraphics.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnGraphicsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 15);
        panelInicio.add(btnGraphics, gridBagConstraints);

        btnLogs.setBackground(new java.awt.Color(46, 204, 113));
        btnLogs.setText("SYSTEM LOGS");
        btnLogs.setMaximumSize(new java.awt.Dimension(104, 50));
        btnLogs.setMinimumSize(new java.awt.Dimension(104, 50));
        btnLogs.setPreferredSize(new java.awt.Dimension(104, 55));
        btnLogs.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnLogsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 15);
        panelInicio.add(btnLogs, gridBagConstraints);

        contentArea.setOpaque(false);
        panelMonitor.setOpaque(false);
        panelOperaciones.setOpaque(false);
        panelSettings.setOpaque(false);
        consoleContainer.setOpaque(false);

        contentArea.add(panelInicio, "card2");

        panelSettings.setLayout(new javax.swing.BoxLayout(panelSettings, javax.swing.BoxLayout.Y_AXIS));

        jPanel2.setLayout(new java.awt.GridBagLayout());

        btnFullscreen.setText("FULLSCREEN");
        btnFullscreen.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnFullscreenActionPerformed(evt);
            }
        });
        jPanel2.add(btnFullscreen, new java.awt.GridBagConstraints());

        btnUiScale.setText("UI SCALE");
        btnUiScale.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnUiScaleActionPerformed(evt);
            }
        });
        jPanel2.add(btnUiScale, new java.awt.GridBagConstraints());

        btnCtrFilter.setText("CTR FILTER");
        btnCtrFilter.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnCtrFilterActionPerformed(evt);
            }
        });
        jPanel2.add(btnCtrFilter, new java.awt.GridBagConstraints());

        panelSettings.add(jPanel2);

        sliderBrightness.setMaximum(2);
        sliderBrightness.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent evt)
            {
                sliderBrightnessStateChanged(evt);
            }
        });
        panelSettings.add(sliderBrightness);

        lblBrightness.setText("INTERFACE BRIGHTNESS");
        panelSettings.add(lblBrightness);

        contentArea.add(panelSettings, "settings");

        consoleContainer.setLayout(new java.awt.BorderLayout());

        monitorScroll2.setViewportView(monitorConsole2);

        // Estética "Perfecta" clonada del monitor principal
        java.awt.Color accent = getThemeAccentColor();
        monitorScroll2.setBorder(javax.swing.BorderFactory.createTitledBorder(
                javax.swing.BorderFactory.createLineBorder(accent),
                "SYSTEM LOGS", 0, 0, null, accent));

        consoleContainer.add(monitorScroll2, java.awt.BorderLayout.CENTER);

        contentArea.add(consoleContainer, "console");

        panelMonitor.setLayout(new java.awt.BorderLayout());

        panelMonitorCenter.setLayout(new java.awt.BorderLayout());

        btnAdminChallenges.setText("ADMIN CHALLENGES");
        btnAdminChallenges.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnAdminChallengesActionPerformed(evt);
            }
        });
        panelMonitorCenter.add(btnAdminChallenges, java.awt.BorderLayout.SOUTH);

        panelMapPlaceholder.setLayout(new java.awt.BorderLayout());
        panelMonitorCenter.add(panelMapPlaceholder, java.awt.BorderLayout.CENTER);

        panelMonitor.add(panelMonitorCenter, java.awt.BorderLayout.CENTER);

        panelMonitorSouth.setLayout(new java.awt.BorderLayout());

        monitorScroll.setViewportView(monitorConsole);

        panelMonitorSouth.add(monitorScroll, java.awt.BorderLayout.CENTER);

        panelMonitor.add(panelMonitorSouth, java.awt.BorderLayout.SOUTH);

        contentArea.add(panelMonitor, "monitor");

        panelChallenge.setLayout(new javax.swing.BoxLayout(panelChallenge, javax.swing.BoxLayout.LINE_AXIS));

        tblChallenge.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "ID", "USERNAME", "PASSWORD"
            }
        )
        {
            Class[] types = new Class []
            {
                java.lang.Integer.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean []
            {
                false, true, true
            };

            public Class getColumnClass(int columnIndex)
            {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit [columnIndex];
            }
        });
        challengeScroll.setViewportView(tblChallenge);

        panelChallenge.add(challengeScroll);

        radioStandard.setSelected(true);
        radioStandard.setText("VIEW: STANDARD");
        radioStandard.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                radioViewModeActionPerformed(evt);
            }
        });

        btnSyncChallenge.setText("SYNC CHANGES");
        btnSyncChallenge.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnSyncChallengeActionPerformed(evt);
            }
        });

        radioSecurity.setText("VIEW: SECURITY");
        radioSecurity.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                radioViewModeActionPerformed(evt);
            }
        });

        btnDeleteChallenge.setText("DELETE SELECTED");
        btnDeleteChallenge.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnDeleteChallengeActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout panelChallengeControlsLayout = new javax.swing.GroupLayout(panelChallengeControls);
        panelChallengeControls.setLayout(panelChallengeControlsLayout);
        panelChallengeControlsLayout.setHorizontalGroup(
            panelChallengeControlsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelChallengeControlsLayout.createSequentialGroup()
                .addGap(21, 21, 21)
                .addGroup(panelChallengeControlsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(radioStandard)
                    .addGroup(panelChallengeControlsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(btnSyncChallenge, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(radioSecurity)
                        .addComponent(btnDeleteChallenge, javax.swing.GroupLayout.PREFERRED_SIZE, 116, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(20, Short.MAX_VALUE))
        );
        panelChallengeControlsLayout.setVerticalGroup(
            panelChallengeControlsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelChallengeControlsLayout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addComponent(radioSecurity)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(radioStandard)
                .addGap(14, 14, 14)
                .addComponent(btnDeleteChallenge)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnSyncChallenge)
                .addContainerGap(167, Short.MAX_VALUE))
        );

        panelChallenge.add(panelChallengeControls);

        contentArea.add(panelChallenge, "challenge");

        panelOperaciones.setLayout(new java.awt.BorderLayout());

        panelDashboardCenter.setLayout(new java.awt.BorderLayout());

        panelGraphContainer.setLayout(new java.awt.BorderLayout());
        panelDashboardCenter.add(panelGraphContainer, java.awt.BorderLayout.CENTER);
        panelDashboardCenter.add(panelTimelineControls, java.awt.BorderLayout.SOUTH);

        panelOperaciones.add(panelDashboardCenter, java.awt.BorderLayout.CENTER);

        panelRightSidebar.setLayout(new javax.swing.BoxLayout(panelRightSidebar, javax.swing.BoxLayout.Y_AXIS));

        jPanel1.setMaximumSize(new java.awt.Dimension(180, 32767));
        jPanel1.setPreferredSize(new java.awt.Dimension(180, 500));
        jPanel1.setLayout(new javax.swing.BoxLayout(jPanel1, javax.swing.BoxLayout.Y_AXIS));

        lblStatusMysql.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        lblStatusMysql.setText("○ MYSQL: OFFLINE");
        jPanel1.add(lblStatusMysql);

        lblStatusDb.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        lblStatusDb.setText("○ DB: DISCONNECTED");
        jPanel1.add(lblStatusDb);

        btnLanzarMysql.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        btnLanzarMysql.setText("START MYSQL");
        btnLanzarMysql.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnLanzarMysqlActionPerformed(evt);
            }
        });
        jPanel1.add(btnLanzarMysql);

        lblStatusService.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        lblStatusService.setForeground(new java.awt.Color(100, 100, 100));
        lblStatusService.setText("○ TOMCAT: INACTIVE");
        jPanel1.add(lblStatusService);

        lblStatusPort.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        lblStatusPort.setForeground(new java.awt.Color(100, 100, 100));
        lblStatusPort.setText("○ PORT 8080");
        jPanel1.add(lblStatusPort);

        btnLanzarTomcat.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        btnLanzarTomcat.setText("START TOMCAT");
        btnLanzarTomcat.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnLanzarTomcatActionPerformed(evt);
            }
        });
        jPanel1.add(btnLanzarTomcat);

        lblStatusNgrok.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        lblStatusNgrok.setText("○ NGROK: INACTIVE");
        jPanel1.add(lblStatusNgrok);

        btnNgrok.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        btnNgrok.setText("LANZAR NGROK");
        btnNgrok.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnNgrokActionPerformed(evt);
            }
        });
        jPanel1.add(btnNgrok);

        btnShowQR.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        btnShowQR.setText("MOSTRAR QR");
        jPanel1.add(btnShowQR);

        btnTanner.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        btnTanner.setText("LANZAR TANNER");
        btnTanner.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnTannerActionPerformed(evt);
            }
        });
        jPanel1.add(btnTanner);

        btnBots.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        btnBots.setText("LANZAR BOTS");
        btnBots.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnBotsActionPerformed(evt);
            }
        });
        jPanel1.add(btnBots);

        btnInforme.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        btnInforme.setText("INFORME IA");
        btnInforme.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnInformeActionPerformed(evt);
            }
        });
        jPanel1.add(btnInforme);

        btnMonitorRed.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        btnMonitorRed.setText("MONITOR RED");
        btnMonitorRed.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnMonitorRedActionPerformed(evt);
            }
        });
        jPanel1.add(btnMonitorRed);

        panelRightSidebar.add(jPanel1);

        panelActivityFeed.setLayout(new java.awt.BorderLayout());
        panelRightSidebar.add(panelActivityFeed);

        panelOperaciones.add(panelRightSidebar, java.awt.BorderLayout.EAST);

        contentArea.add(panelOperaciones, "ops");

        getContentPane().add(contentArea, java.awt.BorderLayout.CENTER);

        titleBar.setToolTipText("");
        titleBar.setName(""); // NOI18N
        titleBar.addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
        {
            public void mouseDragged(java.awt.event.MouseEvent evt)
            {
                titleBarMouseDragged(evt);
            }
        });
        titleBar.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mousePressed(java.awt.event.MouseEvent evt)
            {
                titleBarMousePressed(evt);
            }
        });

        btnMaximize.setText("⬜");
        btnMaximize.setContentAreaFilled(false);
        btnMaximize.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnMaximizeActionPerformed(evt);
            }
        });

        btnMinimize.setText("-");
        btnMinimize.setContentAreaFilled(false);
        btnMinimize.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnMinimizeActionPerformed(evt);
            }
        });

        btnClose.setText("❌");
        btnClose.setContentAreaFilled(false);
        btnClose.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnCloseActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout titleBarLayout = new javax.swing.GroupLayout(titleBar);
        titleBar.setLayout(titleBarLayout);
        titleBarLayout.setHorizontalGroup(
            titleBarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, titleBarLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lblTitleIcon, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 669, Short.MAX_VALUE)
                .addComponent(btnMaximize)
                .addGap(0, 0, 0)
                .addComponent(btnMinimize)
                .addGap(0, 0, 0)
                .addComponent(btnClose)
                .addContainerGap())
        );
        titleBarLayout.setVerticalGroup(
            titleBarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(titleBarLayout.createSequentialGroup()
                .addGroup(titleBarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(titleBarLayout.createSequentialGroup()
                        .addGap(12, 12, 12)
                        .addGroup(titleBarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(btnMaximize)
                            .addComponent(btnMinimize)
                            .addComponent(btnClose)))
                    .addGroup(titleBarLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(lblTitleIcon, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );

        lblTitleIcon.getAccessibleContext().setAccessibleName("lblTitleIcon");

        getContentPane().add(titleBar, java.awt.BorderLayout.NORTH);

        footerPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));

        themeToggle.setSelected(true);
        themeToggle.setMaximumSize(new java.awt.Dimension(30, 30));
        themeToggle.setMinimumSize(new java.awt.Dimension(30, 30));
        themeToggle.setPreferredSize(new java.awt.Dimension(35, 35));
        themeToggle.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                themeToggleActionPerformed(evt);
            }
        });
        footerPanel.add(themeToggle);

        btnFooterNav.setText("EXIT");
        footerPanel.add(btnFooterNav);

        getContentPane().add(footerPanel, java.awt.BorderLayout.SOUTH);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnStartActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_btnStartActionPerformed
    {// GEN-HEADEREND:event_btnStartActionPerformed

        navigateTo("ops");

    }// GEN-LAST:event_btnStartActionPerformed

    private void btnGraphicsActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_btnGraphicsActionPerformed
    {// GEN-HEADEREND:event_btnGraphicsActionPerformed
        navigateTo("settings");

    }// GEN-LAST:event_btnGraphicsActionPerformed

    private void btnLogsActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_btnLogsActionPerformed
    {// GEN-HEADEREND:event_btnLogsActionPerformed
        navigateTo("console");

    }// GEN-LAST:event_btnLogsActionPerformed

    private void btnBackSettingsActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_btnBackSettingsActionPerformed
    {// GEN-HEADEREND:event_btnBackSettingsActionPerformed
        updateFooterNav("EXIT", e -> startExitAnimation());
        java.awt.CardLayout cl = (java.awt.CardLayout) contentArea.getLayout();
        cl.show(contentArea, "card2"); // Regresa al inicio (Menú Principal)
    }// GEN-LAST:event_btnBackSettingsActionPerformed

    private void btnFullscreenActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_btnFullscreenActionPerformed
    {// GEN-HEADEREND:event_btnFullscreenActionPerformed
        java.awt.GraphicsConfiguration gc = this.getGraphicsConfiguration();
        java.awt.Rectangle screenBounds = gc.getBounds();

        // Detección de estado actual (Basada en visibilidad de barra y dimensiones)
        boolean isCurrentlyFull = !titleBar.isVisible();

        if (isCurrentlyFull) {
            // SALIR DE FULLSCREEN -> IR A VENTANA NORMAL
            setExtendedState(javax.swing.JFrame.NORMAL);
            titleBar.setVisible(true);
            setSize(getMinimumSize());
            setLocationRelativeTo(null);
            btnFullscreen.setText("MAXIMIZE");
        } else if (getExtendedState() == java.awt.Frame.MAXIMIZED_BOTH) {
            // IR A FULLSCREEN INMERSIVO (Compatible con popups) desde Maximizado
            setExtendedState(javax.swing.JFrame.NORMAL); // Reset para control total de bounds
            titleBar.setVisible(false);
            setBounds(screenBounds);
            btnFullscreen.setText("WINDOWED");
        } else {
            // IR A MAXIMIZADO desde ventana normal
            setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
            titleBar.setVisible(true);
            btnFullscreen.setText("FULLSCREEN");
        }

        // Asegurar que la UI se refresque correctamente
        revalidate();
        repaint();
    }// GEN-LAST:event_btnFullscreenActionPerformed

    private void btnCtrFilterActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_btnCtrFilterActionPerformed
    {// GEN-HEADEREND:event_btnCtrFilterActionPerformed
        isCrtActivate = !isCrtActivate;
        if (isCrtActivate) {
            if (crtOverlay == null) {
                initCrtOverlay();
            }
            setGlassPane(crtOverlay);
            getGlassPane().setVisible(true);
            crtTimer.start();
            btnCtrFilter.setText("CRT: ON");
        } else {
            getGlassPane().setVisible(false);
            if (crtTimer != null)
                crtTimer.stop();
            btnCtrFilter.setText("CRT: OFF");
        }
        saveConfig(); // Guardamos el cambio al instante
    }// GEN-LAST:event_btnCtrFilterActionPerformed

    private void initCrtOverlay() {
        crtOverlay = new javax.swing.JPanel() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();

                boolean isDark = currentThemeMode == 2;

                // 1. Scanlines horizontales
                // En modo oscuro usamos un tono ligeramente más claro para que contraste
                int scanAlpha = isDark ? 60 : 35;
                java.awt.Color scanColor = isDark ? new java.awt.Color(255, 255, 255, 10)
                        : new java.awt.Color(0, 0, 0, scanAlpha);

                g2.setColor(scanColor);
                for (int y = 0; y < getHeight(); y += 3) {
                    g2.drawLine(0, y, getWidth(), y);
                }

                // 2. Grain/Noise Effect
                // En modo oscuro subimos la visibilidad del grano
                int noiseAlpha = isDark ? 20 : 8;
                g2.setColor(new java.awt.Color(255, 255, 255, noiseAlpha));
                java.util.Random rand = new java.util.Random();
                for (int i = 0; i < 2000; i++) {
                    int x = rand.nextInt(getWidth() + 1);
                    int y = rand.nextInt(getHeight() + 1);
                    g2.fillRect(x, y, 1, 1);
                }

                // 3. Tinte CRT (Verde/Cian)
                // Más intenso en modo oscuro para el glow
                int tintAlpha = isDark ? 15 : 5;
                g2.setColor(new java.awt.Color(0, 255, 100, tintAlpha));
                g2.fillRect(0, 0, getWidth(), getHeight());

                g2.dispose();
            }

            @Override
            public boolean contains(int x, int y) {
                return false; // Permite clics a través del overlay
            }
        };
        crtOverlay.setOpaque(false);

        crtTimer = new javax.swing.Timer(80, e -> {
            if (isCrtActivate && crtOverlay != null) {
                crtOverlay.repaint();
            }
        });
    }

    private void btnUiScaleActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_btnUiScaleActionPerformed
    {// GEN-HEADEREND:event_btnUiScaleActionPerformed
        uiScaleLevel = (uiScaleLevel + 1) % 3;
        float scale = (uiScaleLevel == 0) ? 0.75f : (uiScaleLevel == 1) ? 1.0f : 1.5f;

        // Aplicamos la escala global
        System.setProperty("flatlaf.uiScale", String.valueOf(scale));
        FlatLaf.updateUI(); // Refresca toda la app al instante

        // Sincronizamos los botones con el nuevo escalado
        updateButtonSizes(scale);

        String label = (uiScaleLevel == 0) ? "SMALL" : (uiScaleLevel == 1) ? "NORMAL" : "HUGE";
        btnUiScale.setText("UI : " + label); // TODO add your handling code here:

        saveConfig(); // Guardamos el cambio al instante
    }// GEN-LAST:event_btnUiScaleActionPerformed

    private boolean isPortInUse(int port) {
        try (Socket s = new java.net.Socket("localhost", port)) {
            return true; // Si conecta, el puerto está ocupado
        } catch (IOException e) {
            return false; // Si falla la conexión, el puerto está libre
        }
    }

    private void btnLanzarTomcatActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_btnLanzarTomcatActionPerformed
    {// GEN-HEADEREND:event_btnLanzarTomcatActionPerformed
        String catalinaHome = savedCatalinaHome != null ? savedCatalinaHome : System.getenv("CATALINA_HOME");

        if (catalinaHome == null || catalinaHome.trim().isEmpty()) {
            log("ADVERTENCIA: CATALINA_HOME no detectado. Solicitando vinculación manual...",
                    Color.ORANGE);
            int resp = JOptionPane.showConfirmDialog(this,
                    "No se encontró la ruta de Tomcat en tu sistema (CATALINA_HOME vacío).\n¿Deseas seleccionar la carpeta 'bin' de Apache Tomcat manualmente?",
                    "Vincular Apache Tomcat", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (resp == JOptionPane.YES_OPTION) {
                javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
                chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
                chooser.setDialogTitle("Selecciona la carpeta 'bin' de Tomcat");
                if (chooser.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
                    File selected = chooser.getSelectedFile();
                    catalinaHome = selected.getParent(); // Guardamos el padre de bin/
                    savedCatalinaHome = catalinaHome; // Persistencia en memoria runtime
                    log("Ruta de Tomcat mapeada exitosamente: " + catalinaHome, Color.GREEN);
                } else {
                    log("Error: Operación cancelada por el operador.", "ERROR");
                    return;
                }
            } else {
                return;
            }
        }

        if (!isTomcatRunning) {
            // --- LIMPIEZA PREVENTIVA DE PUERTO 8080 ---
            // Forzamos el cierre de cualquier proceso en el puerto 8080 (ej. NetBeans)
            // para garantizar que Sentinel C2 pueda capturar los logs tácticos.
            try {
                if (detectedOS.toLowerCase().contains("win")) {
                    String killCmd = "powershell -Command \"Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }\"";
                    Runtime.getRuntime().exec("cmd.exe /c " + killCmd).waitFor();
                    Thread.sleep(1000);
                }
            } catch (Exception killEx) {
                // Silencioso si no hay nada que matar o falla el acceso
            }

            // INTENTANDO INICIAR
            isTransitioningService = true;
            log("Iniciando secuencia Apache Tomcat [CATALINA_RUN]...", Color.CYAN);

            try {
                File binDir = new File(catalinaHome, "bin");
                String script = detectedOS.toLowerCase().contains("win") ? "catalina.bat" : "catalina.sh";
                File execScript = new File(binDir, script);

                if (!execScript.exists()) {
                    log("ERROR: No se encontró " + script + " en " + binDir.getAbsolutePath(),
                            Color.RED);
                    JOptionPane.showMessageDialog(this,
                            "El ejecutable de Tomcat (" + script + ") no existe en la ruta especificada.",
                            "Fallo de Requisito", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // --- AUTO-DEPLOY SEQUENCE ---
                try {
                    log("[SENTINEL] Preparando LoginWeb.war...", Color.CYAN);
                    File warSource = new File("../../LoginWeb/target/LoginWeb-1.0-SNAPSHOT.war");
                    File webappsDir = new File(catalinaHome, "webapps");

                    if (warSource.exists() && webappsDir.exists()) {
                        File destWar = new File(webappsDir, "LoginWeb.war");
                        java.nio.file.Files.copy(warSource.toPath(), destWar.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        log("[SENTINEL] Despliegue en caliente OK: LoginWeb.war", Color.GREEN);
                    }
                } catch (Exception deployEx) {
                    log("[SENTINEL] Error en despliegue: " + deployEx.getMessage(), Color.RED);
                }

                // Ejecución Portátil: catalina run
                ProcessBuilder pbTomcat;
                if (detectedOS.toLowerCase().contains("win")) {
                    pbTomcat = new ProcessBuilder("cmd.exe", "/c", "\"" + execScript.getAbsolutePath() + "\"", "run");
                } else {
                    pbTomcat = new ProcessBuilder("sh", execScript.getAbsolutePath(), "run");
                }

                Map<String, String> env = pbTomcat.environment();
                env.put("CATALINA_HOME", catalinaHome);
                if (System.getenv("JAVA_HOME") != null)
                    env.put("JAVA_HOME", System.getenv("JAVA_HOME"));

                pbTomcat.directory(binDir);
                pbTomcat.redirectErrorStream(true);

                log("[SENTINEL] Lanzando proceso: " + execScript.getName(), Color.CYAN);
                tomcatProcess = pbTomcat.start();

                new Thread(() -> {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(tomcatProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            final String logLine = line;
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                Color logColor = logLine.contains("ERROR") ? Color.RED
                                        : (logLine.contains("Exception") ? Color.ORANGE : Color.WHITE);
                                log("[TOMCAT/App] " + logLine, logColor);
                            });
                        }
                    } catch (Exception ex) {
                    }
                }).start();

                log("Apache Tomcat iniciado en modo INTERCEPT (Live Logs)", Color.GREEN);
                showMapToast("TOMCAT SERVICE INITIATED...", "MEDIA");
                actualizarIndicadores(true);
                isTomcatRunning = true;
                btnLanzarTomcat.setText("STOP TOMCAT");
                setTomcatUIState(true);

                // --- INICIAR MONITOR DE RED (NODE.JS) ---
                startNetworkMonitor();

                javax.swing.Timer initTimer = new javax.swing.Timer(5000, te -> isTransitioningService = false);
                initTimer.setRepeats(false);
                initTimer.start();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "FALLO FATAL al iniciar Tomcat", "Fallo de Fatal",
                        JOptionPane.ERROR_MESSAGE);
                log("FALLO FATAL al iniciar Tomcat: " + e.getMessage(), Color.RED);
                setTomcatUIState(false);
            }
        } else {
            // INTENTANDO DETENER
            isTransitioningService = true;
            log("Enviando señal de terminación a Tomcat...", Color.CYAN);
            try {
                if (tomcatProcess != null) {
                    // Cierre elegante y luego forzado de toda la rama de procesos
                    java.util.Optional<java.lang.ProcessHandle> handle = java.lang.ProcessHandle
                            .of(tomcatProcess.pid());
                    handle.ifPresent(h -> {
                        h.descendants().forEach(java.lang.ProcessHandle::destroyForcibly);
                        h.destroyForcibly();
                    });
                    tomcatProcess.destroyForcibly();
                }

                // --- DETENER MONITOR DE RED ---
                stopNetworkMonitor();

                isTomcatRunning = false;
                btnLanzarTomcat.setText("START TOMCAT");
                setTomcatUIState(false);
                log("Servicios detenidos quirúrgicamente.", Color.WHITE);
                showMapToast("TOMCAT SERVICE STOPPED", "MEDIA");
                actualizarIndicadores(false);

                // Grace period: esperar a que el SO libere los puertos antes de reanudar el
                // monitor
                javax.swing.Timer releaseTimer = new javax.swing.Timer(4000, te -> isTransitioningService = false);
                releaseTimer.setRepeats(false);
                releaseTimer.start();
            } catch (Exception e) {
                log("Fallo crítico en Tomcat Service: " + e.getMessage(), "ERROR");
                isTransitioningService = false;
            }
        }
    }// GEN-LAST:event_btnLanzarTomcatActionPerformed

    private void btnLanzarMysqlActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnLanzarMysqlActionPerformed
        if (!isMysqlRunning)
            lanzarMysql();
        else
            detenerMysql();
    }// GEN-LAST:event_btnLanzarMysqlActionPerformed

    private void btnNgrokActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnNgrokActionPerformed
        if (isNgrokRunning) {
            try {
                String killCmd = (detectedOS.toLowerCase().contains("win")) ? "taskkill /F /IM ngrok* /T"
                        : "pkill ngrok";
                Runtime.getRuntime().exec(killCmd);
                log("Deteniendo Servicio Ngrok...", java.awt.Color.ORANGE);
                showMapToast("NGROK SERVICE STOPPED", "MEDIA");
            } catch (Exception ex) {
                log("Error al detener Ngrok: " + ex.getMessage(), "ERROR");
            }
        } else {
            if (!checkDependency("ngrok")) {
                int res = JOptionPane.showConfirmDialog(this, "Ngrok no detectado. ¿Deseas descargarlo ahora?",
                        "SENTINEL - REQUISITO", JOptionPane.YES_NO_OPTION);
                if (res == JOptionPane.YES_OPTION) {
                    promptInstallation(detectedOS, "NGROK");
                }
            } else {
                // GUARDIA: verificar authtoken antes de lanzar el túnel
                if (!tieneNgrokAuthtoken()) {
                    JOptionPane.showMessageDialog(this,
                            "⚠️  NGROK AUTHTOKEN NO CONFIGURADO\n\n" +
                                    "ngrok requiere un authtoken para crear túneles.\n" +
                                    "Sin él, el túnel fallará al iniciarse.\n\n" +
                                    "Para obtenerlo y configurarlo:\n" +
                                    "  1. Regístrate en: https://dashboard.ngrok.com\n" +
                                    "  2. Copia tu authtoken desde 'Your Authtoken'.\n" +
                                    "  3. En una terminal, ejecuta:\n" +
                                    "     ngrok config add-authtoken TU_TOKEN_AQUI\n\n" +
                                    "Después de configurarlo, vuelve a pulsar LANZAR NGROK.",
                            "SENTINEL C2 — NGROK: TOKEN REQUERIDO",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }

                try {
                    String ngrokPath = findToolPath("ngrok");
                    if (ngrokPath == null) {
                        log("[NGROK] Ejecutable no encontrado tras búsqueda exhaustiva.", "ERROR");
                        return;
                    }

                    String[] launchCmd = detectedOS.toLowerCase().contains("win")
                            ? new String[] { "cmd", "/c", "start", "", ngrokPath, "http", "8080" }
                            : new String[] { ngrokPath, "http", "8080" };
                    Runtime.getRuntime().exec(launchCmd);
                    log("Lanzando Ngrok Tunnel (Port 8080) desde: " + ngrokPath, java.awt.Color.CYAN);
                    showMapToast("NGROK TUNNEL INITIATED...", "MEDIA");

                    new javax.swing.Timer(3500, ev -> {
                        showNgrokDialog();
                        ((javax.swing.Timer) ev.getSource()).stop();
                    }).start();
                } catch (Exception ex) {
                    log("Fallo al lanzar Ngrok: " + ex.getMessage(), "ERROR");
                }
            }
        }
    }// GEN-LAST:event_btnNgrokActionPerformed

    private void btnTannerActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnTannerActionPerformed
        checkAndInstallParamikoThenLaunch();
    }// GEN-LAST:event_btnTannerActionPerformed

    private void btnBotsActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnBotsActionPerformed
        log("═══════════════════════════════════════════", java.awt.Color.WHITE);
        log("  INFILTRATION BOTS - INICIANDO SIMULACIÓN  ", java.awt.Color.WHITE);
        log("═══════════════════════════════════════════", java.awt.Color.WHITE);
        showMapToast("INFILTRATION BOTS INJECTION STARTED...", "ALTA");

        new Thread(() -> {
            try {
                String nodeBin = findNodeBinary();
                File botsDir = new File("../../Ataque_Bots");
                if (!botsDir.exists())
                    botsDir = new File("Ataque_Bots");

                ProcessBuilder pb = new ProcessBuilder(nodeBin, "lanzador_maestro.js");
                pb.directory(botsDir);
                pb.redirectErrorStream(true);

                Process p = pb.start();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String logLine = line;
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            log("[BOTS] " + logLine, Color.LIGHT_GRAY);
                        });
                    }
                }
            } catch (Exception ex) {
                log("FALLO al lanzar bots: " + ex.getMessage(), Color.RED);
            }
        }).start();
    }// GEN-LAST:event_btnBotsActionPerformed

    private void btnInformeActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnInformeActionPerformed
        if (groqApiKey == null || groqApiKey.isEmpty()) {
            showGroqOnboarding();
            if (groqApiKey == null || groqApiKey.isEmpty())
                return;
        }

        log("═══════════════════════════════════════════", java.awt.Color.MAGENTA);
        log("  GROQ CLOUD AI ENGINE - INICIANDO...", java.awt.Color.MAGENTA);
        log("═══════════════════════════════════════════", java.awt.Color.MAGENTA);
        showMapToast("GROQ ANALYSIS REQUESTED...", "MEDIA");
        btnInforme.setEnabled(false);
        btnInforme.setText("ANALIZANDO...");

        javax.swing.JDialog progressDialog = new javax.swing.JDialog(MainFrame.this, "SentinelC2 - Motor IA CLOUD",
                false);
        progressDialog.setSize(420, 140);
        progressDialog.setLocationRelativeTo(MainFrame.this);
        progressDialog.setLayout(new java.awt.BorderLayout(10, 10));
        progressDialog.getContentPane().setBackground(new java.awt.Color(10, 25, 47));

        javax.swing.JLabel lblProgress = new javax.swing.JLabel("  Sentinel consultando Groq Cloud...",
                javax.swing.SwingConstants.LEFT);
        lblProgress.setForeground(new java.awt.Color(0, 242, 255));
        lblProgress.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 13));

        javax.swing.JProgressBar prgAI = new javax.swing.JProgressBar();
        prgAI.setIndeterminate(true);
        prgAI.setStringPainted(true);
        prgAI.setString("Consultando Groq: Llama-3.3-70B...");
        prgAI.setForeground(new java.awt.Color(0, 242, 255));

        progressDialog.add(lblProgress, java.awt.BorderLayout.NORTH);
        progressDialog.add(prgAI, java.awt.BorderLayout.CENTER);
        progressDialog.setVisible(true);

        new javax.swing.SwingWorker<Object[], String>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                // 1. Fetch real DB context
                Map<String, Object> stats = fetchDbStatsForReport();

                // 2. Build context string (INTELIGENCIA AVANZADA)
                StringBuilder contextBuilder = new StringBuilder();
                contextBuilder.append("TOTAL INCIDENTES: ").append(stats.getOrDefault("total_intentos", 0))
                        .append("\\n");

                contextBuilder.append("TOP 15 CONTRASEAS (GLOBAL):\\n");
                List<Map<String, Object>> topPwGlobal = (List<Map<String, Object>>) stats.get("top_passwords_global");
                if (topPwGlobal != null) {
                    for (Map<String, Object> p : topPwGlobal) {
                        contextBuilder.append("- ").append(p.get("pass")).append(" (").append(p.get("cnt"))
                                .append(" veces)\\n");
                    }
                }

                contextBuilder.append("BRECHAS DETECTADAS (MATCH CHALLENGE):\\n");
                List<Map<String, Object>> matchPw = (List<Map<String, Object>>) stats.get("password_matches");
                if (matchPw != null) {
                    for (Map<String, Object> m : matchPw) {
                        contextBuilder.append("- ").append(m.get("pass")).append(" (CRTICO)\\n");
                    }
                }

                contextBuilder.append("INTELIGENCIA DE DISPOSITIVOS:\\n");
                List<Map<String, Object>> hardware = (List<Map<String, Object>>) stats.get("hardware_stats");
                if (hardware != null) {
                    for (Map<String, Object> h : hardware) {
                        contextBuilder.append("- Modelo: ").append(h.get("modelo")).append(" Bat: ")
                                .append(h.get("bateria")).append("\\n");
                    }
                }

                contextBuilder.append("TOP IPs / UBICACIONES:\\n");
                List<Map<String, Object>> topIps = (List<Map<String, Object>>) stats.get("top_ips");
                if (topIps != null) {
                    for (Map<String, Object> ip : topIps) {
                        contextBuilder.append("- ").append(ip.get("ip")).append(" (").append(ip.get("cnt"))
                                .append(" ataques)\\n");
                    }
                }

                // 3. Obtain report from Groq Cloud
                String reportText = GroqClient.generateReport(groqApiKey, contextBuilder.toString());

                return new Object[] { reportText, stats };
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                btnInforme.setEnabled(true);
                btnInforme.setText("INFORME IA");
                try {
                    Object[] result = get();
                    if (result != null) {
                        String reportText = (String) result[0];
                        Map<String, Object> stats = (Map<String, Object>) result[1];

                        // Generar y abrir el informe directamente (Headless)
                        ControlPanelFrame.generarYAbrirReporte(reportText, stats);
                    }
                } catch (Exception ex) {
                    log("Error en IA: " + ex.getMessage(), Color.RED);
                }
            }
        }.execute();
    }// GEN-LAST:event_btnInformeActionPerformed

    private java.util.Map<String, Object> fetchDbStatsForReport() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        String url = DBConfig.getInstance().getUrl();
        try (Connection conn = DriverManager.getConnection(url, DBConfig.getInstance().getUser(),
                DBConfig.getInstance().getPassword())) {
            // total intentos
            try (java.sql.PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM capturas");
                    java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    stats.put("total_intentos", rs.getInt(1));
            }
            // top ips
            List<Map<String, Object>> topIps = new ArrayList<>();
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT ip_address, COUNT(*) as cnt FROM capturas GROUP BY ip_address ORDER BY cnt DESC LIMIT 5");
                    java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("ip", rs.getString(1));
                    m.put("cnt", rs.getInt(2));
                    topIps.add(m);
                }
            }
            stats.put("top_ips", topIps);

            // TOP 15 PASSWORDS GLOBAL (Texto Plano)
            List<Map<String, Object>> topPwGlobal = new ArrayList<>();
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT password_en_plano, COUNT(*) as cnt FROM capturas WHERE password_en_plano IS NOT NULL AND password_en_plano != '' GROUP BY password_en_plano ORDER BY cnt DESC LIMIT 15");
                    java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("pass", rs.getString(1));
                    m.put("cnt", rs.getInt(2));
                    topPwGlobal.add(m);
                }
            }
            stats.put("top_passwords_global", topPwGlobal);

            // MATCHED CHALLENGE (Las que adivinaron) - Incluimos USUARIO para el reporte
            List<Map<String, Object>> matchPw = new ArrayList<>();
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT c.usuario, c.password_en_plano as pass, COUNT(*) as cnt FROM capturas c INNER JOIN challenge ch ON c.password = ch.password_hash GROUP BY c.usuario, c.password_en_plano");
                    java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("usuario", rs.getString(1));
                    m.put("pass", rs.getString(2));
                    m.put("cnt", rs.getInt(3));
                    matchPw.add(m);
                }
            }
            stats.put("password_matches", matchPw);

            // 5. HEATMAP DATA (Histórico Global con Gravedad Dinámica)
            List<Map<String, Object>> heatmap = new ArrayList<>();
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT latitud, longitud, ubicacion, COUNT(*) as cnt FROM capturas WHERE latitud != 0 OR longitud != 0 GROUP BY latitud, longitud, ubicacion ORDER BY cnt DESC");
                    java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("lat", rs.getDouble(1));
                    m.put("lng", rs.getDouble(2));
                    m.put("ubicacion", rs.getString(3));
                    m.put("cnt", rs.getInt(4));
                    heatmap.add(m);
                }
            } catch (Exception e) {
                if (monitorConsole != null)
                    log("Fallo en Heatmap: " + e.getMessage(), Color.YELLOW);
            }
            stats.put("heatmap_data", heatmap);

            // 6. HARDWARE TELEMETRY (Histórico Global)
            List<Map<String, Object>> hwStats = new ArrayList<>();
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT modelo, bateria FROM capturas WHERE modelo IS NOT NULL AND modelo != '' GROUP BY modelo, bateria ORDER BY COUNT(*) DESC");
                    java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("modelo", rs.getString(1));
                    m.put("bateria", rs.getString(2));
                    hwStats.add(m);
                }
            } catch (Exception e) {
                if (monitorConsole != null)
                    log("Fallo en Hardware: " + e.getMessage(), Color.YELLOW);
            }
            stats.put("hardware_stats", hwStats);

            // 7. DEEP TRACE PROFILING: Identidad & Conducta
            List<Map<String, Object>> eliteData = new ArrayList<>();
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT ip_address, usuario, ubicacion, modelo, bateria, ip_privada, biometria, raw_json, password_en_plano, user_agent FROM capturas ORDER BY id DESC");
                    java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String ipA = rs.getString(1);
                    String user = rs.getString(2);
                    String ubi = rs.getString(3);
                    String mod = rs.getString(4);
                    String bat = rs.getString(5);
                    String ipP = rs.getString(6);
                    String bio = rs.getString(7);
                    String raw = rs.getString(8);
                    String pass = rs.getString(9);
                    String ua = rs.getString(10);

                    // FILTRO DE RUIDO Y AGRUPACIÓN
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("ip_ext", ipA);
                    m.put("usuario", user != null ? user : "Anonimo");
                    m.put("password", pass != null ? pass : "N/A");
                    m.put("ua", ua != null ? ua : "Unknown");
                    m.put("ubicacion", ubi != null ? ubi : "Unknown");
                    m.put("modelo", (mod != null && !mod.isEmpty()) ? mod : "Generic Device");
                    m.put("bateria", (bat != null && !bat.isEmpty()) ? bat : "N/A");

                    if (ipP != null && !ipP.contains("Detectando") && !ipP.contains("N/A") && !ipP.contains("Protegido")
                            && ipP.length() > 5) {
                        m.put("ip_privada", ipP);
                    }
                    m.put("biometria", (bio != null && bio.length() > 10) ? bio : "[]");

                    // Parsing de LAN Discovery
                    if (raw != null && raw.contains("\"mapeo_lan\":[")) {
                        try {
                            int start = raw.indexOf("\"mapeo_lan\":[") + 12;
                            int end = raw.indexOf("]", start) + 1;
                            if (end > start) {
                                m.put("lan", raw.substring(start, end));
                            }
                        } catch (Exception e) {
                        }
                    }

                    eliteData.add(m);
                    if (eliteData.size() >= 15)
                        break;
                }
            } catch (Exception e) {
                if (monitorConsole != null)
                    log("Fallo en Deep Trace: " + e.getMessage(), Color.YELLOW);
            }
            stats.put("elite_telemetry", eliteData);

            // 8. NGROK / TRAFFIC STATS
            List<Map<String, Object>> ngrokStats = new ArrayList<>();
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT ip_origen, pais, COUNT(*) as cnt, SUM(es_sospechoso) as sosp FROM logs_trafico_ngrok GROUP BY ip_origen, pais ORDER BY cnt DESC LIMIT 5");
                    java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("ip", rs.getString(1));
                    m.put("pais", rs.getString(2));
                    m.put("cnt", rs.getInt(3));
                    m.put("sosp", rs.getInt(4));
                    ngrokStats.add(m);
                }
            } catch (Exception e) {
            }
            stats.put("ngrok_stats", ngrokStats);

            if (monitorConsole != null)
                log("Inteligencia Forense Global Generada con Éxito.", Color.GREEN);

        } catch (Exception e) {
            log("Error consultando stats: " + e.getMessage(), Color.RED);
        }
        return stats;
    }

    private void btnMonitorRedActionPerformed(java.awt.event.ActionEvent evt) {
        navigateTo("monitor");
    }

    private void setTomcatUIState(boolean running) {
        isTomcatRunning = running;
        String hexBg = toHexString(
                UIManager.getColor("Button.background") != null ? UIManager.getColor("Button.background")
                        : new java.awt.Color(10, 25, 47));
        String hexAccent = toHexString(getThemeAccentColor());

        if (running) {
            btnLanzarTomcat.setText("STOP TOMCAT");
            btnLanzarTomcat.putClientProperty("FlatLaf.style",
                    "arc: 0; borderWidth: 1; background: " + hexBg
                            + "; borderColor: #FF4444; foreground: #FF4444; hoverForeground: #ffffff;"); // Cambiar a
                                                                                                         // Rojo Warning
        } else {
            btnLanzarTomcat.setText("START TOMCAT");
            btnLanzarTomcat.putClientProperty("FlatLaf.style",
                    "arc: 0; borderWidth: 1; background: " + hexBg + "; borderColor: " + hexAccent + "; foreground: "
                            + hexAccent + "; hoverForeground: #ffffff;"); // Volver a Acorde
        }
    }

    private void updateButtonSizes(float scale) {
        if (btnStart == null)
            return; // Guard para evitar NPE inicial

        // Actualizamos los tamaños de los botones centrales
        btnStart.setPreferredSize(new java.awt.Dimension((int) (160 * scale), (int) (80 * scale)));
        btnGraphics.setPreferredSize(new java.awt.Dimension((int) (210 * scale), (int) (60 * scale)));
        btnLogs.setPreferredSize(new java.awt.Dimension((int) (210 * scale), (int) (60 * scale)));

        btnFullscreen.setPreferredSize(new java.awt.Dimension((int) (180 * scale), (int) (50 * scale)));
        btnUiScale.setPreferredSize(new java.awt.Dimension((int) (180 * scale), (int) (50 * scale)));
        btnCtrFilter.setPreferredSize(new java.awt.Dimension((int) (180 * scale), (int) (50 * scale)));

        // Botón del footer
        if (btnFooterNav != null) {
            btnFooterNav.setPreferredSize(new java.awt.Dimension((int) (100 * scale), (int) (40 * scale)));
        }

        // Fuentes escaladas
        java.awt.Font smallFont = new java.awt.Font("Monospaced", 1, 14);
        btnGraphics.setFont(smallFont.deriveFont(16.0f * scale));
        btnLogs.setFont(smallFont.deriveFont(16.0f * scale));
        btnFullscreen.setFont(smallFont.deriveFont(16.0f * scale));
        btnUiScale.setFont(smallFont.deriveFont(16.0f * scale));
        btnCtrFilter.setFont(smallFont.deriveFont(16.0f * scale));
        btnLanzarTomcat.setFont(smallFont.deriveFont(16.0f * scale));

        if (btnFooterNav != null) {
            btnFooterNav.setFont(smallFont.deriveFont(14.0f * scale));
        }

        // El logo de START se maneja por su icono, pero aseguramos texto nulo
        btnStart.setText(null);

        panelInicio.revalidate();
        panelSettings.revalidate();
        contentArea.revalidate();
    }

    private void themeToggleActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_themeToggleActionPerformed
    {// GEN-HEADEREND:event_themeToggleActionPerformed
     // Ciclo de 3 estados: 0 -> 1 -> 2 -> 0
        currentThemeMode = (currentThemeMode + 1) % 3;
        switchTheme(currentThemeMode);
        saveConfig(); // Guardamos el cambio al instante
    }

    private void actualizarIndicadores(boolean activo) {
        java.awt.Color on = getThemeAccentColor();
        java.awt.Color off = new java.awt.Color(100, 100, 100); // Gris apagado

        if (activo) {
            lblStatusService.setForeground(on);
            lblStatusService.setText("● SERVICE: ACTIVE");
        } else {
            lblStatusService.setForeground(off);
            lblStatusService.setText("● SERVICE: INACTIVE");
        }
    }

    private void switchTheme(int mode) {
        FlatAnimatedLafChange.showSnapshot();

        java.awt.Color gradientStart;
        java.awt.Color gradientEnd;
        java.awt.Color gridColor;

        // Definimos la fuente uniforme estilo Hacker/Cyan C2
        java.awt.Font actionFont = new java.awt.Font("Monospaced", 1, 16);
        java.awt.Font startFont = new java.awt.Font("Monospaced", 1, 26);

        switch (mode) {
            case 0: // LIGHT MODE (Admin)
                FlatLightLaf.setup();
                gradientStart = new java.awt.Color(245, 245, 245);
                gradientEnd = new java.awt.Color(220, 230, 240);
                gridColor = new java.awt.Color(0, 0, 0, 10);
                break;
            case 1: // MID BLUE (Analysis)
                FlatLightLaf.setup();
                gradientStart = new java.awt.Color(230, 247, 255);
                gradientEnd = new java.awt.Color(186, 231, 255);
                gridColor = new java.awt.Color(0, 100, 255, 15);
                break;
            default: // DEEP SPACE (C2 Operations)
                FlatDarkLaf.setup();
                gradientStart = new java.awt.Color(2, 6, 23);
                gradientEnd = new java.awt.Color(10, 20, 45);
                gridColor = new java.awt.Color(0, 242, 255, 10);
                break;
        }

        java.awt.Color btnBg;
        java.awt.Color btnFg;
        java.awt.Color gColor;
        java.awt.Color lColor;
        String borderColor;
        FlatSVGIcon icon;

        if (mode == 0) {
            btnBg = new java.awt.Color(220, 230, 240);
            btnFg = new java.awt.Color(5, 25, 55); // Azul muy oscuro para contraste
            gColor = new java.awt.Color(0, 82, 204); // Azul fuerte (no amarillo)
            lColor = new java.awt.Color(0, 102, 51); // Verde oscuro
            borderColor = "#051937";
            icon = sunIcon;
        } else if (mode == 1) {
            btnBg = new java.awt.Color(186, 231, 255);
            btnFg = new java.awt.Color(0, 100, 200);
            gColor = new java.awt.Color(0, 70, 150);
            lColor = new java.awt.Color(0, 80, 40);
            borderColor = "#0050B3";
            icon = midIcon;
        } else {
            btnBg = new java.awt.Color(10, 25, 47);
            btnFg = new java.awt.Color(0, 242, 255);
            gColor = new java.awt.Color(255, 215, 0);
            lColor = new java.awt.Color(46, 204, 113);
            borderColor = "#00F2FF";
            icon = moonIcon;
        }

        // 1. Aplicar a las propiedades globales
        UIManager.put("Panel.background", gradientStart);
        UIManager.put("Button.background", btnBg);

        if (this.getContentPane() instanceof BackgroundPanel) {
            ((BackgroundPanel) this.getContentPane()).updateTheme(gradientStart, gradientEnd, gridColor);
        }

        // 1.1 Sincronizar logotipo de Hacker (opacidad segun tema)
        if (this.getContentPane() instanceof BackgroundPanel) {
            float alpha = (mode == 2) ? 0.15f : 0.08f;
            ((BackgroundPanel) this.getContentPane()).setWatermarkAlpha(alpha);
        }

        // 2. Forzar a los paneles a ser transparentes
        contentArea.setBackground(new java.awt.Color(0, 0, 0, 0));
        contentArea.setOpaque(false);
        panelInicio.setOpaque(false);
        panelSettings.setOpaque(false);
        panelLoading.setOpaque(false);
        panelMonitor.setOpaque(false);
        panelOperaciones.setOpaque(false);
        consoleContainer.setOpaque(false);
        footerPanel.setBackground(new java.awt.Color(0, 0, 0, 0));
        footerPanel.setOpaque(false);
        titleBar.setOpaque(false);
        titleBar.setBackground(new java.awt.Color(0, 0, 0, 0));
        jPanel1.setOpaque(false);
        jPanel1.setBackground(new java.awt.Color(0, 0, 0, 0));

        // 3. Actualizar el estilo de los botones principales y Titulo
        if (lblTitleIcon != null)
            lblTitleIcon.setForeground(btnFg);

        // Estilo especial para el botón START (siempre rojo pero adaptado al tema)
        String redBorder = (mode == 0) ? "#B22222" : (mode == 1) ? "#FF0000" : "#FF4444";
        java.awt.Color redBg = (mode == 0) ? new java.awt.Color(255, 235, 235)
                : (mode == 1) ? new java.awt.Color(40, 0, 0) : new java.awt.Color(20, 0, 0);

        startIcon.setColorFilter(
                new com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter(color -> java.awt.Color.decode(redBorder)));
        btnStart.setBackground(redBg);
        btnStart.setForeground(java.awt.Color.decode(redBorder));
        btnStart.setFont(startFont);
        btnStart.setText(null);
        btnStart.putClientProperty("FlatLaf.style", "arc: 0; borderWidth: 2; background: " + toHexString(redBg)
                + "; borderColor: " + redBorder + "; foreground: " + redBorder
                + "; hoverForeground: #ffffff; hoverBackground: " + redBorder + ";");

        String hexBgAtTop = toHexString(btnBg);
        btnGraphics.setForeground(gColor);
        btnGraphics.setBackground(btnBg);
        btnGraphics.setFont(actionFont);
        btnGraphics.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: " + hexBgAtTop + "; borderColor: " + toHexString(gColor)
                        + "; foreground: " + toHexString(gColor) + "; hoverForeground: #ffffff;");

        btnLogs.setForeground(lColor);
        btnLogs.setBackground(btnBg);
        btnLogs.setFont(actionFont);
        btnLogs.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: " + hexBgAtTop + "; borderColor: " + toHexString(lColor)
                        + "; foreground: " + toHexString(lColor) + "; hoverForeground: #ffffff;");

        btnLanzarTomcat.setForeground(lColor);
        btnLanzarTomcat.setBackground(btnBg);
        btnLanzarTomcat.setFont(actionFont);
        btnLanzarTomcat.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: " + hexBgAtTop + "; borderColor: " + toHexString(lColor)
                        + "; foreground: " + toHexString(lColor) + "; hoverForeground: #ffffff;");

        themeToggle.setIcon(icon);

        // 4. Actualizar el estilo de TODOS los demás botones
        String hexBg = toHexString(btnBg);
        String hexFg = toHexString(btnFg);
        String hexBorder = borderColor;

        applyButtonStyle(btnFullscreen, hexBg, hexFg, hexBorder, actionFont);
        applyButtonStyle(btnUiScale, hexBg, hexFg, hexBorder, actionFont);
        applyButtonStyle(btnCtrFilter, hexBg, hexFg, hexBorder, actionFont);
        applyButtonStyle(btnFooterNav, hexBg, hexFg, hexBorder, actionFont);

        if (btnLanzarMysql != null) {
            applyButtonStyle(btnLanzarMysql, hexBg, hexFg, hexBorder, actionFont);
        }

        // Actualizamos nuestros botones inyectados en runtime!
        java.awt.Font smallerActionFont = new java.awt.Font("Monospaced", 1, 13);
        if (btnTanner != null) {
            applyButtonStyle(btnTanner, hexBg, hexFg, toHexString(gColor), smallerActionFont);
            applyButtonStyle(btnBots, hexBg, hexFg, "#FF4444", actionFont);
            applyButtonStyle(btnInforme, hexBg, hexFg, toHexString(lColor), actionFont);
            applyButtonStyle(btnMonitorRed, hexBg, hexFg, hexBorder, actionFont);
            applyButtonStyle(btnNgrok, hexBg, hexFg, hexBorder, actionFont);
        }

        // 5. Actualizar labels de estado y feed dinámico (LEGIBILIDAD)
        Color accent = getThemeAccentColor();
        if (lblTicker != null)
            lblTicker.setForeground(mode == 0 ? accent : accent.darker());
        if (lblStatusService != null)
            lblStatusService.setForeground(accent);
        if (lblStatusPort != null)
            lblStatusPort.setForeground(accent);
        if (lblStatusMysql != null)
            lblStatusMysql.setForeground(accent);
        if (lblStatusNgrok != null)
            lblStatusNgrok.setForeground(accent);
        if (lblStatusDb != null)
            lblStatusDb.setForeground(accent);

        // Sincronizar borde del feed con el nuevo acento
        if (jPanel1 != null) {
            for (java.awt.Component c : jPanel1.getComponents()) {
                if (c instanceof javax.swing.JScrollPane) {
                    ((javax.swing.JScrollPane) c).setBorder(javax.swing.BorderFactory
                            .createLineBorder(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40), 1));
                }
            }
        }

        // 6. Sincronizar Paneles Especializados (Gráficos, Tablas, Feeds)
        if (threatMonitor != null)
            threatMonitor.updatePanelTheme();
        if (geoMap != null)
            geoMap.updatePanelTheme();
        if (liveActivityTicker != null)
            liveActivityTicker.updatePanelTheme();

        // 7. Legibilidad de Indicadores de Estado
        actualizarIndicadores(isTomcatRunning); // Recalibramos colores de los LEDs (ONLINE/OFFLINE) con el nuevo tema

        FlatLaf.updateUI();
        // 4. Asegurar que el modo de alerta (Slider) se mantenga sincronizado tras el
        // cambio de tema
        if (sliderBrightness != null) {
            applySystemAlertMode(sliderBrightness.getValue());
        }

        FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }

    private void applyButtonStyle(javax.swing.JButton btn, String bg, String fg, String border, java.awt.Font font) {
        if (btn == null)
            return;
        btn.setBackground(java.awt.Color.decode(bg));
        btn.setForeground(java.awt.Color.decode(fg));
        btn.setFont(font);
        btn.putClientProperty("FlatLaf.style", "arc: 0; borderWidth: 1; background: " + bg + "; borderColor: " + border
                + "; foreground: " + border + "; hoverForeground: #ffffff;");
    }

    private void updateFooterNav(String text, java.awt.event.ActionListener listener) {
        // Limpiar listeners anteriores
        for (java.awt.event.ActionListener al : btnFooterNav.getActionListeners()) {
            btnFooterNav.removeActionListener(al);
        }
        btnFooterNav.setText(text);
        if (listener != null) {
            btnFooterNav.addActionListener(listener);
        }

    }

    private String toHexString(java.awt.Color color) {
        return String.format("#%06x", (color.getRGB() & 0x00FFFFFF));
    }

    private void titleBarMousePressed(java.awt.event.MouseEvent evt)// GEN-FIRST:event_titleBarMousePressed
    {// GEN-HEADEREND:event_titleBarMousePressed
        xMouse = evt.getX();
        yMouse = evt.getY();
    }

    private void spawnGhostToast(String message) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            GhostToast toast = new GhostToast(this, message);
            activeToasts.add(toast);
            repositionToasts();
        });
    }

    private void repositionToasts() {
        int y = 50; // Margen superior
        for (int i = activeToasts.size() - 1; i >= 0; i--) {
            GhostToast t = activeToasts.get(i);
            t.setLocation((this.getWidth() - t.getWidth()) / 2 + this.getX(), this.getY() + y);
            y += 45; // Espaciado vertical
        }
    }

    private class GhostToast extends javax.swing.JWindow {
        private float opacity = 1.0f;
        private Timer fadeTimer;

        public GhostToast(JFrame parent, String msg) {
            super(parent);
            setBackground(new Color(0, 0, 0, 0));

            JPanel panel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));

                    // Fondo tipo Glossy/Glass
                    GradientPaint grad = new GradientPaint(0, 0, new Color(10, 25, 47, 200), 0, getHeight(),
                            new Color(2, 6, 23, 230));
                    g2.setPaint(grad);
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 15, 15));

                    // Borde Cyan Neón
                    g2.setColor(new Color(0, 242, 255, (int) (opacity * 255)));
                    g2.setStroke(new java.awt.BasicStroke(1.5f));
                    g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 15, 15));

                    g2.dispose();
                }
            };
            panel.setLayout(new BorderLayout());
            panel.setOpaque(false);

            JLabel lbl = new JLabel(msg, JLabel.CENTER);
            lbl.setForeground(new Color(0, 242, 255));
            lbl.setFont(new java.awt.Font("Monospaced", Font.BOLD, 12));
            lbl.setBorder(BorderFactory.createEmptyBorder(10, 25, 10, 25));
            panel.add(lbl, BorderLayout.CENTER);

            add(panel);
            pack();

            // Animación de flotado y desaparición
            fadeTimer = new Timer(30, e -> {
                opacity -= 0.02f;
                if (opacity <= 0) {
                    fadeTimer.stop();
                    activeToasts.remove(this);
                    dispose();
                    repositionToasts();
                }
                repaint();
            });
            fadeTimer.setInitialDelay(2000); // Se queda 2s antes de desvanecer
            fadeTimer.start();
            setVisible(true);
        }
    }

    private void titleBarMouseDragged(java.awt.event.MouseEvent evt)// GEN-FIRST:event_titleBarMouseDragged
    {// GEN-HEADEREND:event_titleBarMouseDragged
        int x = evt.getXOnScreen();
        int y = evt.getYOnScreen();
        this.setLocation(x - xMouse, y - yMouse);
    }// GEN-LAST:event_titleBarMouseDragged

    private void btnMinimizeActionPerformed(java.awt.event.ActionEvent evt) {
        this.setState(java.awt.Frame.ICONIFIED);
    }

    private void lanzarMysql() {
        log("[SENTINEL] Ejecutando lanzarMysql()...", Color.WHITE);
        if (isMysqlRunning)
            return;

        try {
            if (detectedOS.equals("WINDOWS")) {
                if (!isAdmin) {
                    int opt = javax.swing.JOptionPane.showConfirmDialog(this,
                            "⚠️ SENTINEL: Iniciar servicios de sistema requiere privilegios de ADMINISTRADOR.\n¿Deseas intentar elevar privilegios ahora?",
                            "ELEVACIÓN REQUERIDA", javax.swing.JOptionPane.YES_NO_OPTION);
                    if (opt == javax.swing.JOptionPane.YES_OPTION) {
                        requestAdminElevation();
                    }
                    return;
                }

                log("[SENTINEL] Intentando iniciar servicio MySQL (Windows)...", Color.CYAN);
                new Thread(() -> {
                    try {
                        Process p = Runtime.getRuntime().exec("cmd /c net start MySQL80");
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getInputStream()));
                        java.io.BufferedReader err = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getErrorStream()));
                        String line;
                        while ((line = br.readLine()) != null)
                            log("[MYSQL] " + line, Color.CYAN);
                        while ((line = err.readLine()) != null)
                            log("[MYSQL-ERR] " + line, Color.RED);
                        int exitCode = p.waitFor();
                        if (exitCode != 0) {
                            log("[MYSQL] Fallo al iniciar, probando nombre alternativo 'mysql'...",
                                    Color.ORANGE);
                            Process p2 = Runtime.getRuntime().exec("cmd /c net start mysql");
                            java.io.BufferedReader br2 = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(p2.getInputStream()));
                            java.io.BufferedReader err2 = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(p2.getErrorStream()));
                            while ((line = br2.readLine()) != null)
                                log("[MYSQL] " + line, Color.CYAN);
                            while ((line = err2.readLine()) != null)
                                log("[MYSQL-ERR] " + line, Color.RED);
                            p2.waitFor();
                        }
                    } catch (Exception e) {
                        log("[ERROR] Thread MySQL Start: " + e.getMessage(), Color.RED);
                    }
                }).start();

            } else {
                log(
                        "[SENTINEL] Por favor, asegúrate de que MySQL esté corriendo (sudo systemctl start mysql).",
                        Color.ORANGE);
            }
        } catch (Exception e) {
            log("[ERROR] Error al arrancar MySQL: " + e.getMessage(), Color.RED);
        }
    }

    private void detenerMysql() {
        log("[SENTINEL] Intentando detener MySQL...", Color.ORANGE);
        try {
            if (detectedOS.equals("WINDOWS")) {
                if (!isAdmin) {
                    int opt = javax.swing.JOptionPane.showConfirmDialog(this,
                            "⚠️ SENTINEL: Detener servicios de sistema requiere privilegios de ADMINISTRADOR.\n¿Deseas intentar elevar privilegios ahora?",
                            "ELEVACIÓN REQUERIDA", javax.swing.JOptionPane.YES_NO_OPTION);
                    if (opt == javax.swing.JOptionPane.YES_OPTION) {
                        requestAdminElevation();
                    }
                    return;
                }
                new Thread(() -> {
                    try {
                        Process p = Runtime.getRuntime().exec("cmd /c net stop MySQL80");
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getInputStream()));
                        java.io.BufferedReader err = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getErrorStream()));
                        String line;
                        while ((line = br.readLine()) != null)
                            log("[MYSQL] " + line, Color.CYAN);
                        while ((line = err.readLine()) != null)
                            log("[MYSQL-ERR] " + line, Color.RED);
                        int exitCode = p.waitFor();
                        if (exitCode != 0) {
                            Process p2 = Runtime.getRuntime().exec("cmd /c net stop mysql");
                            java.io.BufferedReader br2 = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(p2.getInputStream()));
                            java.io.BufferedReader err2 = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(p2.getErrorStream()));
                            while ((line = br2.readLine()) != null)
                                log("[MYSQL] " + line, Color.CYAN);
                            while ((line = err2.readLine()) != null)
                                log("[MYSQL-ERR] " + line, Color.RED);
                            p2.waitFor();
                        }
                        log("[SENTINEL] Comando enviado: 'net stop'", Color.CYAN);
                    } catch (Exception e) {
                        log("[ERROR] Thread MySQL Stop: " + e.getMessage(), Color.RED);
                    }
                }).start();
            } else {
                log("[SENTINEL] Por favor, detén MySQL manualmente (sudo systemctl stop mysql).",
                        Color.ORANGE);
            }
        } catch (Exception ex) {
            log("[ERROR] " + ex.getMessage(), Color.RED);
        }
    }

    private void btnMaximizeActionPerformed(java.awt.event.ActionEvent evt) {
        if (this.getExtendedState() == java.awt.Frame.MAXIMIZED_BOTH) {
            this.setExtendedState(java.awt.Frame.NORMAL);
        } else {
            this.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
        }
    }

    private void btnHoneypotSuiteActionPerformed(java.awt.event.ActionEvent evt) {
        checkAndInstallParamikoThenLaunch();
    }

    private void checkAndInstallParamikoThenLaunch() {
        new Thread(() -> {
            try {
                log("Verificando integridad de la suite Sentinel...", Color.GRAY);
                String pythonCmd = isPythonReady ? (detectedOS.equals("WINDOWS") ? "python" : "python3") : "python";

                // Intentamos importar paramiko para ver si está
                Process p = Runtime.getRuntime().exec(new String[] { pythonCmd, "-c", "import paramiko" });
                if (p.waitFor() != 0) {
                    log("[SENTINEL] Instalando dependencia Cowrie (Paramiko)...", Color.CYAN);
                    Runtime.getRuntime().exec(new String[] { "pip", "install", "paramiko", "--user" }).waitFor();
                }

                launchHoneypotSuite();

            } catch (Exception e) {
                log("[ERROR] No se pudo preparar la suite: " + e.getMessage(), Color.RED);
            }
        }).start();
    }

    private void btnCloseActionPerformed(java.awt.event.ActionEvent evt) {
        if (!isUndecorated()) {
            System.exit(0);

            return;
        }
        startExitAnimation();
    }

    /**
     * Limpia cualquier proceso que esté usando un puerto específico (Zombie
     * prevention).
     */
    private void killProcessOnPort(int port) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Windows: netstat -ano | findstr :PORT
                Process p = Runtime.getRuntime().exec("cmd /c netstat -ano | findstr :" + port);
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("LISTENING")) {
                            String[] parts = line.trim().split("\\s+");
                            String pid = parts[parts.length - 1];
                            Runtime.getRuntime().exec("taskkill /F /PID " + pid + " /T");
                            System.out.println("[SENTINEL] Zombie eliminado en puerto " + port + " (PID: " + pid + ")");
                        }
                    }
                }
            } else {
                // Unix: fuser -k port/tcp
                Runtime.getRuntime().exec("fuser -k " + port + "/tcp");
            }
        } catch (Exception e) {
        }
    }

    private void startNetworkMonitor() {
        new Thread(() -> {
            try {
                // AUTO-LIMPIEZA DE PUERTOS (Fase 7)
                killProcessOnPort(3001);

                log("[SENTINEL] Iniciando Monitor de Red (Node.js Engine)...", Color.CYAN);

                String nodeBin = findNodeBinary();
                if (nodeBin == null || nodeBin.isEmpty()) {
                    log("[ERROR] No se encontró el binario de Node.js en el sistema.", Color.RED);
                    return;
                }

                // Ruta relativa al monitor (ajustada para portabilidad)
                File monitorDir = new File("../../Monitor_red");
                if (!monitorDir.exists()) {
                    monitorDir = new File("Monitor_red");
                }

                if (!monitorDir.exists()) {
                    log("[ERROR] Directorio 'Monitor_red' no encontrado.", Color.RED);
                    return;
                }

                File scriptFile = new File(monitorDir, "log_monitor.js");
                if (!scriptFile.exists()) {
                    log("[ERROR] No se encontró log_monitor.js en " + monitorDir.getAbsolutePath(), Color.RED);
                    return;
                }

                ProcessBuilder pb = new ProcessBuilder(nodeBin, "log_monitor.js");
                pb.directory(monitorDir);
                pb.redirectErrorStream(true);
                
                // Forzar salida sin buffer para recibir logs en tiempo real
                pb.environment().put("NODE_DISABLE_COLORS", "1");

                networkMonitorProcess = pb.start();
                log("[SENTINEL] Monitor Activo (PID: " + networkMonitorProcess.pid() + ")", Color.GREEN);

                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(networkMonitorProcess.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String logLine = line;
                        System.out.println("[RED_STDOUT] " + logLine); // Esencial para depuración en IDE

                        // 1. Log en las consolas sincronizadas
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            Color c = Color.WHITE;
                            if (logLine.contains("[ALERT]"))
                                c = Color.YELLOW;
                            if (logLine.contains("[SCAN]"))
                                c = Color.RED;
                            if (logLine.contains("[OK]"))
                                c = new Color(0, 242, 255);
                            log("[RED] " + logLine, c);
                        });

                        // 2. Procesamiento de Eventos Críticos (Notificaciones)
                        if (logLine.contains("[ALERT]") || logLine.contains("[SCAN]")) {
                            String msg = logLine.substring(logLine.indexOf("]") + 1).trim();
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                showMapToast(msg, logLine.contains("[SCAN]") ? "ALTA" : "MEDIA");
                            });
                        }

                        // 3. Ticker de Actividad (Option A)
                        if (logLine.contains("[OK]") || logLine.contains("[ALERT]") || logLine.contains("[SCAN]")) {
                            // Extraer parte relevante: IP (PAIS) -> URL
                            String tickerMsg = logLine.trim();
                            if (tickerMsg.contains("🟢"))
                                tickerMsg = tickerMsg.substring(tickerMsg.indexOf("🟢") + 1).trim();
                            if (tickerMsg.contains("🔴"))
                                tickerMsg = tickerMsg.substring(tickerMsg.indexOf("🔴") + 1).trim();
                            if (tickerMsg.contains("🟡"))
                                tickerMsg = tickerMsg.substring(tickerMsg.indexOf("🟡") + 1).trim();

                            final String finalMsg = tickerMsg;
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                if (liveActivityTicker != null)
                                    liveActivityTicker.logActivity(finalMsg);
                            });

                            // 4. Refresco de Tablas y Gráficos
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                if (geoMap != null)
                                    geoMap.fetchDataFromMySQL();
                                if (threatMonitor != null)
                                    threatMonitor.fetchDataFromMySQL();

                                // GHOST TOAST: Notificación anónima
                                if (logLine.contains("[OK]")) {
                                    spawnGhostToast("NUEVA CONEXIÓN #" + (++connectionCounter));
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {
                log("[ERROR RED] Fallo al iniciar monitor: " + e.getMessage(), Color.RED);
            }
        }).start();
    }

    private void stopNetworkMonitor() {
        if (networkMonitorProcess != null) {
            // Matar recursivamente (Node puede levantar subprocesos)
            java.util.Optional<java.lang.ProcessHandle> handle = java.lang.ProcessHandle
                    .of(networkMonitorProcess.pid());
            handle.ifPresent(h -> h.descendants().forEach(java.lang.ProcessHandle::destroyForcibly));
            networkMonitorProcess.destroyForcibly();

            log("[SENTINEL] Monitor de Red detenido.", Color.WHITE);
        }
    }

    private void saveConfig() {
        java.util.Properties props = new java.util.Properties();
        try (java.io.OutputStream out = new java.io.FileOutputStream("config.properties")) {
            props.setProperty("uiScale", String.valueOf(uiScaleLevel));
            props.setProperty("theme", String.valueOf(currentThemeMode));
            props.setProperty("crtActive", String.valueOf(isCrtActivate));
            props.setProperty("groqApiKey", groqApiKey != null ? groqApiKey : "");
            if (sliderBrightness != null) {
                props.setProperty("alertMode", String.valueOf(sliderBrightness.getValue()));
            }
            props.store(out, "Sentinel C2 - UserSettings");
        } catch (java.io.IOException e) {
            logger.log(Level.SEVERE, "Error al guardar configuración", e);
        }
    }

    private void loadConfig() {
        // Prioridad: Variable de entorno GROQ_API_KEY
        String envGroqKey = System.getenv("GROQ_API_KEY");
        if (envGroqKey != null && !envGroqKey.trim().isEmpty()) {
            groqApiKey = envGroqKey.trim();
        }

        java.io.File configFile = new java.io.File("config.properties");
        if (!configFile.exists())
            return;

        java.util.Properties props = new java.util.Properties();
        try (java.io.InputStream in = new java.io.FileInputStream(configFile)) {
            props.load(in);

            // Cargamos la escala
            String scaleStr = props.getProperty("uiScale", "1");
            uiScaleLevel = Integer.parseInt(scaleStr);

            // Cargamos el tema
            String themeStr = props.getProperty("theme", "2");
            currentThemeMode = Integer.parseInt(themeStr);

            // Cargamos el estado del CRT
            String crtStr = props.getProperty("crtActive", "false");
            isCrtActivate = Boolean.parseBoolean(crtStr);

            // Cargamos la API Key de Groq si no vino ya asignada desde variable de entorno
            if (groqApiKey == null || groqApiKey.trim().isEmpty()) {
                groqApiKey = props.getProperty("groqApiKey", "");
            }

            // Cargamos el modo de alerta
            String alertStr = props.getProperty("alertMode", "1");
            int alertVal = Integer.parseInt(alertStr);
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (sliderBrightness != null) {
                    sliderBrightness.setValue(alertVal);
                    applySystemAlertMode(alertVal);
                }
            });
        } catch (Exception e) {
            logger.info("Error al cargar configuración, usando valores por defecto: " + e.getMessage());
        }
    }

    /**
     * Sobrescribimos el cierre de ventana para aplicar tu animación de fade-out
     */
    @Override
    protected void processWindowEvent(java.awt.event.WindowEvent e) {
        if (e.getID() == java.awt.event.WindowEvent.WINDOW_CLOSING) {
            startExitAnimation();
        } else {
            super.processWindowEvent(e);
        }
    }

    private void showNgrokDialog() {
        javax.swing.JDialog dialog = new javax.swing.JDialog(this, "SENTINEL - NGROK REGISTRATION", true);
        dialog.setSize(440, 640);
        dialog.setUndecorated(true);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(new Color(10, 25, 47));
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(getThemeAccentColor(), 2));

        JPanel main = new JPanel();
        main.setLayout(new javax.swing.BoxLayout(main, javax.swing.BoxLayout.Y_AXIS));
        main.setOpaque(false);
        main.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        javax.swing.JLabel lblT = new javax.swing.JLabel("VINCULACIÓN DE TÚNEL C2");
        lblT.setForeground(getThemeAccentColor());
        lblT.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 20));
        lblT.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        main.add(lblT);
        main.add(javax.swing.Box.createRigidArea(new Dimension(0, 10)));

        javax.swing.JLabel lblSub = new javax.swing.JLabel("Operación táctica de red");
        lblSub.setForeground(new Color(100, 100, 100));
        lblSub.setFont(new java.awt.Font("Monospaced", java.awt.Font.ITALIC, 11));
        lblSub.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        main.add(lblSub);
        main.add(javax.swing.Box.createRigidArea(new Dimension(0, 20)));

        javax.swing.JTextArea txtInstr = new javax.swing.JTextArea(
                "⚠️ AUTHENTICATION REQUIRED:\n" +
                        "Localiza la URL de 'Forwarding' en tu terminal activa de NGROK \n" +
                        "(Copia desde consola con CTRL+SHIFT+C).\n\n" +
                        "Copia y pega el endpoint público abajo.\n" +
                        "[Bypass local Loopback 127.0.0.1/localhost]");
        txtInstr.setEditable(false);
        txtInstr.setOpaque(false);
        txtInstr.setForeground(new Color(200, 200, 200));
        txtInstr.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 11));
        txtInstr.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        txtInstr.setLineWrap(true);
        txtInstr.setWrapStyleWord(true);
        main.add(txtInstr);
        main.add(javax.swing.Box.createRigidArea(new Dimension(0, 20)));

        javax.swing.JTextField txtManualInput = new javax.swing.JTextField();
        txtManualInput.setMaximumSize(new java.awt.Dimension(380, 40));
        txtManualInput.setBackground(new Color(15, 35, 55));
        txtManualInput.setForeground(new Color(0, 242, 255));
        txtManualInput.setCaretColor(Color.CYAN);
        txtManualInput.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        txtManualInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 150, 150), 1),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        main.add(txtManualInput);
        main.add(javax.swing.Box.createRigidArea(new Dimension(0, 15)));

        javax.swing.JButton btnVincular = new javax.swing.JButton("GENERAR ACCESO SEGURO");
        btnVincular.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        btnVincular.setMaximumSize(new java.awt.Dimension(380, 40));
        btnVincular.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 12));
        btnVincular.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: #00000000; borderColor: #FFD700; foreground: #FFD700; hoverForeground: #ffffff;");
        main.add(btnVincular);
        main.add(javax.swing.Box.createRigidArea(new Dimension(0, 15)));

        javax.swing.JButton btnPresentar = new javax.swing.JButton("PANTALLA COMPLETA LINK");
        btnPresentar.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        btnPresentar.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 12));
        btnPresentar.setForeground(new Color(255, 215, 0));
        btnPresentar.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: #00000000; borderColor: #FFD700; foreground: #FFD700; hoverForeground: #ffffff;");
        btnPresentar.setVisible(false); // Solo visible tras generar link
        main.add(btnPresentar);

        main.add(javax.swing.Box.createRigidArea(new Dimension(0, 20)));

        javax.swing.JLabel lblQR = new javax.swing.JLabel();
        lblQR.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        lblQR.setPreferredSize(new Dimension(200, 200));
        main.add(lblQR);
        main.add(javax.swing.Box.createRigidArea(new Dimension(0, 15)));

        // Link Interactivo
        javax.swing.JLabel lblShortLink = new javax.swing.JLabel(
                "<html><center>VINCULACIÓN PENDIENTE...</center></html>", javax.swing.JLabel.CENTER);
        lblShortLink.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        lblShortLink.setForeground(new Color(150, 150, 150));
        lblShortLink.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        lblShortLink.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        lblShortLink.setPreferredSize(new Dimension(380, 50));
        lblShortLink.setMinimumSize(new Dimension(380, 50));
        main.add(lblShortLink);

        btnVincular.addActionListener(e -> {
            String input = txtManualInput.getText().trim();
            if (input.startsWith("http")) {
                actualizarQRyLink(input, lblQR, lblShortLink, btnPresentar);
            } else {
                showMapToast("URL INVÁLIDA", "ALTA");
            }
        });

        javax.swing.JButton btnClose = new javax.swing.JButton("CERRAR PANEL");
        btnClose.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: #00000000; borderColor: #FF4444; foreground: #FF4444; hoverForeground: #ffffff;");
        btnClose.addActionListener(e -> dialog.dispose());

        JPanel pnlS = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER));
        pnlS.setOpaque(false);
        pnlS.add(btnClose);

        dialog.add(main, BorderLayout.CENTER);
        dialog.add(pnlS, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void actualizarQRyLink(String rawUrl, javax.swing.JLabel lblQR, javax.swing.JLabel lblShortLink,
            javax.swing.JButton btnPresentar) {
        // Asegurar que termina en /LoginWeb
        final String finalTargetUrl = rawUrl.endsWith("/LoginWeb") ? rawUrl
                : rawUrl + (rawUrl.endsWith("/") ? "LoginWeb" : "/LoginWeb");

        new javax.swing.SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() throws Exception {
                String shortUrl = finalTargetUrl;
                try {
                    // Intento 1: TinyURL (HTTPS)
                    java.net.URL api = java.net.URI.create(
                            "https://tinyurl.com/api-create?url=" + java.net.URLEncoder.encode(finalTargetUrl, "UTF-8"))
                            .toURL();
                    java.io.BufferedReader in = new java.io.BufferedReader(
                            new java.io.InputStreamReader(api.openStream()));
                    String res = in.readLine();
                    if (res != null && (res.contains("tinyurl.com") || res.contains("is.gd"))) {
                        shortUrl = res;
                    } else {
                        // Fallback: is.gd (Es más robusto y rápido)
                        java.net.URL api2 = java.net.URI.create("https://is.gd/create.php?format=simple&url="
                                + java.net.URLEncoder.encode(finalTargetUrl, "UTF-8")).toURL();
                        java.io.BufferedReader in2 = new java.io.BufferedReader(
                                new java.io.InputStreamReader(api2.openStream()));
                        shortUrl = in2.readLine();
                    }
                } catch (Exception ex) {
                    log("[SENTINEL] Error acortador: " + ex.getMessage(), java.awt.Color.ORANGE);
                }
                return new String[] { finalTargetUrl, shortUrl };
            }

            @Override
            protected void done() {
                try {
                    String[] res = get();
                    // Simplificamos el HTML para asegurar renderizado
                    lblShortLink.setText("<html><center>URL (Click): " + res[1] + "</center></html>");
                    lblShortLink.setForeground(new Color(0, 242, 255));

                    log("[SENTINEL] Link acortado generado: " + res[1], java.awt.Color.CYAN);

                    // Activar botón de presentación si se acortó con éxito
                    if (btnPresentar != null && !res[1].equals(finalTargetUrl)) {
                        btnPresentar.setVisible(true);
                        for (java.awt.event.ActionListener al : btnPresentar.getActionListeners())
                            btnPresentar.removeActionListener(al);
                        btnPresentar.addActionListener(e -> showPresentationDialog(res[1]));
                    } else if (btnPresentar != null) {
                        // Mostrarlo aunque no se acorte? El usuario dijo "cuando se acorte",
                        // pero si falla, al menos el largo sirve. Lo activamos igual para no fallar en
                        // la pizarra.
                        btnPresentar.setVisible(true);
                        for (java.awt.event.ActionListener al : btnPresentar.getActionListeners())
                            btnPresentar.removeActionListener(al);
                        btnPresentar.addActionListener(e -> showPresentationDialog(res[1]));
                    }

                    // Comportamiento interactivo
                    for (java.awt.event.MouseListener ml : lblShortLink.getMouseListeners())
                        lblShortLink.removeMouseListener(ml);

                    lblShortLink.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override
                        public void mouseEntered(java.awt.event.MouseEvent e) {
                            lblShortLink.setFont(lblShortLink.getFont().deriveFont(java.awt.Font.BOLD));
                            lblShortLink.setText("<html><center><u>URL: " + res[1] + "</u></center></html>");
                        }

                        @Override
                        public void mouseExited(java.awt.event.MouseEvent e) {
                            lblShortLink.setFont(lblShortLink.getFont().deriveFont(java.awt.Font.PLAIN));
                            lblShortLink.setText("<html><center>URL (Click): " + res[1] + "</center></html>");
                        }

                        @Override
                        public void mouseClicked(java.awt.event.MouseEvent e) {
                            try {
                                java.awt.Desktop.getDesktop().browse(new java.net.URI(res[1]));
                            } catch (Exception ex) {
                            }
                        }
                    });

                    String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data="
                            + java.net.URLEncoder.encode(res[0], "UTF-8");
                    lblQR.setIcon(new javax.swing.ImageIcon(java.net.URI.create(qrUrl).toURL()));
                    log("[NGROK] Túnel vinculado: " + res[0], java.awt.Color.GREEN);

                    // Forzar dibujado del panel
                    javax.swing.SwingUtilities.getWindowAncestor(lblShortLink).revalidate();
                    javax.swing.SwingUtilities.getWindowAncestor(lblShortLink).repaint();
                } catch (Exception ex) {
                    log("[ERROR] UI Update fail: " + ex.getMessage(), java.awt.Color.RED);
                }
            }
        }.execute();
    }

    private void showPresentationDialog(String url) {
        javax.swing.JDialog dialog = new javax.swing.JDialog(this, "MODO PRESENTACIÓN - SENTINEL C2", true);
        dialog.setSize(800, 300);
        dialog.setUndecorated(true);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(new Color(2, 6, 23));

        javax.swing.JPanel pnl = new javax.swing.JPanel(new java.awt.BorderLayout(20, 20));
        pnl.setOpaque(false);
        pnl.setBorder(javax.swing.BorderFactory.createLineBorder(new Color(0, 242, 255), 3));

        javax.swing.JLabel lblHeader = new javax.swing.JLabel("SENTINEL C2 | ENLACE DE ACCESO PÚBLICO",
                javax.swing.JLabel.CENTER);
        lblHeader.setForeground(new Color(255, 215, 0));
        lblHeader.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 18));
        lblHeader.setBorder(javax.swing.BorderFactory.createEmptyBorder(20, 0, 0, 0));

        javax.swing.JLabel lblLink = new javax.swing.JLabel("<html><center>" + url + "</center></html>",
                javax.swing.JLabel.CENTER);
        lblLink.setForeground(new Color(0, 242, 255));
        lblLink.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 42)); // TAMAÑO GIGANTE
        lblLink.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        lblLink.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                lblLink.setText("<html><center><u>" + url + "</u></center></html>");
                lblLink.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                lblLink.setText("<html><center>" + url + "</center></html>");
                lblLink.setForeground(new Color(0, 242, 255));
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                } catch (Exception ex) {
                }
            }
        });

        javax.swing.JButton btnClose = new javax.swing.JButton("SALIR DE PRESENTACIÓN");
        btnClose.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 14));
        btnClose.setBackground(new Color(150, 0, 0));
        btnClose.setForeground(Color.WHITE);
        btnClose.addActionListener(e -> dialog.dispose());

        pnl.add(lblHeader, java.awt.BorderLayout.NORTH);
        pnl.add(lblLink, java.awt.BorderLayout.CENTER);
        pnl.add(btnClose, java.awt.BorderLayout.SOUTH);

        dialog.add(pnl);
        dialog.setVisible(true);
    }

    private void startExitAnimation() {
        // Si estamos en FullScreen o no somos undecorated (transparencia no soportada
        // en algunos estados)
        // cerramos inmediatamente para evitar errores visuales o bloqueos
        java.awt.GraphicsDevice gd = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();
        if (!isUndecorated() || gd.getFullScreenWindow() == this) {
            System.exit(0);
            return;
        }

        javax.swing.Timer timer = new javax.swing.Timer(10, null);
        timer.addActionListener(new java.awt.event.ActionListener() {
            float opacity = 1.0f;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                opacity -= 0.1f;
                if (opacity <= 0.05f) {
                    timer.stop();
                    System.exit(0);
                } else {
                    try {
                        setOpacity(opacity);
                    } catch (Exception ex) {
                        System.exit(0); // Fallback si falla el ajuste de opacidad
                    }
                }
            }
        });
        timer.start();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        // 1. Activar decoraciones de ventana personalizadas de FlatLaf
        JFrame.setDefaultLookAndFeelDecorated(true);

        try {
            // Inicialización limpia
            FlatDarkLaf.setup();
            UIManager.put("TitlePane.unifiedBackground", true);
            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 12);
            UIManager.put("TextComponent.arc", 12);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Failed to initialize FlatLaf", ex);
        }

        java.awt.EventQueue.invokeLater(() -> {
            io.sentinel.command.sentinelc2.LoginDialog login = new io.sentinel.command.sentinelc2.LoginDialog(null);
            login.setVisible(true);

            if (login.isLoginExitoso()) {
                MainFrame frame = new MainFrame();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }
    // </editor-fold>

    private void setupHackerBranding() {
        try {
            java.net.URL imgURL = getClass().getResource("/icons/hacker_logo.png");
            if (imgURL == null)
                return;

            ImageIcon icon = new ImageIcon(imgURL);

            // 1. Logo en Splash (panelLoading)
            Image imgSplash = icon.getImage().getScaledInstance(200, 200, Image.SCALE_SMOOTH);
            JLabel lblHackerSplash = new JLabel(new ImageIcon(imgSplash));
            java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.insets = new java.awt.Insets(20, 0, 0, 0);
            panelLoading.add(lblHackerSplash, gbc);

            // 1.5 Logo en Barra de Titulo (Pequeno)
            Image imgTitle = icon.getImage().getScaledInstance(22, 22, Image.SCALE_SMOOTH);
            if (lblTitleIcon != null) {
                lblTitleIcon.setIcon(new ImageIcon(imgTitle));
                lblTitleIcon.setText(" SENTINEL C2");
                lblTitleIcon.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 14));
                lblTitleIcon.setForeground(new java.awt.Color(0, 242, 255));
                lblTitleIcon.setPreferredSize(new java.awt.Dimension(250, 40));

                // Purgar restricciones rígidas de NetBeans de la Cabecera Visual
                titleBar.removeAll();
                titleBar.setLayout(new java.awt.BorderLayout());

                javax.swing.JPanel leftPanel = new javax.swing.JPanel(
                        new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0));
                leftPanel.setOpaque(false);
                leftPanel.add(lblTitleIcon);

                javax.swing.JPanel rightPanel = new javax.swing.JPanel(
                        new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
                rightPanel.setOpaque(false);

                if (btnMinimize != null) {
                    btnMinimize.putClientProperty("FlatLaf.style",
                            "background: #00000000; hoverBackground: #333333; hoverForeground: #ffffff; arc: 0; borderWidth: 0; focusPainted: false;");
                    rightPanel.add(btnMinimize);
                }
                if (btnMaximize != null) {
                    btnMaximize.putClientProperty("FlatLaf.style",
                            "background: #00000000; hoverBackground: #333333; hoverForeground: #ffffff; arc: 0; borderWidth: 0; focusPainted: false;");
                    rightPanel.add(btnMaximize);
                }
                if (btnClose != null) {
                    // Botón cerrar debe colorearse en rojo al pasar el cursor
                    btnClose.putClientProperty("FlatLaf.style",
                            "background: #00000000; hoverBackground: #E81123; hoverForeground: #ffffff; arc: 0; borderWidth: 0; focusPainted: false;");
                    rightPanel.add(btnClose);
                }

                titleBar.add(leftPanel, java.awt.BorderLayout.WEST);
                titleBar.add(rightPanel, java.awt.BorderLayout.EAST);
                titleBar.revalidate();
                titleBar.repaint();
            }

            // 2. Pasar la imagen al panel de fondo dinámico
            if (getContentPane() instanceof BackgroundPanel) {
                Image imgWatermark = icon.getImage().getScaledInstance(450, 450, Image.SCALE_SMOOTH);
                ((BackgroundPanel) getContentPane()).setWatermark(imgWatermark);
            }

        } catch (Exception e) {
            logger.warning("No se pudo cargar el hacker logo: " + e.getMessage());
        }
    }

    private void setupDynamicBackground() {
        // Obtenemos los componentes actuales
        java.awt.Component[] components = getContentPane().getComponents();
        java.awt.LayoutManager layout = getContentPane().getLayout();

        // Creamos nuestro panel de fondo
        BackgroundPanel bgPanel = new BackgroundPanel();
        bgPanel.setLayout(layout);

        // Migramos los componentes
        for (java.awt.Component c : components) {
            // Mantener las restricciones del BorderLayout
            Object constraints = null;
            if (c == titleBar)
                constraints = java.awt.BorderLayout.NORTH;
            else if (c == contentArea)
                constraints = java.awt.BorderLayout.CENTER;
            else if (c == footerPanel)
                constraints = java.awt.BorderLayout.SOUTH;

            bgPanel.add(c, constraints);
        }

        // Establecemos el nuevo panel como el principal
        setContentPane(bgPanel);

        // Aseguramos transparencia base
        contentArea.setOpaque(false);
        panelInicio.setOpaque(false);
        panelSettings.setOpaque(false);
        panelLoading.setOpaque(false);
        panelOperaciones.setOpaque(false); // <--- AGREGADO
        footerPanel.setOpaque(false);
        titleBar.setOpaque(false);
    }

    /**
     * Panel personalizado para renderizar gradientes y entramados tecnológicos.
     */
    private class BackgroundPanel extends javax.swing.JPanel {
        private java.awt.Color color1 = new java.awt.Color(2, 6, 23);
        private java.awt.Color color2 = new java.awt.Color(10, 20, 45);
        private java.awt.Color gridCol = new java.awt.Color(0, 242, 255, 10);
        private int gridSize = 40;
        private Image watermark;
        private float watermarkAlpha = 0.15f;

        public void setWatermark(Image img) {
            this.watermark = img;
            repaint();
        }

        public void setWatermarkAlpha(float a) {
            this.watermarkAlpha = a;
            repaint();
        }

        public void updateTheme(java.awt.Color c1, java.awt.Color c2, java.awt.Color gc) {
            this.color1 = c1;
            this.color2 = c2;
            this.gridCol = gc;
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            // 1. Gradiente Diagonal Principal
            java.awt.GradientPaint gp = new java.awt.GradientPaint(
                    0, 0, color1,
                    getWidth(), getHeight(), color2);
            g2.setPaint(gp);
            g2.fillRect(0, 0, getWidth(), getHeight());

            // 2. Grid de fondo (Tech Pattern)
            g2.setColor(gridCol);
            g2.setStroke(new java.awt.BasicStroke(1.0f));

            // Lineas verticales
            for (int x = 0; x < getWidth(); x += gridSize) {
                g2.drawLine(x, 0, x, getHeight());
            }
            // Lineas horizontales
            for (int y = 0; y < getHeight(); y += gridSize) {
                g2.drawLine(0, y, getWidth(), y);
            }

            // 3. Brillo en las intersecciones (Micro-detalles)
            g2.setColor(new java.awt.Color(gridCol.getRed(), gridCol.getGreen(), gridCol.getBlue(),
                    gridCol.getAlpha() + 10));
            for (int x = 0; x < getWidth(); x += gridSize) {
                for (int y = 0; y < getHeight(); y += gridSize) {
                    g2.fillOval(x - 1, y - 1, 3, 3);
                }
            }

            // 4. Marca de agua (Hacker Logo)
            if (watermark != null) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, watermarkAlpha));
                int x = (getWidth() - watermark.getWidth(null)) / 2;
                int y = (getHeight() - watermark.getHeight(null)) / 2;
                g2.drawImage(watermark, x, y, null);
            }

            g2.dispose();
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAdminChallenges;
    private javax.swing.JButton btnBots;
    private javax.swing.JButton btnClose;
    private javax.swing.JButton btnCtrFilter;
    private javax.swing.JButton btnDeleteChallenge;
    private javax.swing.JButton btnFooterNav;
    private javax.swing.JButton btnFullscreen;
    private javax.swing.JButton btnGraphics;
    private javax.swing.JButton btnInforme;
    private javax.swing.JButton btnLanzarMysql;
    private javax.swing.JButton btnLanzarTomcat;
    private javax.swing.JButton btnLogs;
    private javax.swing.JButton btnMaximize;
    private javax.swing.JButton btnMinimize;
    private javax.swing.JButton btnMonitorRed;
    private javax.swing.JButton btnNgrok;
    private javax.swing.JButton btnShowQR;
    private javax.swing.JButton btnStart;
    private javax.swing.JButton btnSyncChallenge;
    private javax.swing.JButton btnTanner;
    private javax.swing.JButton btnUiScale;
    private javax.swing.JScrollPane challengeScroll;
    private javax.swing.JPanel consoleContainer;
    private javax.swing.JPanel contentArea;
    private javax.swing.JPanel footerPanel;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JLabel lblBrightness;
    private javax.swing.JLabel lblLoadingStatus;
    private javax.swing.JLabel lblOsIcon;
    private javax.swing.JLabel lblStatusDb;
    private javax.swing.JLabel lblStatusMysql;
    private javax.swing.JLabel lblStatusNgrok;
    private javax.swing.JLabel lblStatusPort;
    private javax.swing.JLabel lblStatusService;
    private javax.swing.JLabel lblTitleIcon;
    private io.sentinel.command.sentinelc2.SentinelConsole monitorConsole;
    private io.sentinel.command.sentinelc2.SentinelConsole monitorConsole2;
    private javax.swing.JScrollPane monitorScroll;
    private javax.swing.JScrollPane monitorScroll2;
    private javax.swing.JPanel panelActivityFeed;
    private javax.swing.JPanel panelChallenge;
    private javax.swing.JPanel panelChallengeControls;
    private javax.swing.JPanel panelDashboardCenter;
    private javax.swing.JPanel panelGraphContainer;
    private javax.swing.JPanel panelInicio;
    private javax.swing.JPanel panelLoading;
    private javax.swing.JPanel panelMapPlaceholder;
    private javax.swing.JPanel panelMonitor;
    private javax.swing.JPanel panelMonitorCenter;
    private javax.swing.JPanel panelMonitorSouth;
    private javax.swing.JPanel panelOperaciones;
    private javax.swing.JPanel panelRightSidebar;
    private javax.swing.JPanel panelSettings;
    private javax.swing.JPanel panelTimelineControls;
    private javax.swing.JProgressBar prgBar;
    private javax.swing.JRadioButton radioSecurity;
    private javax.swing.JRadioButton radioStandard;
    private javax.swing.JSlider sliderBrightness;
    private javax.swing.JTable tblChallenge;
    private javax.swing.JToggleButton themeToggle;
    private javax.swing.JPanel titleBar;
    // End of variables declaration//GEN-END:variables

    private class ThreatMonitorPanel extends javax.swing.JPanel {
        private org.jfree.data.xy.XYSeriesCollection barDataset;
        private org.jfree.data.xy.XYSeriesCollection lineDataset;
        private org.jfree.data.xy.XYSeries barSeries;
        private org.jfree.data.xy.XYSeries lineSeries;
        private org.jfree.chart.ChartPanel chartPanel;
        private Timer updateTimer;

        private String currentRangeState = "1 DÍA";
        private org.jfree.chart.plot.XYPlot plot;
        private org.jfree.chart.JFreeChart chart;
        private org.jfree.chart.axis.NumberAxis domainAxis;
        private org.jfree.chart.axis.NumberAxis barAxis;

        public void setCurrentRangeState(String state) {
            this.currentRangeState = state;
        }

        public ThreatMonitorPanel() {
            setOpaque(false);
            setLayout(new java.awt.BorderLayout());

            // --- Datasets ---
            barSeries = new org.jfree.data.xy.XYSeries("Incidentes");
            lineSeries = new org.jfree.data.xy.XYSeries("Tendencia");
            barDataset = new org.jfree.data.xy.XYSeriesCollection(barSeries);
            lineDataset = new org.jfree.data.xy.XYSeriesCollection(lineSeries);
            for (int i = 0; i < 24; i++) {
                barSeries.add(i + 1, 0);
                lineSeries.add(i + 1, 0);
            }

            // --- Ejes ---
            domainAxis = new org.jfree.chart.axis.NumberAxis("Día (últimos 24)");
            domainAxis.setTickLabelFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
            domainAxis.setLabelFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 12));

            barAxis = new org.jfree.chart.axis.NumberAxis("Conexiones");
            barAxis.setTickLabelFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
            barAxis.setLabelFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 12));
            barAxis.setStandardTickUnits(org.jfree.chart.axis.NumberAxis.createIntegerTickUnits());

            // --- Renderer Barras ---
            org.jfree.chart.renderer.xy.XYBarRenderer barRenderer = new org.jfree.chart.renderer.xy.XYBarRenderer(0.15);
            barRenderer.setSeriesPaint(0, new java.awt.Color(220, 20, 60, 210)); // Rojo agresivo (Crimson)
            barRenderer.setShadowVisible(false);
            barRenderer.setDefaultItemLabelsVisible(true);
            barRenderer.setDefaultItemLabelGenerator(new org.jfree.chart.labels.StandardXYItemLabelGenerator(
                    "{2}", new java.text.DecimalFormat("0"), new java.text.DecimalFormat("0")));
            barRenderer.setDefaultItemLabelFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 11));
            barRenderer.setDefaultItemLabelPaint(new java.awt.Color(255, 255, 255));
            barRenderer.setDefaultPositiveItemLabelPosition(
                    new org.jfree.chart.labels.ItemLabelPosition(
                            org.jfree.chart.labels.ItemLabelAnchor.OUTSIDE12,
                            org.jfree.chart.ui.TextAnchor.BOTTOM_CENTER));

            // --- Renderer Línea de tendencia ---
            org.jfree.chart.renderer.xy.XYLineAndShapeRenderer lineRenderer = new org.jfree.chart.renderer.xy.XYLineAndShapeRenderer(
                    true, true);
            lineRenderer.setSeriesPaint(0, new java.awt.Color(0, 255, 65)); // Verde Consola
            lineRenderer.setSeriesStroke(0, new java.awt.BasicStroke(2.5f));

            // --- Plot combinado ---
            plot = new org.jfree.chart.plot.XYPlot();
            plot.setDomainAxis(domainAxis);
            plot.setRangeAxis(barAxis);

            plot.setDataset(0, barDataset);
            plot.setRenderer(0, barRenderer);
            plot.setDataset(1, lineDataset);
            plot.setRenderer(1, lineRenderer);
            plot.mapDatasetToRangeAxis(1, 0);

            // Inyectamos colores según el tema actual inmediatamente
            updatePlotColors();

            // --- Chart ---
            chart = new org.jfree.chart.JFreeChart(
                    "LIVE LOGIN ACTIVITY & ANOMALIES",
                    new java.awt.Font("Monospaced", java.awt.Font.BOLD, 16),
                    plot,
                    false);
            updateChartBranding();

            chartPanel = new org.jfree.chart.ChartPanel(chart);
            chartPanel.setOpaque(true);
            add(chartPanel, java.awt.BorderLayout.CENTER);
            updatePanelTheme();

            updateTimer = new Timer(5000, e -> fetchDataFromMySQL());
            updateTimer.setInitialDelay(0);
            updateTimer.start();
        }

        private void updatePanelTheme() {
            if (chartPanel == null)
                return;
            boolean isLight = (currentThemeMode == 0);
            java.awt.Color bg = isLight ? new java.awt.Color(245, 245, 245)
                    : (currentThemeMode == 1) ? new java.awt.Color(230, 247, 255) : new java.awt.Color(10, 25, 47);
            java.awt.Color border = isLight ? new java.awt.Color(0, 51, 102) : new java.awt.Color(0, 242, 255);

            chartPanel.setBackground(bg);
            chartPanel.setBorder(javax.swing.BorderFactory.createLineBorder(border, 1));
            updatePlotColors();
            updateChartBranding();
        }

        private void updatePlotColors() {
            if (plot == null)
                return;
            java.awt.Color bg;
            java.awt.Color grid;
            java.awt.Color text;

            if (currentThemeMode == 0) { // LIGHT
                bg = new java.awt.Color(240, 240, 240);
                grid = new java.awt.Color(200, 200, 200);
                text = new java.awt.Color(0, 51, 102);
            } else if (currentThemeMode == 1) { // MID BLUE
                bg = new java.awt.Color(200, 235, 255);
                grid = new java.awt.Color(0, 100, 255, 40);
                text = new java.awt.Color(0, 70, 150);
            } else { // DEEP SPACE
                bg = new java.awt.Color(10, 25, 47);
                grid = new java.awt.Color(0, 242, 255, 40);
                text = new java.awt.Color(0, 242, 255);
            }

            plot.setBackgroundPaint(bg);
            plot.setDomainGridlinePaint(grid);
            plot.setRangeGridlinePaint(grid);
            plot.setOutlinePaint(text);

            domainAxis.setAxisLinePaint(text);
            domainAxis.setTickLabelPaint(text);
            domainAxis.setLabelPaint(text);
            barAxis.setAxisLinePaint(text);
            barAxis.setTickLabelPaint(text);
            barAxis.setLabelPaint(text);
        }

        private void updateChartBranding() {
            if (chart == null)
                return;
            boolean isLight = (currentThemeMode == 0);
            java.awt.Color text = isLight ? new java.awt.Color(0, 51, 102) : new java.awt.Color(0, 242, 255);
            java.awt.Color bg = isLight ? new java.awt.Color(245, 245, 245) : new java.awt.Color(10, 25, 47);

            chart.setBackgroundPaint(bg);
            chart.getTitle().setPaint(text);
            chart.getTitle().setText("LIVE LOGIN ACTIVITY & ANOMALIES (" + currentRangeState + ")");
        }

        public void fetchDataFromMySQL() {
            final String range = currentRangeState;
            updateChartBranding();

            new javax.swing.SwingWorker<java.util.List<Integer>, Void>() {
                @Override
                protected java.util.List<Integer> doInBackground() throws Exception {
                    java.util.List<Integer> stats = new java.util.ArrayList<>();
                    String url = DBConfig.getInstance().getUrl();

                    int intervalValue = 24;
                    String intervalUnit = "HOUR";
                    int slots = 24;
                    String timeFunc = "HOUR"; // El eje X base

                    switch (range) {
                        case "15 MIN":
                            intervalValue = 15;
                            intervalUnit = "MINUTE";
                            slots = 15;
                            timeFunc = "MINUTE";
                            break;
                        case "1 HORA":
                            intervalValue = 1;
                            intervalUnit = "HOUR";
                            slots = 60;
                            timeFunc = "MINUTE";
                            break;
                        case "1 DÍA":
                            intervalValue = 24;
                            intervalUnit = "HOUR";
                            slots = 24;
                            timeFunc = "HOUR";
                            break;
                        case "1 SEM":
                            intervalValue = 7;
                            intervalUnit = "DAY";
                            slots = 7;
                            timeFunc = "DAY";
                            break;
                        case "1 MES":
                            intervalValue = 30;
                            intervalUnit = "DAY";
                            slots = 30;
                            timeFunc = "DAY";
                            break;
                    }

                    try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url,
                            DBConfig.getInstance().getUser(), DBConfig.getInstance().getPassword())) {
                        String query = "SELECT TIMESTAMPDIFF(" + timeFunc
                                + ", fecha_hora, NOW()) as diff_time, COUNT(*) as ops " +
                                "FROM logs_trafico_ngrok WHERE fecha_hora >= DATE_SUB(NOW(), INTERVAL " + intervalValue
                                + " " + intervalUnit + ") " +
                                "GROUP BY diff_time ORDER BY diff_time DESC";

                        try (java.sql.PreparedStatement st = conn.prepareStatement(query);
                                java.sql.ResultSet rs = st.executeQuery()) {
                            java.util.Map<Integer, Integer> map = new java.util.HashMap<>();
                            while (rs.next()) {
                                map.put(rs.getInt("diff_time"), rs.getInt("ops"));
                            }

                            for (int i = 0; i < slots; i++) {
                                stats.add(map.getOrDefault((slots - 1) - i, 0));
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        for (int i = 0; i < slots; i++)
                            stats.add(0);
                    }
                    return stats;
                }

                @Override
                protected void done() {
                    try {
                        java.util.List<Integer> data = get();
                        barSeries.clear();
                        lineSeries.clear();
                        for (int i = 0; i < data.size(); i++) {
                            barSeries.add(i + 1, data.get(i));
                            lineSeries.add(i + 1, data.get(i));
                        }
                        chartPanel.revalidate();
                        chartPanel.repaint();
                        MainFrame.this.revalidate();
                        MainFrame.this.repaint();
                    } catch (Exception ex) {
                    }
                }
            }.execute();
        }
    }

    private class GeoLocationPanel extends javax.swing.JPanel {
        private javax.swing.JTable tabla;
        private javax.swing.table.DefaultTableModel model;
        private Timer updateTimer;

        public GeoLocationPanel() {
            setOpaque(false);
            setLayout(new java.awt.BorderLayout());

            javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane();
            scrollPane.setOpaque(false);
            scrollPane.getViewport().setOpaque(false);
            scrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder(20, 20, 20, 20));

            String[] colums = { "PAÍS / UBICACIÓN", "INCIDENTES REGISTRADOS" };
            model = new javax.swing.table.DefaultTableModel(colums, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            tabla = new javax.swing.JTable(model);
            tabla.setOpaque(false);
            ((javax.swing.table.DefaultTableCellRenderer) tabla.getDefaultRenderer(Object.class)).setOpaque(false);
            tabla.setForeground(new java.awt.Color(0, 242, 255));
            tabla.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 16));
            tabla.setRowHeight(35);
            tabla.setShowGrid(true);
            tabla.setGridColor(new java.awt.Color(0, 242, 255, 50));

            tabla.getTableHeader().setOpaque(false);
            tabla.getTableHeader().setBackground(new java.awt.Color(10, 25, 47));
            tabla.getTableHeader().setForeground(new java.awt.Color(255, 215, 0));
            tabla.getTableHeader().setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 18));

            scrollPane.setViewportView(tabla);

            lblT = new javax.swing.JLabel("  GEO-LOCATION DEPLOYMENT | TABLA DE AMENAZAS ACTIVAS");
            lblT.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 18));
            lblT.setForeground(new java.awt.Color(255, 215, 0));
            lblT.setBorder(javax.swing.BorderFactory.createEmptyBorder(20, 0, 0, 0));

            add(lblT, java.awt.BorderLayout.NORTH);
            add(scrollPane, java.awt.BorderLayout.CENTER);

            updatePanelTheme(); // Aplicar tema inicial

            updateTimer = new Timer(5000, e -> fetchDataFromMySQL());
            updateTimer.setInitialDelay(0);
            updateTimer.start();
        }

        private javax.swing.JLabel lblT; // Referencia para theme (inicializada en constructor)

        public void updatePanelTheme() {
            java.awt.Color text;
            java.awt.Color headerBg;
            java.awt.Color headerFg;
            java.awt.Color grid;

            if (currentThemeMode == 0) { // LIGHT
                text = new java.awt.Color(5, 25, 55);
                headerBg = new java.awt.Color(200, 220, 240);
                headerFg = new java.awt.Color(0, 51, 102);
                grid = new java.awt.Color(180, 200, 220);
            } else if (currentThemeMode == 1) { // MID BLUE
                text = new java.awt.Color(0, 70, 150);
                headerBg = new java.awt.Color(186, 231, 255);
                headerFg = new java.awt.Color(0, 100, 200);
                grid = new java.awt.Color(0, 100, 255, 50);
            } else { // DEEP SPACE
                text = new java.awt.Color(0, 242, 255);
                headerBg = new java.awt.Color(10, 25, 47);
                headerFg = new java.awt.Color(255, 215, 0);
                grid = new java.awt.Color(0, 242, 255, 50);
            }

            if (lblT != null)
                lblT.setForeground(headerFg);
            tabla.setForeground(text);
            tabla.setGridColor(grid);
            tabla.getTableHeader().setBackground(headerBg);
            tabla.getTableHeader().setForeground(headerFg);

            // Refrescar renderers
            tabla.repaint();
        }

        private String getFlagEmoji(String country) {
            if (country == null || country.isEmpty())
                return "🏳️ ";
            String c = country.toLowerCase();
            if (c.contains("rus"))
                return "🇷🇺 ";
            if (c.contains("us") || c.contains("united states") || c.contains("estados unidos"))
                return "🇺🇸 ";
            if (c.contains("china"))
                return "🇨🇳 ";
            if (c.contains("spain") || c.contains("españa"))
                return "🇪🇸 ";
            if (c.contains("uk") || c.contains("united kingdom") || c.contains("britain"))
                return "🇬🇧 ";
            if (c.contains("brazil") || c.contains("brasil"))
                return "🇧🇷 ";
            if (c.contains("france") || c.contains("francia"))
                return "🇫🇷 ";
            if (c.contains("germany") || c.contains("alemania"))
                return "🇩🇪 ";
            if (c.contains("italy") || c.contains("italia"))
                return "🇮🇹 ";
            if (c.contains("japan") || c.contains("japón"))
                return "🇯🇵 ";
            if (c.contains("mexico") || c.contains("méxico"))
                return "🇲🇽 ";
            if (c.contains("canada") || c.contains("canadá"))
                return "🇨🇦 ";
            return "🏳️ ";
        }

        private void fetchDataFromMySQL() {
            new javax.swing.SwingWorker<java.util.List<Object[]>, Void>() {
                @Override
                protected java.util.List<Object[]> doInBackground() throws Exception {
                    java.util.List<Object[]> data = new java.util.ArrayList<>();
                    String url = DBConfig.getInstance().getUrl();
                    try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url,
                            DBConfig.getInstance().getUser(), DBConfig.getInstance().getPassword())) {
                        String query = "SELECT ubicacion, COUNT(*) as incidentes FROM capturas GROUP BY ubicacion ORDER BY incidentes DESC LIMIT 50";
                        try (java.sql.PreparedStatement st = conn.prepareStatement(query);
                                java.sql.ResultSet rs = st.executeQuery()) {
                            while (rs.next()) {
                                String ubi = rs.getString("ubicacion");
                                int inc = rs.getInt("incidentes");
                                String country = (ubi == null || ubi.trim().isEmpty()) ? "Ubicación Oculta" : ubi;
                                data.add(new Object[] { getFlagEmoji(country) + country, inc });
                            }
                        }
                    } catch (Exception ex) {
                        data.add(new Object[] { "ERROR BDD: No se pudo conectar a honeypot_db", 0 });
                    }
                    return data;
                }

                @Override
                protected void done() {
                    try {
                        model.setRowCount(0);
                        for (Object[] row : get()) {
                            model.addRow(row);
                        }
                        tabla.revalidate();
                        tabla.repaint();
                    } catch (Exception ex) {
                    }
                }
            }.execute();
        }
    }

    /**
     * Panel especializado para el feed de actividad en vivo (Live Activity Ticker).
     */
    private class LiveActivityPanel extends javax.swing.JPanel {
        private final javax.swing.DefaultListModel<String> model;
        private final javax.swing.JList<String> list;

        public LiveActivityPanel() {
            setOpaque(false);
            setLayout(new java.awt.BorderLayout());
            model = new javax.swing.DefaultListModel<>();
            list = new javax.swing.JList<>(model);
            list.setOpaque(false);
            list.setBackground(new Color(0, 0, 0, 0));
            list.setForeground(new Color(0, 242, 255));
            list.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
            list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                        int index, boolean isSelected, boolean cellHasFocus) {
                    javax.swing.JLabel label = (javax.swing.JLabel) super.getListCellRendererComponent(list, value,
                            index, isSelected, cellHasFocus);
                    label.setOpaque(false);
                    label.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
                    if (value.toString().contains("ALERT") || value.toString().contains("SCAN")) {
                        label.setForeground(Color.RED);
                    } else if (value.toString().contains("OK")) {
                        label.setForeground(new Color(0, 242, 255, 180));
                    }
                    return label;
                }
            });
            add(list, java.awt.BorderLayout.CENTER);
            updatePanelTheme();
        }

        private javax.swing.JLabel lblActivityTitleRef;

        public void updatePanelTheme() {
            java.awt.Color main;
            if (currentThemeMode == 0)
                main = new java.awt.Color(5, 25, 55);
            else if (currentThemeMode == 1)
                main = new java.awt.Color(0, 70, 150);
            else
                main = new java.awt.Color(0, 242, 255);

            list.setForeground(main);
            list.repaint();
        }

        public void logActivity(String msg) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                model.addElement(msg);
                if (model.size() > 100)
                    model.remove(0); // Mantener historial manejable
                // Auto-scroll al final
                list.ensureIndexIsVisible(model.size() - 1);
            });
        }
    }

    /**
     * Utilidad para permitir el redimensionado de una ventana JDialog o JFrame
     * "undecorated".
     */
    private class WindowResizer extends java.awt.event.MouseAdapter {
        private final javax.swing.JFrame frame;
        private final int dist = 8; // Distancia de detección del borde
        private int direction;
        private java.awt.Point startPos;
        private java.awt.Rectangle startBounds;

        public WindowResizer(javax.swing.JFrame frame) {
            this.frame = frame;
            this.frame.addMouseListener(this);
            this.frame.addMouseMotionListener(this);
        }

        @Override
        public void mouseMoved(java.awt.event.MouseEvent e) {
            // Desactivar redimensionado en pantalla completa o maximizado
            if (!titleBar.isVisible() || frame.getExtendedState() == javax.swing.JFrame.MAXIMIZED_BOTH) {
                frame.setCursor(java.awt.Cursor.getDefaultCursor());
                direction = 0;
                return;
            }
            int x = e.getX();
            int y = e.getY();
            int w = frame.getWidth();
            int h = frame.getHeight();

            int cursor = 0;
            if (x < dist) {
                if (y < dist)
                    cursor = java.awt.Cursor.NW_RESIZE_CURSOR;
                else if (y > h - dist)
                    cursor = java.awt.Cursor.SW_RESIZE_CURSOR;
                else
                    cursor = java.awt.Cursor.W_RESIZE_CURSOR;
            } else if (x > w - dist) {
                if (y < dist)
                    cursor = java.awt.Cursor.NE_RESIZE_CURSOR;
                else if (y > h - dist)
                    cursor = java.awt.Cursor.SE_RESIZE_CURSOR;
                else
                    cursor = java.awt.Cursor.E_RESIZE_CURSOR;
            } else if (y < dist) {
                cursor = java.awt.Cursor.N_RESIZE_CURSOR;
            } else if (y > h - dist) {
                cursor = java.awt.Cursor.S_RESIZE_CURSOR;
            }

            if (cursor != 0) {
                frame.setCursor(java.awt.Cursor.getPredefinedCursor(cursor));
                direction = cursor;
            } else {
                frame.setCursor(java.awt.Cursor.getDefaultCursor());
                direction = 0;
            }
        }

        @Override
        public void mousePressed(java.awt.event.MouseEvent e) {
            if (direction != 0) {
                startPos = e.getLocationOnScreen();
                startBounds = frame.getBounds();
            }
        }

        @Override
        public void mouseDragged(java.awt.event.MouseEvent e) {
            if (startPos != null && direction != 0) {
                java.awt.Point curPos = e.getLocationOnScreen();
                int dx = curPos.x - startPos.x;
                int dy = curPos.y - startPos.y;

                java.awt.Rectangle newBounds = new java.awt.Rectangle(startBounds);

                switch (direction) {
                    case java.awt.Cursor.N_RESIZE_CURSOR:
                        newBounds.y += dy;
                        newBounds.height -= dy;
                        break;
                    case java.awt.Cursor.S_RESIZE_CURSOR:
                        newBounds.height += dy;
                        break;
                    case java.awt.Cursor.W_RESIZE_CURSOR:
                        newBounds.x += dx;
                        newBounds.width -= dx;
                        break;
                    case java.awt.Cursor.E_RESIZE_CURSOR:
                        newBounds.width += dx;
                        break;
                    case java.awt.Cursor.NW_RESIZE_CURSOR:
                        newBounds.x += dx;
                        newBounds.y += dy;
                        newBounds.width -= dx;
                        newBounds.height -= dy;
                        break;
                    case java.awt.Cursor.NE_RESIZE_CURSOR:
                        newBounds.y += dy;
                        newBounds.width += dx;
                        newBounds.height -= dy;
                        break;
                    case java.awt.Cursor.SW_RESIZE_CURSOR:
                        newBounds.x += dx;
                        newBounds.width -= dx;
                        newBounds.height += dy;
                        break;
                    case java.awt.Cursor.SE_RESIZE_CURSOR:
                        newBounds.width += dx;
                        newBounds.height += dy;
                        break;
                }

                // Respetar dimensiones mínimas
                java.awt.Dimension min = frame.getMinimumSize();
                if (newBounds.width < min.width) {
                    if (direction == java.awt.Cursor.W_RESIZE_CURSOR || direction == java.awt.Cursor.NW_RESIZE_CURSOR
                            || direction == java.awt.Cursor.SW_RESIZE_CURSOR) {
                        newBounds.x = startBounds.x + startBounds.width - min.width;
                    }
                    newBounds.width = min.width;
                }
                if (newBounds.height < min.height) {
                    if (direction == java.awt.Cursor.N_RESIZE_CURSOR || direction == java.awt.Cursor.NW_RESIZE_CURSOR
                            || direction == java.awt.Cursor.NE_RESIZE_CURSOR) {
                        newBounds.y = startBounds.y + startBounds.height - min.height;
                    }
                    newBounds.height = min.height;
                }

                frame.setBounds(newBounds);
                frame.validate();
            }
        }

        @Override
        public void mouseReleased(java.awt.event.MouseEvent e) {
            startPos = null;
            startBounds = null;
        }
    }

    /**
     * Busca el binario de node de forma portable.
     */
    private String findNodeBinary() {
        String name = System.getProperty("os.name").toLowerCase();
        if (name.contains("win")) {
            try {
                Process p = Runtime.getRuntime().exec("node -v");
                if (p.waitFor() == 0)
                    return "node";
            } catch (Exception e) {
            }
            File stdPath = new File("C:\\Program Files\\nodejs\\node.exe");
            if (stdPath.exists())
                return stdPath.getAbsolutePath();
            return "node";
        } else {
            if (new File("/usr/bin/node").exists())
                return "/usr/bin/node";
            if (new File("/usr/local/bin/node").exists())
                return "/usr/local/bin/node";
            return "node";
        }
    }

    private void launchHoneypotSuite() {
        try {
            log("[SENTINEL] Lanzando Consola de Honeypots...", getThemeAccentColor());
            String pythonCmd = detectedOS.equals("WINDOWS") ? "python" : "python3";
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", pythonCmd, "sentinel_suite.py");
            pb.directory(new File("."));
            pb.start();
            log("[SENTINEL] Suite iniciada en ventana independiente.", Color.GREEN);
        } catch (Exception e) {
            log("[ERROR] No se pudo lanzar la suite: " + e.getMessage(), Color.RED);
        }
    }

    // =========================================================================
    // NGROK — Instalación via winget (Microsoft Store) y verificación de token
    // =========================================================================

    /**
     * Instala ngrok vía winget (Microsoft Store).
     * Si winget falla o no está disponible, muestra instrucciones manuales.
     */
    private void instalarNgrokConWinget() {
        Color fondo = new Color(10, 25, 47);
        Color acento = new Color(0, 242, 255);

        javax.swing.JDialog dlg = new javax.swing.JDialog(this, "SENTINEL — NGROK INSTALL", true);
        dlg.setUndecorated(true);
        dlg.getRootPane().setBorder(BorderFactory.createLineBorder(acento, 1));
        dlg.setSize(500, 210);
        dlg.setLocationRelativeTo(this);
        dlg.getContentPane().setBackground(fondo);
        dlg.setLayout(new java.awt.BorderLayout(10, 10));

        javax.swing.JLabel lblEstado = new javax.swing.JLabel(
                "Instalando ngrok via Microsoft Store (winget)...", javax.swing.JLabel.CENTER);
        lblEstado.setForeground(acento);
        lblEstado.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 12));

        javax.swing.JProgressBar barra = new javax.swing.JProgressBar();
        barra.setIndeterminate(true);
        barra.setForeground(acento);
        barra.setBackground(new Color(40, 40, 40));
        barra.setStringPainted(true);
        barra.setString("winget install ngrok -s msstore");

        javax.swing.JButton btnCerrar = new javax.swing.JButton("CANCELAR");
        btnCerrar.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 1; background: #00000000; borderColor: #FF4444; foreground: #FF4444;");

        javax.swing.JPanel pnlCenter = new javax.swing.JPanel(new java.awt.GridLayout(2, 1, 8, 8));
        pnlCenter.setOpaque(false);
        pnlCenter.setBorder(BorderFactory.createEmptyBorder(20, 30, 10, 30));
        pnlCenter.add(lblEstado);
        pnlCenter.add(barra);

        javax.swing.JPanel pnlSouth = new javax.swing.JPanel();
        pnlSouth.setOpaque(false);
        pnlSouth.add(btnCerrar);

        dlg.add(pnlCenter, java.awt.BorderLayout.CENTER);
        dlg.add(pnlSouth, java.awt.BorderLayout.SOUTH);

        // Ciclo de mensajes dinámicos para feedback visual (cada 1.5s)
        String[] mensajes = {
                "Conectando con Microsoft Store...",
                "Validando manifiesto de ngrok...",
                "Descargando binarios seguros...",
                "Configurando enlaces del sistema...",
                "Verificando integridad del paquete...",
                "Finalizando registro en WinGet..."
        };
        javax.swing.Timer labelTimer = new javax.swing.Timer(3000, new java.awt.event.ActionListener() {
            int i = 0;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                barra.setString(mensajes[i % mensajes.length]);
                i++;
            }
        });

        javax.swing.SwingWorker<Integer, String> worker = new javax.swing.SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                labelTimer.start(); // Iniciamos el ciclo de mensajes
                // Añadimos flags para evitar prompts interactivos que bloquean el proceso
                String command = "winget install ngrok -s msstore --accept-package-agreements --accept-source-agreements";
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        publish(line);
                    }
                }
                return proc.waitFor();
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                chunks.forEach(l -> monitorConsole.log("[WINGET] " + l, "INFO"));
            }

            @Override
            protected void done() {
                labelTimer.stop(); // Detenemos el ciclo de mensajes
                barra.setIndeterminate(false);
                barra.setString("");
                try {
                    int exitCode = get();
                    if (exitCode == 0) {
                        barra.setValue(100);
                        lblEstado.setText("✔  ngrok instalado. Reinicia Sentinel C2 si no se detecta.");
                        lblEstado.setForeground(new Color(0, 255, 128));
                        isNgrokReady = checkDependency("ngrok");
                        log("[NGROK] Instalación completada via winget.", "INFO");
                    } else {
                        mostrarInstruccionesManualNgrok(dlg, exitCode, null);
                    }
                } catch (Exception ex) {
                    mostrarInstruccionesManualNgrok(dlg, -1, ex);
                }
                btnCerrar.setText("CERRAR");
            }
        };

        btnCerrar.addActionListener(e -> {
            labelTimer.stop();
            worker.cancel(true);
            dlg.dispose();
        });
        worker.execute();
        dlg.setVisible(true);
    }

    /**
     * Muestra un JOptionPane con las instrucciones manuales para instalar ngrok
     * desde Microsoft Store cuando winget no está disponible o falla.
     */
    private void mostrarInstruccionesManualNgrok(javax.swing.JDialog parent, int exitCode, Exception ex) {
        String detalle = (ex != null) ? ex.getMessage() : "Código de salida: " + exitCode;
        log("[NGROK] winget falló (" + detalle + "). Mostrando instrucciones manuales.", "ERROR");

        String msg = "⚠️  INSTALACIÓN MANUAL REQUERIDA\n\n" +
                "winget no pudo completar la instalación.\n" +
                "Detalle: " + detalle + "\n\n" +
                "Sigue estos pasos para instalar ngrok manualmente:\n\n" +
                "  1. Abre Microsoft Store en tu equipo\n" +
                "     (Menú Inicio → 'Microsoft Store').\n" +
                "  2. En el buscador escribe: ngrok\n" +
                "  3. Selecciona el resultado de ngrok Inc. y haz clic en 'Obtener'.\n" +
                "  4. Espera a que se instale y cierra la Store.\n" +
                "  5. Cierra y vuelve a abrir Sentinel C2.\n\n" +
                "  ─── O bien, desde una terminal con permisos de admin: ───\n" +
                "      winget install ngrok -s msstore";

        JOptionPane.showMessageDialog(
                parent, msg,
                "SENTINEL C2 — NGROK: INSTALACIÓN MANUAL",
                JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Verifica si ngrok tiene un authtoken configurado ejecutando 'ngrok config
     * check'.
     * Devuelve true si la salida contiene 'authtoken'; false en cualquier otro
     * caso.
     * Se usa como guardia antes de lanzar el túnel.
     */
    private boolean tieneNgrokAuthtoken() {
        String ngrokPath = findToolPath("ngrok");
        if (ngrokPath == null) {
            return false;
        }

        try {
            // 'ngrok config show' imprime el YAML de configuración actual
            ProcessBuilder pb = new ProcessBuilder(ngrokPath, "config", "show");
            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            p.waitFor();
            String output = sb.toString();

            // Si el YAML contiene 'authtoken:', es que está configurado
            if (output.contains("authtoken:")) {
                return true;
            }

            // Fallback: Si el comando falla o no muestra nada, intentamos verificar
            // el archivo por defecto en Windows (%LOCALAPPDATA%\ngrok\ngrok.yml)
            if (detectedOS.equals("WINDOWS")) {
                String localAppData = System.getenv("LOCALAPPDATA");
                if (localAppData != null) {
                    File configFile = new File(localAppData + File.separator + "ngrok" + File.separator + "ngrok.yml");
                    if (configFile.exists()) {
                        // Leemos un poco del archivo para buscar la clave
                        try (java.io.BufferedReader fr = new java.io.BufferedReader(
                                new java.io.FileReader(configFile))) {
                            String l;
                            while ((l = fr.readLine()) != null) {
                                if (l.contains("authtoken:"))
                                    return true;
                            }
                        }
                    }
                }
            }

            return false;
        } catch (Exception e) {
            log("[NGROK] Error al validar token: " + e.getMessage(), Color.YELLOW);
            return false;
        }
    }

    // =========================================================================
    // GESTIÓN DE DESAFÍOS (CHALLENGE) Y UI CUSTOM
    // =========================================================================

    private void loadChallengeData() {
        javax.swing.table.DefaultTableModel model = (javax.swing.table.DefaultTableModel) tblChallenge.getModel();
        model.setRowCount(0);

        DBConfig config = DBConfig.getInstance();
        try (Connection conn = DriverManager.getConnection(config.getUrl(),
                config.getUser(), config.getPassword());
                java.sql.Statement stmt = conn.createStatement();
                java.sql.ResultSet rs = stmt.executeQuery(
                        "SELECT id, username, password, password_hash FROM challenge ORDER BY id")) {

            while (rs.next()) {
                model.addRow(new Object[] {
                        rs.getInt("id"), // Columna 0: ID
                        rs.getString("username"), // Columna 1: USERNAME
                        rs.getString("password"), // Columna 2: PASSWORD (plano)
                        rs.getString("password_hash") // Columna 3: PASSWORD HASH (real)
                });
            }
            log("[DB] Challenge cargada (" + model.getRowCount() + " registros)", Color.GREEN);
        } catch (Exception ex) {
            log("[ERROR] Cargando challenge: " + ex.getMessage(), Color.RED);
        }
    }

    private String hashPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return "";
        }

        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-512");
            byte[] encodedHash = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Convertir bytes a hexadecimal
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception ex) {
            log("[ERROR] Fallo al generar hash SHA-512: " + ex.getMessage(), Color.RED);
            return password; // Fallback (no recomendado en producción)
        }
    }

    private void btnAdminChallengesActionPerformed(java.awt.event.ActionEvent evt) {
        System.out.println("[NAV] ADMIN CHALLENGES pulsado → yendo a challenge");

        CardLayout cl = (CardLayout) contentArea.getLayout();
        cl.show(contentArea, "challenge");

        // Cargar datos
        loadChallengeData();

        // Actualizar footer
        updateFooterNav("BACK", e -> navigateTo("monitor"));
    }

    private void radioViewModeActionPerformed(java.awt.event.ActionEvent evt) {
        // La lógica de añadir/quitar columna se maneja aquí
        javax.swing.table.TableColumnModel tcm = tblChallenge.getColumnModel();
        if (radioSecurity.isSelected()) {
            if (tblChallenge.getColumnCount() < 4) {
                log("[UI] Vista de Seguridad activada.", Color.CYAN);
            }
        } else {
            log("[UI] Vista Estándar activada.", Color.CYAN);
        }

        // Actualizar la vista (máscara de contraseñas)
        updateChallengeView();
    }

    private void btnSyncChallengeActionPerformed(java.awt.event.ActionEvent evt) {
        // 0. Forzar el guardado si el usuario aún tiene el cursor en la celda
        if (tblChallenge.isEditing()) {
            tblChallenge.getCellEditor().stopCellEditing();
        }

        int selectedRow = tblChallenge.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Seleccione una fila de la tabla primero.");
            return;
        }

        // 1. Obtener valores actuales de la tabla
        String idStr = tblChallenge.getValueAt(selectedRow, 0).toString();
        String newUser = tblChallenge.getValueAt(selectedRow, 1).toString();
        String newPass = tblChallenge.getValueAt(selectedRow, 2).toString();
        String newHash = hashPassword(newPass);

        DBConfig config = DBConfig.getInstance();

        try (Connection conn = DriverManager.getConnection(config.getUrl(), config.getUser(), config.getPassword())) {
            // 2. Ejecutar el UPDATE (Corregido 'user' a 'username' e ID a int)
            String sql = "UPDATE challenge SET username = ?, password = ?, password_hash = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newUser);
                ps.setString(2, newPass);
                ps.setString(3, newHash);
                ps.setInt(4, Integer.parseInt(idStr));

                log("[DB] Sincronizando ID " + idStr + " (User: " + newUser + ")", Color.CYAN);

                int rowsAffected = ps.executeUpdate();

                // 3. Verificación de cambios
                if (rowsAffected > 0) {
                    JOptionPane.showMessageDialog(this, "Registro actualizado con éxito.", "Éxito",
                            JOptionPane.INFORMATION_MESSAGE);
                    loadChallengeData();
                } else {
                    JOptionPane.showMessageDialog(this,
                            "No se ha modificado ningún parámetro en la tupla seleccionada.",
                            "Aviso", JOptionPane.WARNING_MESSAGE);
                }
            }

        } catch (SQLException | NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Error de base de datos: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
            log("[DB ERROR] " + ex.getMessage(), Color.RED);
        }
    }

    private void btnDeleteChallengeActionPerformed(java.awt.event.ActionEvent evt) {
        int selectedRow = tblChallenge.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Seleccione la fila que desea eliminar.");
            return;
        }

        String idStr = tblChallenge.getValueAt(selectedRow, 0).toString();

        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Está seguro de eliminar la tupla seleccionada?",
                "Confirmar Eliminación",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            DBConfig config = DBConfig.getInstance();
            try (Connection conn = DriverManager.getConnection(config.getUrl(), config.getUser(),
                    config.getPassword())) {
                String sql = "DELETE FROM challenge WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, Integer.parseInt(idStr));
                    int rowsAffected = ps.executeUpdate();

                    if (rowsAffected > 0) {
                        JOptionPane.showMessageDialog(this, "Tupla eliminada correctamente de la base de datos.",
                                "Éxito",
                                JOptionPane.INFORMATION_MESSAGE);
                        loadChallengeData(); // Refrescar la tabla
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "No se pudo eliminar: El registro ya no existe en la base de datos.",
                                "Aviso", JOptionPane.WARNING_MESSAGE);
                    }
                }

            } catch (SQLException | NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Error al eliminar: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
                log("[DB ERROR] " + ex.getMessage(), Color.RED);
            }
        }
    }

    private void setupChallengePanel() {
        if (panelChallenge == null)
            return;

        panelChallenge.setLayout(new java.awt.BorderLayout(10, 10));
        panelChallenge.setOpaque(false);

        // Modelo con 4 columnas
        String[] columnas = { "ID", "USERNAME", "PASSWORD", "PASSWORD HASH" };
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Solo permitimos editar USERNAME (1) y PASSWORD (2)
                return column == 1 || column == 2;
            }
        };
        tblChallenge.setModel(model);

        // Configuración de columnas redimensionables
        tblChallenge.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        tblChallenge.getTableHeader().setReorderingAllowed(true);

        // Anchos iniciales recomendados
        tblChallenge.getColumnModel().getColumn(0).setPreferredWidth(70); // ID
        tblChallenge.getColumnModel().getColumn(1).setPreferredWidth(180); // Username
        tblChallenge.getColumnModel().getColumn(2).setPreferredWidth(220); // Password
        tblChallenge.getColumnModel().getColumn(3).setPreferredWidth(320); // Password Hash

        challengeScroll.setViewportView(tblChallenge);
        tblChallenge.setRowHeight(32);
        tblChallenge.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 13));

        // Controles
        if (panelChallengeControls == null)
            panelChallengeControls = new javax.swing.JPanel();

        panelChallengeControls.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 20, 10));
        panelChallengeControls.setOpaque(false);

        

        radioSecurity.setSelected(true); // ← DEFAULT: SECURITY

        panelChallengeControls.removeAll();
        panelChallengeControls.add(radioStandard);
        panelChallengeControls.add(radioSecurity);
        panelChallengeControls.add(btnSyncChallenge);
        panelChallengeControls.add(btnDeleteChallenge);

        panelChallenge.removeAll();
        panelChallenge.add(panelChallengeControls, java.awt.BorderLayout.NORTH);
        panelChallenge.add(challengeScroll, java.awt.BorderLayout.CENTER);

        loadChallengeData();
        updateChallengeView(); // Aplicar máscara por defecto (Security)

        panelChallenge.revalidate();
        panelChallenge.repaint();
    }

    private void updateChallengeView() {
        if (tblChallenge == null || tblChallenge.getColumnCount() < 4)
            return;

        boolean securityMode = radioSecurity.isSelected();

        // Renderer personalizado para la columna PASSWORD HASH
        tblChallenge.getColumnModel().getColumn(3).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {

                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (securityMode) {
                    setText("********");
                    setForeground(new java.awt.Color(160, 160, 160)); // Gris sutil
                } else {
                    setText(value != null ? value.toString() : "");
                    setForeground(table.getForeground());
                }
                return this;
            }
        });

        String modo = securityMode ? "SECURITY" : "STANDARD";
        log("[UI] Modo cambiado a: " + modo, Color.CYAN);

        tblChallenge.repaint();
    }

    private void sliderBrightnessStateChanged(javax.swing.event.ChangeEvent evt) {
        int mode = sliderBrightness.getValue(); // 0: Gris, 1: Azul, 2: Rojo
        applySystemAlertMode(mode);
        saveConfig(); // Persistimos el cambio inmediatamente
    }

    private void applySystemAlertMode(int alertMode) {
        String label = (alertMode == 0) ? "STEALTH" : (alertMode == 1) ? "TACTICAL" : "OVERDRIVE";
        lblBrightness.setText("SYSTEM ALERT: " + label);

        Color gStart, gEnd, grid;

        // Lógica de colores por Tema y Modo de Alerta
        if (currentThemeMode == 2) { // DEEP SPACE (OSCURO)
            if (alertMode == 0) { // Gris
                gStart = new Color(10, 10, 15);
                gEnd = new Color(25, 25, 30);
                grid = new Color(150, 150, 150, 20);
            } else if (alertMode == 1) { // Azul
                gStart = new Color(2, 6, 23);
                gEnd = new Color(10, 20, 45);
                grid = new Color(0, 242, 255, 15);
            } else { // Rojo
                gStart = new Color(25, 5, 5);
                gEnd = new Color(50, 10, 10);
                grid = new Color(255, 0, 0, 20);
            }
        } else if (currentThemeMode == 1) { // MID BLUE (INTERMEDIO)
            if (alertMode == 0) { // Gris Acero
                gStart = new Color(176, 196, 222);
                gEnd = new Color(112, 128, 144);
                grid = new Color(255, 255, 255, 40);
            } else if (alertMode == 1) { // Azul Intermedio
                gStart = new Color(230, 247, 255);
                gEnd = new Color(186, 231, 255);
                grid = new Color(0, 100, 255, 30);
            } else { // Rojo Atardecer / Magenta
                gStart = new Color(255, 230, 240);
                gEnd = new Color(255, 179, 209);
                grid = new Color(200, 0, 100, 40);
            }
        } else { // LIGHT MODE (CLARO)
            if (alertMode == 0) { // Gris Perla
                gStart = new Color(245, 245, 245);
                gEnd = new Color(210, 210, 210);
                grid = new Color(0, 0, 0, 20);
            } else if (alertMode == 1) { // Azul Pálido
                gStart = new Color(245, 250, 255);
                gEnd = new Color(220, 235, 250);
                grid = new Color(0, 82, 204, 20);
            } else { // Rojo Pastel
                gStart = new Color(255, 240, 240);
                gEnd = new Color(255, 210, 210);
                grid = new Color(255, 50, 50, 20);
            }
        }

        // 1. Actualizar el fondo dinámico (BackgroundPanel)
        if (this.getContentPane() instanceof BackgroundPanel) {
            ((BackgroundPanel) this.getContentPane()).updateTheme(gStart, gEnd, grid);
        }

        // 2. Actualizar paneles internos
        javax.swing.JPanel[] mainPanels = { panelMonitor, panelInicio, panelSettings, panelOperaciones, panelChallenge,
                contentArea };
        for (javax.swing.JPanel p : mainPanels) {
            if (p != null) {
                p.setBackground(gStart);
                p.setOpaque(false);
            }
        }

        // 3. Actualizar estilos de botones críticos
        java.awt.Color accent = getThemeAccentColor();
        String hexAccent = toHexString(accent);
        String hexBg = toHexString(gStart); // Usamos el color de inicio del gradiente como fondo base
        String styleBase = "arc: 0; borderWidth: 1; background: " + hexBg + "; borderColor: " + hexAccent
                + "; foreground: " + hexAccent + "; hoverForeground: #ffffff;";

        if (btnAdminChallenges != null)
            btnAdminChallenges.putClientProperty("FlatLaf.style", styleBase);
        if (btnSyncChallenge != null)
            btnSyncChallenge.putClientProperty("FlatLaf.style", styleBase);
        if (btnDeleteChallenge != null) {
            btnDeleteChallenge.putClientProperty("FlatLaf.style",
                    "arc: 0; borderWidth: 1; background: " + hexBg
                            + "; borderColor: #FF4444; foreground: #FF4444; hoverForeground: #ffffff;");
        }

        // Estilos para botones de Settings
        if (btnFullscreen != null)
            btnFullscreen.putClientProperty("FlatLaf.style", styleBase);
        if (btnUiScale != null)
            btnUiScale.putClientProperty("FlatLaf.style", styleBase);
        if (btnCtrFilter != null)
            btnCtrFilter.putClientProperty("FlatLaf.style", styleBase);

        // 4. Forzar refresco de layouts dinámicos si estamos en la vista de operaciones
        setupDynamicOperacionesLayout();

        this.repaint();
    }

    // Variables generadas por el plan de integración

    private javax.swing.JPanel dimmerOverlay;

}
