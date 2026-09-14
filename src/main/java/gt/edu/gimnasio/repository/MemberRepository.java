package gt.edu.gimnasio.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import gt.edu.gimnasio.config.DatabaseConnection;
import gt.edu.gimnasio.model.Member;

/** Realiza consultas de lectura sobre los miembros registrados. */
public class MemberRepository {

    private static final String FIND_ALL_SQL = """
            SELECT miembro_id, nombres, apellidos, numero_documento,
                   telefono, correo, miembro_creado_en
            FROM miembro
            ORDER BY apellidos, nombres
            """;

    /** Obtiene los miembros registrados, ordenados por apellidos y nombres. */
    public List<Member> findAll() throws SQLException {
        List<Member> members = new ArrayList<>();

        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                members.add(new Member(
                        resultSet.getInt("miembro_id"),
                        resultSet.getString("nombres"),
                        resultSet.getString("apellidos"),
                        resultSet.getString("numero_documento"),
                        resultSet.getString("telefono"),
                        resultSet.getString("correo"),
                        resultSet.getTimestamp("miembro_creado_en").toLocalDateTime()));
            }
        }

        return members;
    }
}
