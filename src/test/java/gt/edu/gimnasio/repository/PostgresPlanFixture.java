package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

/** PostgreSQL real en un esquema desechable; nunca usa las tablas de public. */
final class PostgresPlanFixture implements AutoCloseable {

    static final String TEST_JDBC_URL = "jdbc:planes-it";

    private final String databaseUrl = requiredEnvironment("TEST_DB_URL");
    private final String databaseUser = requiredEnvironment("TEST_DB_USER");
    private final String databasePassword = requiredEnvironment("TEST_DB_PASSWORD");
    private final String schema = "test_planes_" + UUID.randomUUID().toString().replace("-", "");
    private final List<TrackedResource> resources = new ArrayList<>();
    private final Driver driver = new TrackingDriver();
    private boolean schemaCreated;
    private boolean registered;
    private boolean rejectPlanReads;
    private boolean rejectMemberReads;

    PostgresPlanFixture(boolean migrateLegacyModel) throws SQLException, IOException {
        if (!databaseUrl.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("TEST_DB_URL debe ser una URL JDBC de PostgreSQL.");
        }
        if (!TEST_JDBC_URL.equals(System.getenv("DB_URL"))) {
            throw new IllegalStateException("Ejecuta las pruebas mediante mvn verify -Ppostgres-it.");
        }

        try {
            try (Connection connection = openRawConnection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + quotedSchema());
                schemaCreated = true;
                statement.execute("SET search_path TO " + quotedSchema());
                if (migrateLegacyModel) {
                    statement.execute(readProjectFile("docs/diagramas/der/Sistema_Gimnasio_Modelo_Fisico_v1.0.sql"));
                    statement.execute("INSERT INTO plan (nombre, duracion_dias) VALUES ('Plan previo', 30)");
                    statement.execute(readProjectFile("database/migrations/V1_1__preparar_modelo_multitenant.sql"));
                } else {
                    statement.execute(readProjectFile("docs/diagramas/der/Sistema_Gimnasio_Modelo_Fisico_v1.1_multitenant.sql"));
                    // IDs distintos de 1: el contexto debe resolverlos desde PostgreSQL.
                    statement.execute("""
                            INSERT INTO gimnasio (gimnasio_id, nombre)
                            VALUES (41, 'Gimnasio Principal'), (73, 'Gimnasio Secundario')
                            """);
                }
            }
            DriverManager.registerDriver(driver);
            registered = true;
        } catch (SQLException | IOException | RuntimeException exception) {
            try {
                close();
            } catch (Exception cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    /** Conexión auxiliar: prepara datos ficticios o comprueba el resultado real. */
    Connection openFixtureConnection() throws SQLException {
        Connection connection = openRawConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + quotedSchema());
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    void execute(String sql) throws SQLException {
        try (Connection connection = openFixtureConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    int resourceCount() {
        return resources.size();
    }

    /** Inyección de fallo de recarga; el resto de las operaciones sigue siendo real. */
    void rejectPlanReads(boolean reject) {
        rejectPlanReads = reject;
    }

    void rejectMemberReads(boolean reject) {
        rejectMemberReads = reject;
    }

    void assertResourcesClosed() {
        for (TrackedResource resource : resources) {
            assertTrue(resource.explicitlyClosed, "No se llamó close() sobre " + resource.kind);
        }
    }

    private Connection openRawConnection() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", databaseUser);
        properties.setProperty("password", databasePassword);
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "15");
        return DriverManager.getConnection(databaseUrl, properties);
    }

    private String quotedSchema() {
        // No se admite un nombre proporcionado por el usuario para crear/borrar esquemas.
        if (!schema.matches("test_planes_[a-f0-9]{32}")) {
            throw new IllegalStateException("Nombre de esquema temporal inválido.");
        }
        return '"' + schema + '"';
    }

    private String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("project.root", "."), relativePath));
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Configura " + name + " para la base de datos de pruebas.");
        }
        return value;
    }

    private <T extends AutoCloseable> T track(T delegate, Class<T> type) {
        TrackedResource tracked = new TrackedResource(delegate, type.getSimpleName());
        resources.add(tracked);
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (object, method, args) -> {
            try {
                if (rejectPlanReads && method.getName().equals("prepareStatement")
                        && args[0] instanceof String sql && sql.stripLeading().startsWith("SELECT plan_id")) {
                    throw new SQLException("Fallo de recarga inyectado por la prueba.");
                }
                if (rejectMemberReads && method.getName().equals("prepareStatement")
                        && args[0] instanceof String sql && sql.stripLeading().startsWith("SELECT miembro_id")) {
                    throw new SQLException("Fallo de recarga de miembros inyectado por la prueba.");
                }
                Object result = method.invoke(delegate, args);
                if (method.getName().equals("close")) {
                    tracked.explicitlyClosed = true;
                }
                if (result instanceof PreparedStatement statement) {
                    return track(statement, PreparedStatement.class);
                }
                if (result instanceof ResultSet resultSet) {
                    return track(resultSet, ResultSet.class);
                }
                return result;
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        });
        return type.cast(proxy);
    }

    @Override
    public void close() throws SQLException {
        if (registered) {
            DriverManager.deregisterDriver(driver);
            registered = false;
        }
        try {
            assertResourcesClosed();
        } finally {
            // También limpia si una aserción falla. No modifica ni borra public.
            for (TrackedResource resource : resources.reversed()) {
                if (!resource.explicitlyClosed) {
                    try {
                        resource.delegate.close();
                    } catch (Exception ignored) {
                        // La aserción anterior ya denuncia la fuga; se intenta limpiar el resto.
                    }
                }
            }
            if (schemaCreated) {
                try (Connection connection = openRawConnection(); Statement statement = connection.createStatement()) {
                    statement.execute("DROP SCHEMA " + quotedSchema() + " CASCADE");
                    schemaCreated = false;
                }
            }
        }
    }

    private static final class TrackedResource {
        final AutoCloseable delegate;
        final String kind;
        boolean explicitlyClosed;

        TrackedResource(AutoCloseable delegate, String kind) {
            this.delegate = delegate;
            this.kind = kind;
        }
    }

    /** Sólo existe en src/test; la aplicación normal no puede usar este driver. */
    private final class TrackingDriver implements Driver {
        @Override
        public Connection connect(String url, Properties properties) throws SQLException {
            if (!acceptsURL(url)) {
                return null;
            }
            return track(openFixtureConnection(), Connection.class);
        }

        @Override
        public boolean acceptsURL(String url) {
            return TEST_JDBC_URL.equals(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() { return 1; }

        @Override
        public int getMinorVersion() { return 0; }

        @Override
        public boolean jdbcCompliant() { return false; }

        @Override
        public Logger getParentLogger() { return Logger.getLogger("planes-it"); }
    }
}
