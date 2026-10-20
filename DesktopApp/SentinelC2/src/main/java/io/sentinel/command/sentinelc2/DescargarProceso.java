/**
 * @file DescargarProceso.java
 * @brief Gestor de Sincronización y Despliegue de Dependencias.
 * @details Implementa una arquitectura asíncrona basada en SwingWorker para 
 * descargar, verificar y extraer componentes binarios (servidores, 
 * diccionarios, runtimes) sin interrumpir la fluidez de la interfaz táctica.
 * 
 * @author Felipe Andrés
 * @version 2.1.5
 */

package io.sentinel.command.sentinelc2;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.StandardCopyOption;

/**
 * @class DescargarProceso
 * @brief Motor de Transferencia de Datos Forenses.
 * @details Gestiona el ciclo de vida de una descarga HTTP, incluyendo la
 *          monitorización de progreso, control de errores de red y
 *          autoreparación.
 */
public class DescargarProceso {
    private JDialog dialogo;
    private JProgressBar barra;
    private JLabel etiquetaEstado;
    private JButton btnCancelar;
    private SwingWorker<Void, Integer> worker;

    /**
     * Inicia una transferencia HTTP/HTTPS asíncrona hacia el equipo local.
     * Si el archivo descargado es un .zip, invoca rutinas de descompresión
     * automática.
     * 
     * @param padre     Referencia al JFrame principal para bloqueo modal.
     * @param urlOrigen URL directa HTTP del binario a descargar.
     * @param destino   Ruta absoluta o relativa del archivo final (.zip o .exe).
     */
    public void iniciarDescarga(JFrame padre, String urlOrigen, String destino) {
        // Estética Sentinel
        Color fondo = new Color(15, 15, 15);
        Color acento = new Color(0, 255, 255);

        dialogo = new JDialog(padre, "SENTINEL - SYSTEM UPDATE", true);
        dialogo.setUndecorated(true); // Estilo limpio sin bordes de Windows
        dialogo.getRootPane().setBorder(BorderFactory.createLineBorder(acento, 1));
        dialogo.setLayout(new BorderLayout());
        dialogo.setSize(450, 180);
        dialogo.setLocationRelativeTo(padre);
        dialogo.getContentPane().setBackground(fondo);

        // Componenetes
        etiquetaEstado = new JLabel("Preparando conexión...", JLabel.CENTER);

        barra = new JProgressBar(0, 100);
        barra.setStringPainted(true);
        barra.setForeground(acento);
        barra.setBackground(new Color(40, 40, 40));

        btnCancelar = new JButton("CANCELAR");
        btnCancelar.setFocusPainted(false);
        btnCancelar.addActionListener(e -> {
            if (worker != null)
                worker.cancel(true);
            dialogo.dispose();
        });

        // Layout
        JPanel panelCentral = new JPanel(new GridLayout(2, 1, 10, 10));
        panelCentral.setOpaque(false);
        panelCentral.setBorder(BorderFactory.createEmptyBorder(20, 30, 10, 30));
        panelCentral.add(etiquetaEstado);
        panelCentral.add(barra);

        JPanel panelBoton = new JPanel();
        panelBoton.setOpaque(false);
        panelBoton.add(btnCancelar);

        dialogo.add(panelCentral, BorderLayout.CENTER);
        dialogo.add(panelBoton, BorderLayout.SOUTH);

        // Lógica de red
        ejecutarTareaEnSegundoPlano(urlOrigen, destino);
        dialogo.setVisible(true);
    }

    private void ejecutarTareaEnSegundoPlano(String urlString, String destino) {
        worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Verificar si el archivo existe
                File file = new File(destino);
                if (file.exists()) {
                    int resp = JOptionPane.showConfirmDialog(dialogo, "El archivo ya existe. ¿Desea sobreescribirlo?",
                            "Sentinel Warning", JOptionPane.YES_NO_OPTION);
                    if (resp != JOptionPane.YES_OPTION) {
                        worker.cancel(true);
                        return null;
                    }
                }

                URL url = java.net.URI.create(urlString).toURL();
                HttpURLConnection conexion = (HttpURLConnection) url.openConnection();

                // Configurar tiempos de espera (importante para "congelamientos")
                conexion.setConnectTimeout(7000); // 7 segundos para conectar

                int codigoRespuesta = conexion.getResponseCode();

                // Validar si el archivo existe (Código 200 OK)
                if (codigoRespuesta != java.net.HttpURLConnection.HTTP_OK) {
                    throw new Exception("Error del servidor: " + codigoRespuesta);
                    // Si es 404 o 500, saltará directamente al método done()
                }

                // Obtener el tamaño total para la barra
                long tamanoArchivo = conexion.getContentLength();

                try (InputStream entrada = new BufferedInputStream(url.openStream());
                        FileOutputStream salida = new FileOutputStream(destino)) {

                    byte[] datos = new byte[8192];
                    long totalDescargado = 0;
                    int leido;

                    // Si tamanoArchivo <= 0, publicaremos valores negativos representando KB
                    // descargados indicando tamaño desconocido
                    if (tamanoArchivo <= 0) {
                        publish(-1);
                    }

                    while ((leido = entrada.read(datos)) != -1) {

                        if (isCancelled())
                            return null;
                        totalDescargado += leido;
                        salida.write(datos, 0, leido);

                        // 2. Calcular porcentaje y enviar a la interfaz
                        if (tamanoArchivo > 0) {
                            int progreso = (int) ((totalDescargado * 100) / tamanoArchivo);
                            publish(progreso);
                        } else {
                            // Enviar KB en forma de número negativo para indicar modo indeterminado
                            publish((int) -(totalDescargado / 1024));
                        }
                    }
                }
                return null;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                // Esto corre en el hilo de la interfaz (seguro para Swing)
                int ultimoValor = chunks.get(chunks.size() - 1);

                if (ultimoValor < 0) {
                    if (!barra.isIndeterminate())
                        barra.setIndeterminate(true);
                    etiquetaEstado.setText("Descargando actualización: " + (-ultimoValor) + " KB");
                } else {
                    barra.setIndeterminate(false);
                    barra.setValue(ultimoValor);
                    etiquetaEstado.setText("Descargando actualización: " + ultimoValor + "%");
                }
            }

