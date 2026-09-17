package gt.edu.gimnasio.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Guarda y consulta códigos de acceso asociados a membresías. */
public class AccessCodeRepository {

    private static final String EXISTS_BY_CODE_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM codigo_acceso
                WHERE codigo = ?
            )
            """;

    private static final String SAVE_SQL = """
            INSERT INTO codigo_acceso (membresia_id, codigo)
            VALUES (?, ?)
            """;

    /** Indica si ya existe un código con el mismo valor. */
    public boolean existsByCode(Connection connection, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(EXISTS_BY_CODE_SQL)) {
            statement.setString(1, code);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    /** Registra un código activo para una membresía. */
    public void save(Connection connection, int membershipId, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SAVE_SQL)) {
            statement.setInt(1, membershipId);
            statement.setString(2, code);
            statement.executeUpdate();
        }
    }
}
