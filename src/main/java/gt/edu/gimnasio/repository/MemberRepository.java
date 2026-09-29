package gt.edu.gimnasio.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import gt.edu.gimnasio.config.DatabaseConnection;
import gt.edu.gimnasio.model.Member;
import gt.edu.gimnasio.service.GymContext;

/** Realiza operaciones JDBC sobre los miembros registrados. */
public class MemberRepository {

    private static final String FIND_ALL_SQL = """
            SELECT miembro_id, nombres, apellidos, numero_documento,
                   telefono, correo, miembro_creado_en
            FROM miembro
            WHERE gimnasio_id = ?
            ORDER BY apellidos, nombres
            """;

    private static final String EXISTS_BY_DOCUMENT_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM miembro
                WHERE gimnasio_id = ?
                  AND numero_documento = ?
            )
            """;

    private static final String SAVE_SQL = """
            INSERT INTO miembro (gimnasio_id, nombres, apellidos, numero_documento, telefono, correo)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING miembro_id, nombres, apellidos, numero_documento,
                      telefono, correo, miembro_creado_en
            """;

    private static final String EXISTS_BY_DOCUMENT_EXCLUDING_ID_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM miembro
                WHERE gimnasio_id = ?
                  AND numero_documento = ?
                  AND miembro_id <> ?
            )
            """;

    private static final String UPDATE_SQL = """
            UPDATE miembro
            SET nombres = ?, apellidos = ?, numero_documento = ?, telefono = ?, correo = ?
            WHERE gimnasio_id = ?
              AND miembro_id = ?
            RETURNING miembro_id, nombres, apellidos, numero_documento,
                      telefono, correo, miembro_creado_en
            """;

    private final GymContext gymContext;

    public MemberRepository() {
        this(new GymContext());
    }

    public MemberRepository(GymContext gymContext) {
        this.gymContext = Objects.requireNonNull(gymContext);
    }

    /** Obtiene sólo los miembros del gimnasio actual, ordenados por apellidos y nombres. */
    public List<Member> findAll() throws SQLException {
        int gymId = gymContext.getCurrentGymId();
        List<Member> members = new ArrayList<>();

        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL_SQL)) {

            statement.setInt(1, gymId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(mapMember(resultSet));
                }
            }
        }

        return members;
    }

    /** Comprueba documentos duplicados dentro del gimnasio actual al registrar. */
    public boolean existsByDocument(String documentNumber) throws SQLException {
        int gymId = gymContext.getCurrentGymId();

        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_BY_DOCUMENT_SQL)) {

            statement.setInt(1, gymId);
            statement.setString(2, documentNumber);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    /** Inserta un miembro asociado explícitamente al gimnasio actual. */
    public Member save(String firstNames, String lastNames, String documentNumber,
                       String phone, String email) throws SQLException {
        int gymId = gymContext.getCurrentGymId();

        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(SAVE_SQL)) {

            statement.setInt(1, gymId);
            statement.setString(2, firstNames);
            statement.setString(3, lastNames);
            statement.setString(4, documentNumber);
            statement.setString(5, phone);
            statement.setString(6, email);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapMember(resultSet);
                }
            }
        }

        throw new SQLException("PostgreSQL no devolvió el miembro registrado.");
    }

    /** Comprueba duplicados al editar dentro del gimnasio actual, excluyendo el propio ID. */
    public boolean existsByDocumentExcludingId(String documentNumber, int id) throws SQLException {
        int gymId = gymContext.getCurrentGymId();

        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_BY_DOCUMENT_EXCLUDING_ID_SQL)) {

            statement.setInt(1, gymId);
            statement.setString(2, documentNumber);
            statement.setInt(3, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    /** Actualiza datos básicos sólo si el miembro pertenece al gimnasio actual. */
    public Member update(int id, String firstNames, String lastNames, String documentNumber,
                         String phone, String email) throws SQLException {
        int gymId = gymContext.getCurrentGymId();

        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {

            statement.setString(1, firstNames);
            statement.setString(2, lastNames);
            statement.setString(3, documentNumber);
            statement.setString(4, phone);
            statement.setString(5, email);
            statement.setInt(6, gymId);
            statement.setInt(7, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapMember(resultSet);
                }
            }
        }

        throw new MemberNotFoundException();
    }

    private Member mapMember(ResultSet resultSet) throws SQLException {
        return new Member(
                resultSet.getInt("miembro_id"),
                resultSet.getString("nombres"),
                resultSet.getString("apellidos"),
                resultSet.getString("numero_documento"),
                resultSet.getString("telefono"),
                resultSet.getString("correo"),
                resultSet.getTimestamp("miembro_creado_en").toLocalDateTime());
    }
}
