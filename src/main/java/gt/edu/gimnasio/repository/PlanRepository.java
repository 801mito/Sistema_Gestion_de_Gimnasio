package gt.edu.gimnasio.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import gt.edu.gimnasio.config.DatabaseConnection;
import gt.edu.gimnasio.model.Plan;

/** Realiza consultas de sólo lectura sobre los planes de membresía. */
public class PlanRepository {

    private static final String FIND_ALL_SQL = """
            SELECT plan_id, nombre, duracion_dias, plan_activo, plan_creado_en
            FROM plan
            ORDER BY nombre
            """;

    /**
     * Obtiene todos los planes registrados, tanto activos como inactivos.
     *
     * @return lista de planes ordenada alfabéticamente por nombre.
     * @throws SQLException si ocurre un problema de conexión o consulta.
     */
    public List<Plan> findAll() throws SQLException {
        List<Plan> plans = new ArrayList<>();

        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                plans.add(new Plan(
                        resultSet.getInt("plan_id"),
                        resultSet.getString("nombre"),
                        resultSet.getInt("duracion_dias"),
                        resultSet.getBoolean("plan_activo"),
                        resultSet.getTimestamp("plan_creado_en").toLocalDateTime()));
            }
        }

        return plans;
    }
}
