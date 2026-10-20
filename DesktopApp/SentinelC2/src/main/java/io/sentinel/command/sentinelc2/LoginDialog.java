package io.sentinel.command.sentinelc2;

import java.awt.Color;
import java.awt.Font;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.swing.JOptionPane;

/**
 * Diálogo de autenticación de operador del Sentinel C2.
 * Verifica usuario y contraseña (SHA-512) contra la tabla challenge de MySQL.
 * Debe mostrarse antes de abrir MainFrame.
 */
public class LoginDialog extends javax.swing.JDialog {

    private boolean loginExitoso = false;

    public LoginDialog(java.awt.Frame parent) {
        super(parent, true);
        initComponents();
        aplicarEstiloDeepSpace();
        getRootPane().setDefaultButton(btnLogin);
        setLocationRelativeTo(parent);
    }

    /** @return true si el operador se autenticó correctamente. */
    public boolean isLoginExitoso() {
        return loginExitoso;
    }

    private void aplicarEstiloDeepSpace() {
        Color bg        = new Color(10, 25, 47);
        Color fieldBg   = new Color(15, 35, 55);
        Color cyan      = new Color(0, 242, 255);
        Color labelFg   = new Color(180, 200, 220);
        Color subtitleFg = new Color(100, 120, 140);

        getContentPane().setBackground(bg);
        panelMain.setBackground(bg);
        panelMain.setBorder(javax.swing.BorderFactory.createLineBorder(cyan, 2));

        lblTitle.setForeground(cyan);
        lblTitle.setFont(new Font("Monospaced", Font.BOLD, 18));

        lblSubtitle.setForeground(subtitleFg);
        lblSubtitle.setFont(new Font("Monospaced", Font.ITALIC, 10));

        separador.setForeground(new Color(0, 100, 100));

        lblUser.setForeground(labelFg);
        lblUser.setFont(new Font("Monospaced", Font.BOLD, 11));

        lblPassword.setForeground(labelFg);
        lblPassword.setFont(new Font("Monospaced", Font.BOLD, 11));

        javax.swing.border.Border fieldBorder = javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(new Color(0, 150, 150), 1),
            javax.swing.BorderFactory.createEmptyBorder(5, 8, 5, 8));

        txtUser.setBackground(fieldBg);
        txtUser.setForeground(cyan);
        txtUser.setCaretColor(Color.CYAN);
        txtUser.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtUser.setBorder(fieldBorder);

        pwdPassword.setBackground(fieldBg);
        pwdPassword.setForeground(cyan);
        pwdPassword.setCaretColor(Color.CYAN);
        pwdPassword.setFont(new Font("Monospaced", Font.PLAIN, 12));
        pwdPassword.setBorder(fieldBorder);

