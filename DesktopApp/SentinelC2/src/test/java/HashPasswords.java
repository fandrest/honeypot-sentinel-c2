import io.sentinel.command.sentinelc2.DBConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class HashPasswords {
    public static void main(String[] args) {
        try {
            DBConfig db = DBConfig.getInstance();
            Connection conn = DriverManager.getConnection(db.getUrl(), db.getUser(), db.getPassword());
            String sql = "UPDATE capturas SET password = SHA2(password, 512) WHERE LENGTH(password) < 128";
            PreparedStatement stmt = conn.prepareStatement(sql);
            int rows = stmt.executeUpdate();
            System.out.println("Filas actualizadas (hashes generados): " + rows);
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
