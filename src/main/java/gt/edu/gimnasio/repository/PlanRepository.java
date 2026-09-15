package gt.edu.gimnasio.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import gt.edu.gimnasio.config.DatabaseConnection;
import gt.edu.gimnasio.model.Plan;

/** Realiza operaciones JDBC sobre los planes de membresía. */
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

    private static final String FIND_ACTIVE_SQL = """
            SELECT plan_id, nombre, duracion_dias, plan_activo, plan_creado_en
            FROM plan
            WHERE plan_activo = TRUE
            ORDER BY nombre
            """;

    private static final String EXISTS_BY_NAME_EXCLUDING_ID_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM plan
                WHERE LOWER(nombre) = LOWER(?)
                  AND plan_id <> ?
            )
            """;

    private static final String UPDATE_SQL = """
            UPDATE plan
            SET nombre = ?, duracion_dias = ?
            WHERE plan_id = ?
            RETURNING plan_id, nombre, duracion_dias, plan_activo, plan_creado_en
            """;

    private static final String UPDATE_ACTIVE_STATUS_SQL = """
            UPDATE plan
            SET plan_activo = ?
            WHERE plan_id = ?
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

    /** Obtiene sólo los planes que pueden asignarse a una membresía nueva. */
    public List<Plan> findActive() throws SQLException {
        List<Plan> plans = new ArrayList<>();

        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ACTIVE_SQL);
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

    /** Comprueba duplicados de nombre al editar un plan existente. */
    public boolean existsByNameExcludingId(String name, int id) throws SQLException {
        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_BY_NAME_EXCLUDING_ID_SQL)) {

            statement.setString(1, name);
            statement.setInt(2, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    /** Actualiza el nombre y duración de un plan, sin alterar su estado. */
    public Plan update(int id, String name, int durationDays) throws SQLException {
        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {

            statement.setString(1, name);
            statement.setInt(2, durationDays);
            statement.setInt(3, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapPlan(resultSet);
                }
            }
        }

        throw new SQLException("No se encontró el plan que se desea actualizar.");
    }

    /** Activa o desactiva un plan sin eliminarlo de la base de datos. */
    public void updateActiveStatus(int id, boolean active) throws SQLException {
        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_ACTIVE_STATUS_SQL)) {

            statement.setBoolean(1, active);
            statement.setInt(2, id);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("No se encontró el plan cuyo estado se desea cambiar.");
            }
        }
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