        btnLogin.putClientProperty("FlatLaf.style",
            "arc: 0; borderWidth: 1; background: #00000000; borderColor: #00F2FF; foreground: #00F2FF; hoverForeground: #ffffff;");
        btnCancel.putClientProperty("FlatLaf.style",
            "arc: 0; borderWidth: 1; background: #00000000; borderColor: #555555; foreground: #555555; hoverForeground: #ffffff;");
    }

    private String sha512(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] bytes = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void intentarLogin() {
        String usuario  = txtUser.getText().trim();
        String password = new String(pwdPassword.getPassword());

        if (usuario.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Introduce usuario y contraseña.",
                "Campos requeridos",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        String hashPassword = sha512(password);

        DBConfig db = DBConfig.getInstance();
        try (Connection conn = DriverManager.getConnection(
                db.getUrl(), db.getUser(), db.getPassword());
             PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM challenge WHERE username = ? AND password_hash = ?")) {

            ps.setString(1, usuario);
            ps.setString(2, hashPassword);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    loginExitoso = true;
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Credenciales incorrectas. Acceso denegado.",
                        "Error de autenticación",
                        JOptionPane.ERROR_MESSAGE);
                    pwdPassword.setText("");
                    pwdPassword.requestFocus();
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "No se pudo conectar a la base de datos:\n" + e.getMessage(),
                "Error de conexión",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panelMain   = new javax.swing.JPanel();
        lblTitle    = new javax.swing.JLabel();
        lblSubtitle = new javax.swing.JLabel();
        separador   = new javax.swing.JSeparator();
        lblUser     = new javax.swing.JLabel();
        txtUser     = new javax.swing.JTextField();
        lblPassword = new javax.swing.JLabel();
        pwdPassword = new javax.swing.JPasswordField();
        btnLogin    = new javax.swing.JButton();
        btnCancel   = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Sentinel C2 - Autenticación");
        setModal(true);
        setResizable(false);

        panelMain.setBackground(new java.awt.Color(10, 25, 47));

        lblTitle.setFont(new java.awt.Font("Monospaced", 1, 18)); // NOI18N
        lblTitle.setForeground(new java.awt.Color(0, 242, 255));
        lblTitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblTitle.setText("SENTINEL C2");

        lblSubtitle.setFont(new java.awt.Font("Monospaced", 2, 10)); // NOI18N
        lblSubtitle.setForeground(new java.awt.Color(100, 120, 140));
        lblSubtitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblSubtitle.setText("AUTENTICACIÓN DE OPERADOR");

        separador.setForeground(new java.awt.Color(0, 100, 100));

        lblUser.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        lblUser.setForeground(new java.awt.Color(180, 200, 220));
        lblUser.setText("USUARIO");

        txtUser.setBackground(new java.awt.Color(15, 35, 55));
        txtUser.setForeground(new java.awt.Color(0, 242, 255));
        txtUser.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N

        lblPassword.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        lblPassword.setForeground(new java.awt.Color(180, 200, 220));
        lblPassword.setText("CONTRASEÑA");

        pwdPassword.setBackground(new java.awt.Color(15, 35, 55));
        pwdPassword.setForeground(new java.awt.Color(0, 242, 255));
        pwdPassword.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N

        btnLogin.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        btnLogin.setForeground(new java.awt.Color(0, 242, 255));
        btnLogin.setText("INICIAR SESIÓN");
        btnLogin.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLoginActionPerformed(evt);
            }
        });

        btnCancel.setFont(new java.awt.Font("Monospaced", 1, 11)); // NOI18N
        btnCancel.setForeground(new java.awt.Color(100, 100, 100));
        btnCancel.setText("CANCELAR");
        btnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCancelActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout panelMainLayout = new javax.swing.GroupLayout(panelMain);
        panelMain.setLayout(panelMainLayout);
        panelMainLayout.setHorizontalGroup(
            panelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelMainLayout.createSequentialGroup()
                .addGap(30, 30, 30)
                .addGroup(panelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblTitle,    javax.swing.GroupLayout.DEFAULT_SIZE, 340, Short.MAX_VALUE)
                    .addComponent(lblSubtitle, javax.swing.GroupLayout.DEFAULT_SIZE, 340, Short.MAX_VALUE)
                    .addComponent(separador)
                    .addComponent(lblUser)
                    .addComponent(txtUser)
                    .addComponent(lblPassword)
                    .addComponent(pwdPassword)
                    .addGroup(panelMainLayout.createSequentialGroup()
                        .addComponent(btnLogin,  javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(30, 30, 30))
        );
        panelMainLayout.setVerticalGroup(
            panelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelMainLayout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addComponent(lblTitle)
                .addGap(8, 8, 8)
                .addComponent(lblSubtitle)
                .addGap(12, 12, 12)
                .addComponent(separador, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addComponent(lblUser)
                .addGap(6, 6, 6)
                .addComponent(txtUser,      javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addComponent(lblPassword)
                .addGap(6, 6, 6)
                .addComponent(pwdPassword,  javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(20, 20, 20)
                .addGroup(panelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnLogin,  javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(25, 25, 25))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(panelMain, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(panelMain, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnLoginActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLoginActionPerformed
        intentarLogin();
    }//GEN-LAST:event_btnLoginActionPerformed

    private void btnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCancelActionPerformed
        System.exit(0);
    }//GEN-LAST:event_btnCancelActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnCancel;
    private javax.swing.JButton btnLogin;
    private javax.swing.JLabel lblPassword;
    private javax.swing.JLabel lblSubtitle;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JLabel lblUser;
    private javax.swing.JPanel panelMain;
    private javax.swing.JPasswordField pwdPassword;
    private javax.swing.JSeparator separador;
    private javax.swing.JTextField txtUser;
    // End of variables declaration//GEN-END:variables
}
