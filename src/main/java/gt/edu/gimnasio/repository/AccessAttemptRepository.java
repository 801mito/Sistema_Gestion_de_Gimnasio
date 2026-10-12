package gt.edu.gimnasio.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import gt.edu.gimnasio.config.DatabaseConnection;
import gt.edu.gimnasio.model.AccessAttempt;
import gt.edu.gimnasio.service.GymContext;

/** Persiste y consulta intentos dentro del gimnasio autenticado. */
public class AccessAttemptRepository {

    private static final String SAVE_SQL = """
            INSERT INTO intento_acceso (gimnasio_id, codigo_acceso_id, resultado, motivo)
            SELECT gimnasio.gimnasio_id, ?, ?, ?
            FROM gimnasio
            WHERE gimnasio.gimnasio_id = ? AND gimnasio.gimnasio_activo = TRUE
              AND (?::integer IS NULL OR EXISTS (
                  SELECT 1 FROM codigo_acceso codigo
                  JOIN membresia ON membresia.membresia_id = codigo.membresia_id
                  WHERE codigo.codigo_acceso_id = ? AND membresia.gimnasio_id = gimnasio.gimnasio_id
              ))
            RETURNING intento_acceso_id
            """;

    private static final String FIND_ALL_SQL = """
            SELECT intento.intento_acceso_id, intento.gimnasio_id, intento.codigo_acceso_id,
                   codigo.codigo, intento.resultado, intento.motivo, intento.fecha_hora
            FROM intento_acceso intento
            LEFT JOIN codigo_acceso codigo ON codigo.codigo_acceso_id = intento.codigo_acceso_id
            WHERE intento.gimnasio_id = ?
            ORDER BY intento.fecha_hora DESC, intento.intento_acceso_id DESC
            """;

    private final GymContext gymContext;

    public AccessAttemptRepository(GymContext gymContext) {
        this.gymContext = Objects.requireNonNull(gymContext);
    }

    /** Código nulo representa un intento sin código reconocido; nunca guarda el texto ingresado. */
    public int save(Integer accessCodeId, boolean authorized, String reason) throws SQLException {
        Objects.requireNonNull(reason, "El motivo es obligatorio.");
        if (reason.isBlank() || reason.length() > 100 || (authorized && accessCodeId == null)) {
            throw new IllegalArgumentException("Datos del intento inválidos.");
        }
        int gymId = gymContext.getCurrentGymId();
        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(SAVE_SQL)) {
            if (accessCodeId == null) statement.setNull(1, java.sql.Types.INTEGER);
            else statement.setInt(1, accessCodeId);
            statement.setString(2, authorized ? "AUTORIZADO" : "RECHAZADO");
            statement.setString(3, reason);
            statement.setInt(4, gymId);
            if (accessCodeId == null) statement.setNull(5, java.sql.Types.INTEGER);
            else statement.setInt(5, accessCodeId);
            if (accessCodeId == null) statement.setNull(6, java.sql.Types.INTEGER);
            else statement.setInt(6, accessCodeId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("El gimnasio o código de acceso no está disponible para registrar el intento.");
                }
                return result.getInt(1);
            }
        }
    }

    public List<AccessAttempt> findAll() throws SQLException {
        int gymId = gymContext.getCurrentGymId();
        List<AccessAttempt> attempts = new ArrayList<>();
        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL_SQL)) {
            statement.setInt(1, gymId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int codeId = result.getInt("codigo_acceso_id");
                    Integer recognizedCodeId = result.wasNull() ? null : codeId;
                    attempts.add(new AccessAttempt(result.getInt("intento_acceso_id"),
                            result.getInt("gimnasio_id"), recognizedCodeId,
                            result.getString("codigo"), result.getString("resultado"),
                            result.getString("motivo"), result.getObject("fecha_hora", LocalDateTime.class)));
                }
            }
        }
        return attempts;
    }
}
