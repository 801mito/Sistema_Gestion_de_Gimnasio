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

    private static final String EXISTS_BY_NAME_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM plan
                WHERE LOWER(nombre) = LOWER(?)
            )
            """;

    private static final String SAVE_SQL = """
            INSERT INTO plan (nombre, duracion_dias)
            VALUES (?, ?)
            RETURNING plan_id, nombre, duracion_dias, plan_activo, plan_creado_en
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
                plans.add(mapPlan(resultSet));
            }
        }

        return plans;
    }

    /** Verifica si ya hay un plan con el mismo nombre, sin diferenciar mayúsculas. */
    public boolean existsByName(String name) throws SQLException {
        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_BY_NAME_SQL)) {

            statement.setString(1, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    /** Inserta un plan activo y devuelve sus datos generados por PostgreSQL. */
    public Plan save(String name, int durationDays) throws SQLException {
        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(SAVE_SQL)) {

            statement.setString(1, name);
            statement.setInt(2, durationDays);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapPlan(resultSet);
                }
            }
        }

        throw new SQLException("PostgreSQL no devolvió el plan registrado.");
    }

    private Plan mapPlan(ResultSet resultSet) throws SQLException {
        return new Plan(
                resultSet.getInt("plan_id"),
                resultSet.getString("nombre"),
                resultSet.getInt("duracion_dias"),
                resultSet.getBoolean("plan_activo"),
                resultSet.getTimestamp("plan_creado_en").toLocalDateTime());
    }
}
