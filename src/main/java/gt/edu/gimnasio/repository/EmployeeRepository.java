package gt.edu.gimnasio.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import gt.edu.gimnasio.config.DatabaseConnection;
import gt.edu.gimnasio.model.Employee;
import gt.edu.gimnasio.service.PasswordHasher;

/** Persiste cuentas de empleados y consulta sus credenciales para el futuro login. */
public class EmployeeRepository {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9._-]{3,100}");
    private static final String SAVE_SQL = """
            INSERT INTO empleado (gimnasio_id, usuario, contrasena_hash)
            VALUES (?, ?, ?)
            RETURNING empleado_id
            """;
    private static final String FIND_BY_USERNAME_SQL = """
            SELECT e.empleado_id, e.gimnasio_id, g.nombre AS gimnasio_nombre,
                   e.usuario, e.contrasena_hash, e.empleado_activo,
                   g.gimnasio_activo, e.empleado_creado_en
            FROM empleado e
            JOIN gimnasio g ON g.gimnasio_id = e.gimnasio_id
            WHERE e.usuario = ?
            """;

    private final PasswordHasher passwordHasher;

    public EmployeeRepository() {
        this(new PasswordHasher());
    }

    public EmployeeRepository(PasswordHasher passwordHasher) {
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
    }

    /** Inserta la cuenta vinculada a un gimnasio existente, sin conservar la contraseña. */
    public int save(int gymId, String username, char[] password) throws SQLException {
        if (gymId <= 0) {
            throw new IllegalArgumentException("El gimnasio debe ser válido.");
        }
        String normalized = normalizeUsername(username);
        String passwordHash = passwordHasher.hash(password);

        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(SAVE_SQL)) {

            statement.setInt(1, gymId);
            statement.setString(2, normalized);
            statement.setString(3, passwordHash);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("empleado_id");
                }
            }
        }

        throw new SQLException("PostgreSQL no devolvió el empleado registrado.");
    }

    /** Busca por usuario global, sin recibir ni confiar en un gimnasio elegido. */
    public Optional<Credentials> findByUsername(String username) throws SQLException {
        String normalized = normalizeUsername(username);

        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_USERNAME_SQL)) {

            statement.setString(1, normalized);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Employee employee = new Employee(
                            resultSet.getInt("empleado_id"),
                            resultSet.getInt("gimnasio_id"),
                            resultSet.getString("gimnasio_nombre"),
                            resultSet.getString("usuario"),
                            resultSet.getBoolean("empleado_activo"),
                            resultSet.getBoolean("gimnasio_activo"),
                            resultSet.getTimestamp("empleado_creado_en").toLocalDateTime());
                    return Optional.of(new Credentials(employee, resultSet.getString("contrasena_hash")));
                }
            }
        }

        return Optional.empty();
    }

    private static String normalizeUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException("El usuario no puede estar vacío.");
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("El usuario debe tener entre 3 y 100 caracteres simples.");
        }
        return normalized;
    }

    /** La credencial se mantiene separada del modelo de identidad para no mostrarse en la interfaz. */
    public static final class Credentials {
        private final Employee employee;
        private final String passwordHash;

        private Credentials(Employee employee, String passwordHash) {
            this.employee = employee;
            this.passwordHash = passwordHash;
        }

        public Employee getEmployee() {
            return employee;
        }

        public String getPasswordHash() {
            return passwordHash;
        }
    }
}
