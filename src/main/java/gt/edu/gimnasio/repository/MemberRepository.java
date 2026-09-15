package gt.edu.gimnasio.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import gt.edu.gimnasio.config.DatabaseConnection;
import gt.edu.gimnasio.model.Member;

/** Realiza operaciones JDBC sobre los miembros registrados. */
public class MemberRepository {

    private static final String FIND_ALL_SQL = """
            SELECT miembro_id, nombres, apellidos, numero_documento,
                   telefono, correo, miembro_creado_en
            FROM miembro
            ORDER BY apellidos, nombres
            """;

    private static final String EXISTS_BY_DOCUMENT_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM miembro
                WHERE numero_documento = ?
            )
            """;

    private static final String SAVE_SQL = """
            INSERT INTO miembro (nombres, apellidos, numero_documento, telefono, correo)
            VALUES (?, ?, ?, ?, ?)
            RETURNING miembro_id, nombres, apellidos, numero_documento,
                      telefono, correo, miembro_creado_en
            """;

    private static final String EXISTS_BY_DOCUMENT_EXCLUDING_ID_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM miembro
                WHERE numero_documento = ?
                  AND miembro_id <> ?
            )
            """;

    private static final String UPDATE_SQL = """
            UPDATE miembro
            SET nombres = ?, apellidos = ?, numero_documento = ?, telefono = ?, correo = ?
            WHERE miembro_id = ?
            RETURNING miembro_id, nombres, apellidos, numero_documento,
                      telefono, correo, miembro_creado_en
            """;

    /** Obtiene los miembros registrados, ordenados por apellidos y nombres. */
    public List<Member> findAll() throws SQLException {
        List<Member> members = new ArrayList<>();

        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                members.add(mapMember(resultSet));
            }
        }

        return members;
    }

    /** Comprueba si ya existe un miembro con el número de documento indicado. */
    public boolean existsByDocument(String documentNumber) throws SQLException {
        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_BY_DOCUMENT_SQL)) {

            statement.setString(1, documentNumber);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    /** Inserta un miembro y devuelve los datos generados por PostgreSQL. */
    public Member save(String firstNames, String lastNames, String documentNumber,
                       String phone, String email) throws SQLException {
        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(SAVE_SQL)) {

            statement.setString(1, firstNames);
            statement.setString(2, lastNames);
            statement.setString(3, documentNumber);
            statement.setString(4, phone);
            statement.setString(5, email);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapMember(resultSet);
                }
            }
        }

        throw new SQLException("PostgreSQL no devolvió el miembro registrado.");
    }

    /** Comprueba documentos duplicados al editar, excluyendo al miembro actual. */
    public boolean existsByDocumentExcludingId(String documentNumber, int id) throws SQLException {
        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_BY_DOCUMENT_EXCLUDING_ID_SQL)) {

            statement.setString(1, documentNumber);
            statement.setInt(2, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    /** Actualiza los datos básicos de un miembro existente. */
    public Member update(int id, String firstNames, String lastNames, String documentNumber,
                         String phone, String email) throws SQLException {
        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {

            statement.setString(1, firstNames);
            statement.setString(2, lastNames);
            statement.setString(3, documentNumber);
            statement.setString(4, phone);
            statement.setString(5, email);
            statement.setInt(6, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapMember(resultSet);
                }
            }
        }

        throw new SQLException("No se encontró el miembro que se desea actualizar.");
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
