package gt.edu.gimnasio.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import gt.edu.gimnasio.config.DatabaseConnection;
import gt.edu.gimnasio.model.Membership;

/** Realiza consultas de lectura sobre las membresías y su historial. */
public class MembershipRepository {

    private static final String FIND_ALL_SQL = """
            SELECT membresia.membresia_id,
                   miembro.miembro_id,
                   miembro.nombres || ' ' || miembro.apellidos AS miembro_nombre,
                   plan.nombre AS plan_nombre,
                   membresia.estado,
                   membresia.fecha_inicio,
                   membresia.fecha_fin
            FROM membresia
            INNER JOIN miembro ON miembro.miembro_id = membresia.miembro_id
            INNER JOIN plan ON plan.plan_id = membresia.plan_id
            ORDER BY membresia.fecha_inicio DESC, membresia.membresia_id DESC
            """;

    private static final String EXISTS_ACTIVE_FOR_MEMBER_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM membresia
                WHERE miembro_id = ?
                  AND estado = 'ACTIVA'
            )
            """;

    private static final String SAVE_SQL = """
            INSERT INTO membresia (plan_id, miembro_id, estado, fecha_inicio, fecha_fin)
            VALUES (?, ?, 'ACTIVA', ?, ?)
            """;

    /** Obtiene el historial de membresías junto con su miembro y plan asociados. */
    public List<Membership> findAll() throws SQLException {
        List<Membership> memberships = new ArrayList<>();

        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                memberships.add(new Membership(
                        resultSet.getInt("membresia_id"),
                        resultSet.getInt("miembro_id"),
                        resultSet.getString("miembro_nombre"),
                        resultSet.getString("plan_nombre"),
                        resultSet.getString("estado"),
                        resultSet.getObject("fecha_inicio", java.time.LocalDate.class),
                        resultSet.getObject("fecha_fin", java.time.LocalDate.class)));
            }
        }

        return memberships;
    }

    /** Indica si el miembro ya cuenta con una membresía activa. */
    public boolean hasActiveMembership(int memberId) throws SQLException {
        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_ACTIVE_FOR_MEMBER_SQL)) {

            statement.setInt(1, memberId);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    /** Registra una membresía activa y devuelve su identificador generado. */
    public int save(Connection connection, int memberId, int planId, java.time.LocalDate startDate,
                    java.time.LocalDate endDate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SAVE_SQL, Statement.RETURN_GENERATED_KEYS)) {

            statement.setInt(1, planId);
            statement.setInt(2, memberId);
            statement.setObject(3, startDate);
            statement.setObject(4, endDate);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }

        throw new SQLException("No fue posible obtener el identificador de la membresía registrada.");
    }
}
