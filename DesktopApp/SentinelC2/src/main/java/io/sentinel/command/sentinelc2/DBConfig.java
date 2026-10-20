package io.sentinel.command.sentinelc2;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Lector centralizado de configuracion de base de datos.
 * Lee db.properties al primer acceso y expone los valores
 * como metodos de instancia. Si el archivo no existe usa
 * los valores por defecto (localhost/root/honeypot_db).
 */
public class DBConfig {

    private static DBConfig instance;

    private final String host;
    private final String port;
    private final String database;
    private final String user;
    private final String password;

    private DBConfig() {
        Properties props = new Properties();
        File configFile = new File("db.properties");
        if (configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                props.load(in);
            } catch (IOException e) {
                // Si hay error de lectura se usan los valores por defecto
            }
        }
        // Prioridad: Variables de entorno del sistema -> db.properties -> Valores por defecto
        String envHost = System.getenv("DB_HOST");
        host = (envHost != null && !envHost.trim().isEmpty()) ? envHost.trim() : props.getProperty("db.host", "localhost");

        String envPort = System.getenv("DB_PORT");
        port = (envPort != null && !envPort.trim().isEmpty()) ? envPort.trim() : props.getProperty("db.port", "3306");

        String envDb = System.getenv("DB_NAME");
        database = (envDb != null && !envDb.trim().isEmpty()) ? envDb.trim() : props.getProperty("db.name", "honeypot_db");

        String envUser = System.getenv("DB_USER");
        user = (envUser != null && !envUser.trim().isEmpty()) ? envUser.trim() : props.getProperty("db.user", "root");

        String envPass = System.getenv("DB_PASSWORD");
        password = (envPass != null) ? envPass : props.getProperty("db.password", "");
    }

    public static DBConfig getInstance() {
        if (instance == null) {
            instance = new DBConfig();
        }
        return instance;
    }

    /** URL JDBC apuntando directamente a la base de datos del honeypot. */
    public String getUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
    }

    /** URL JDBC sin base de datos (para verificar que MySQL esta activo). */
    public String getServerUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/";
    }

    public String getUser()     { return user; }
    public String getPassword() { return password; }
    public String getDatabase() { return database; }
    public String getHost()     { return host; }
    public String getPort()     { return port; }
}
