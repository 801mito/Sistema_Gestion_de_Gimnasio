package gt.edu.gimnasio.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Punto de entrada para comprobar la configuración JDBC con una consulta
 * inocua a la base de datos.
 */
public final class DatabaseConnectionVerifier {

    private DatabaseConnectionVerifier() {
    }

    public static void main(String[] args) {
        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next() && resultSet.getInt(1) == 1) {
                System.out.println("Conexión JDBC a PostgreSQL verificada correctamente.");
                return;
            }

            System.err.println("PostgreSQL respondió una verificación inesperada.");
        } catch (IllegalStateException exception) {
            System.err.println("Configuración incompleta: " + exception.getMessage());
        } catch (SQLException exception) {
            System.err.println("No fue posible conectar con PostgreSQL: " + exception.getMessage());
        }
    }
}
