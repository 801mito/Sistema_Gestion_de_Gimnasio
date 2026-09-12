package gt.edu.gimnasio.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Crea conexiones JDBC a PostgreSQL a partir de variables de entorno.
 */
public final class DatabaseConnection {

    public static final String URL_ENVIRONMENT_VARIABLE = "DB_URL";
    public static final String USER_ENVIRONMENT_VARIABLE = "DB_USER";
    public static final String PASSWORD_ENVIRONMENT_VARIABLE = "DB_PASSWORD";

    private DatabaseConnection() {
    }

    /**
     * Abre una conexión nueva. La persona que llama debe cerrarla con
     * try-with-resources.
     *
     * @return conexión JDBC a PostgreSQL.
     * @throws SQLException si PostgreSQL no acepta la conexión.
     * @throws IllegalStateException si falta una variable de entorno requerida.
     */
    public static Connection openConnection() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", requiredEnvironmentVariable(USER_ENVIRONMENT_VARIABLE));
        properties.setProperty("password", requiredEnvironmentVariable(PASSWORD_ENVIRONMENT_VARIABLE));
        properties.setProperty("connectTimeout", "5");

        return DriverManager.getConnection(
                requiredEnvironmentVariable(URL_ENVIRONMENT_VARIABLE), properties);
    }

    private static String requiredEnvironmentVariable(String variableName) {
        String value = System.getenv(variableName);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable de entorno " + variableName + ". "
                            + "Consulta el README para configurarla.");
        }

        return value;
    }
}