            // Aquí gestionamos los fallos de internet o archivos no encontrados
            @Override
            protected void done() {
                try {
                    if (!isCancelled()) {
                        get(); // Si doInBackground lanzó una excepción, get() la dispara aquí
                        etiquetaEstado.setText("¡Sincronización completa! Iniciando instalador...");

                        // LANZAR AUTOEJECUCIÓN
                        ejecutarArchivo(destino);

                        etiquetaEstado.setText("¡Descarga completada!");
                        etiquetaEstado.setForeground(new Color(0, 255, 255)); // Color cian
                        btnCancelar.setText("CERRAR");
                    }

                } catch (Exception e) {
                    String mensajeError = (e.getCause() != null) ? e.getCause().getMessage() : e.getMessage();
                    etiquetaEstado.setText("FALLO: " + mensajeError);
                    etiquetaEstado.setForeground(Color.RED);

                    // IMPORTANTE: Cambiar el texto del botón para que puedan salir
                    btnCancelar.setText("CERRAR");
                    dialogo.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                }
            }
        };

        worker.execute();

    }

    public boolean hayInternet() {
        try {
            // Conecta al DNS de Google en el puerto 53 (estándar para chequeos rápidos)
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress("8.8.8.8", 53), 2000);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void ejecutarArchivo(String ruta) {
        File archivo = new File(ruta);
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (!archivo.exists()) {
                throw new Exception("El archivo no se encuentra en la ruta especificada.");
            }

            if (ruta.toLowerCase().endsWith(".zip")) {
                extraerZip(archivo);
                return;
            }

            if (os.contains("win")) {
                if (ruta.toLowerCase().endsWith(".msi") || ruta.toLowerCase().endsWith(".exe")) {
                    // Usar Desktop para instaladores de sistema
                    if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                        java.awt.Desktop.getDesktop().open(archivo);
                    } else {
                        new ProcessBuilder("cmd", "/c", "start", "", archivo.getAbsolutePath()).start();
                    }
                } else {
                    archivo.setExecutable(true, false);
                    new ProcessBuilder(archivo.getAbsolutePath()).start();
                }
            } else if (os.contains("mac")) {
                if (ruta.toLowerCase().endsWith(".pkg")) {
                    if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                        java.awt.Desktop.getDesktop().open(archivo);
                    } else {
                        new ProcessBuilder("open", archivo.getAbsolutePath()).start();
                    }
                } else {
                    archivo.setExecutable(true, false);
                    new ProcessBuilder("open", archivo.getAbsolutePath()).start();
                }
            } else {
                // Lógica para Linux
                archivo.setExecutable(true, false); // Dar permisos de ejecución

                if (ruta.endsWith(".sh")) {
                    new ProcessBuilder("sh", ruta).start();
                } else {
                    new ProcessBuilder(ruta).start();
                }
            }
        } catch (Exception e) {
            // Si falla la ejecución automática, lo registramos
            javax.swing.JOptionPane.showMessageDialog(
                    dialogo, // Usamos el JDialog actual como padre para centrarlo
                    "No se pudo ejecutar el instalador automáticamente.\n" +
                            "Sistema detectado: " + os.toUpperCase() + "\n" +
                            "Error: " + e.getMessage() + "\n\n" +
                            "Por favor, intenta ejecutarlo manualmente desde:\n" + ruta,
                    "SENTINEL C2 - EXECUTION WARNING",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

    private void extraerZip(File zipFile) throws Exception {
        String destDir = zipFile.getParent() + File.separator + zipFile.getName().replaceAll("\\.zip$", "");
        File dir = new File(destDir);
        if (!dir.exists())
            dir.mkdirs();

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(zipFile))) {
            java.util.zip.ZipEntry zipEntry = zis.getNextEntry();
            byte[] buffer = new byte[8192];
            while (zipEntry != null) {
                File newFile = new File(destDir, zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0)
                            fos.write(buffer, 0, len);
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }

        // Copiar ejecutables al nivel del zip (Downloads/) para que sean detectables
        // sin necesidad de estar en PATH del sistema.
        copiarEjecutablesAlPadre(dir, zipFile.getParentFile());

        // Abrir la carpeta extraída
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            new ProcessBuilder("explorer.exe", destDir).start();
        } else {
            java.awt.Desktop.getDesktop().open(dir);
        }
    }

    /**
     * Copia recursivamente cualquier .exe encontrado dentro de {@code origen}
     * directamente a {@code destino} (Downloads/). Así el binario queda en una
     * ruta conocida y fija que la app puede buscar sin depender del PATH.
     */
    private void copiarEjecutablesAlPadre(File origen, File destino) {
        if (origen == null || !origen.isDirectory()) return;
        for (File f : origen.listFiles()) {
            if (f.isDirectory()) {
                copiarEjecutablesAlPadre(f, destino); // búsqueda recursiva
            } else if (f.getName().toLowerCase().endsWith(".exe")
                    || (!System.getProperty("os.name").toLowerCase().contains("win")
                        && f.canExecute() && !f.getName().contains("."))) {
                File target = new File(destino, f.getName());
                try {
                    java.nio.file.Files.copy(f.toPath(), target.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    target.setExecutable(true, false);
                } catch (Exception ignored) {}
            }
        }
    }
}
