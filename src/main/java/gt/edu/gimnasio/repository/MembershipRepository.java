package gt.edu.gimnasio.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
}
