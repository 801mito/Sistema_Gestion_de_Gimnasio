package gt.edu.gimnasio.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import gt.edu.gimnasio.config.DatabaseConnection;
import gt.edu.gimnasio.model.AccessCode;

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

    private static final String FIND_ALL_SQL = """
            SELECT codigo_acceso.codigo_acceso_id,
                   codigo_acceso.membresia_id,
                   miembro.nombres || ' ' || miembro.apellidos AS miembro_nombre,
                   plan.nombre AS plan_nombre,
                   codigo_acceso.codigo,
                   codigo_acceso.codigo_activo,
                   codigo_acceso.codigo_creado_en
            FROM codigo_acceso
            INNER JOIN membresia ON membresia.membresia_id = codigo_acceso.membresia_id
            INNER JOIN miembro ON miembro.miembro_id = membresia.miembro_id
            INNER JOIN plan ON plan.plan_id = membresia.plan_id
            ORDER BY codigo_acceso.codigo_creado_en DESC, codigo_acceso.codigo_acceso_id DESC
            """;

    /** Obtiene los códigos registrados con su miembro y plan asociados. */
    public List<AccessCode> findAll() throws SQLException {
        List<AccessCode> accessCodes = new ArrayList<>();

        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                accessCodes.add(new AccessCode(
                        resultSet.getInt("codigo_acceso_id"),
                        resultSet.getInt("membresia_id"),
                        resultSet.getString("miembro_nombre"),
                        resultSet.getString("plan_nombre"),
                        resultSet.getString("codigo"),
                        resultSet.getBoolean("codigo_activo"),
                        resultSet.getObject("codigo_creado_en", java.time.LocalDateTime.class)));
            }
        }

        return accessCodes;
    }

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
