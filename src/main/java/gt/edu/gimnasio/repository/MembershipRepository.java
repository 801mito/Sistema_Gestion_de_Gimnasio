package gt.edu.gimnasio.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import gt.edu.gimnasio.config.DatabaseConnection;
import gt.edu.gimnasio.model.Membership;
import gt.edu.gimnasio.service.GymContext;

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
            WHERE membresia.gimnasio_id = ?
            ORDER BY membresia.fecha_inicio DESC, membresia.membresia_id DESC
            """;

    private static final String EXISTS_ACTIVE_FOR_MEMBER_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM membresia
                WHERE gimnasio_id = ?
                  AND miembro_id = ?
                  AND estado = 'ACTIVA'
            )
            """;

    private static final String FIND_ACTIVE_GYM_SQL = """
            SELECT 1 FROM gimnasio
            WHERE gimnasio_id = ? AND gimnasio_activo = TRUE
            FOR SHARE
            """;

    private static final String FIND_MEMBER_SQL = """
            SELECT 1 FROM miembro
            WHERE gimnasio_id = ? AND miembro_id = ?
            FOR UPDATE
            """;

    private static final String FIND_ACTIVE_PLAN_SQL = """
            SELECT duracion_dias FROM plan
            WHERE gimnasio_id = ? AND plan_id = ? AND plan_activo = TRUE
            FOR SHARE
            """;

    private static final String SAVE_SQL = """
            INSERT INTO membresia (gimnasio_id, plan_id, miembro_id, estado, fecha_inicio, fecha_fin)
            VALUES (?, ?, ?, 'ACTIVA', ?, ?)
            """;

    private final GymContext gymContext;

    public MembershipRepository() {
        this(new GymContext());
    }

    public MembershipRepository(GymContext gymContext) {
        this.gymContext = Objects.requireNonNull(gymContext);
    }

    /** Obtiene sólo el historial del gimnasio actual con su miembro y plan asociados. */
    public List<Membership> findAll() throws SQLException {
        int gymId = gymContext.getCurrentGymId();
        List<Membership> memberships = new ArrayList<>();

        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL_SQL)) {

            statement.setInt(1, gymId);

            try (ResultSet resultSet = statement.executeQuery()) {
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
        }

        return memberships;
    }

    /** Resuelve el gimnasio actual antes de iniciar la asignación. */
    public int currentGymId() throws SQLException {
        return gymContext.getCurrentGymId();
    }

    /** Revalida el gimnasio en la misma transacción, incluso si el contexto estaba en caché. */
    public boolean isGymActive(Connection connection, int gymId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_ACTIVE_GYM_SQL)) {
            statement.setInt(1, gymId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /** Bloquea el miembro propio para serializar sus asignaciones concurrentes. */
    public boolean memberExists(Connection connection, int gymId, int memberId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_MEMBER_SQL)) {
            statement.setInt(1, gymId);
            statement.setInt(2, memberId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /** Devuelve la duración vigente del plan propio o -1 si ya no está disponible. */
    public int activePlanDurationDays(Connection connection, int gymId, int planId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_ACTIVE_PLAN_SQL)) {
            statement.setInt(1, gymId);
            statement.setInt(2, planId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("duracion_dias") : -1;
            }
        }
    }

    /** Indica si el miembro ya tiene una membresía activa dentro del gimnasio actual. */
    public boolean hasActiveMembership(Connection connection, int gymId, int memberId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(EXISTS_ACTIVE_FOR_MEMBER_SQL)) {

            statement.setInt(1, gymId);
            statement.setInt(2, memberId);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    /** Registra una membresía activa y devuelve su identificador generado. */
    public int save(Connection connection, int gymId, int memberId, int planId, java.time.LocalDate startDate,
                    java.time.LocalDate endDate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SAVE_SQL, Statement.RETURN_GENERATED_KEYS)) {

            statement.setInt(1, gymId);
            statement.setInt(2, planId);
            statement.setInt(3, memberId);
            statement.setObject(4, startDate);
            statement.setObject(5, endDate);
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
