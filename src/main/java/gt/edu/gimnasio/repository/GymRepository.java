package gt.edu.gimnasio.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import gt.edu.gimnasio.config.DatabaseConnection;
import gt.edu.gimnasio.model.Gym;

/** Consulta los gimnasios registrados en PostgreSQL. */
public class GymRepository {

    private static final String FIND_BY_NAME_SQL = """
            SELECT gimnasio_id, nombre, gimnasio_activo, gimnasio_creado_en
            FROM gimnasio
            WHERE nombre = ?
            """;

    /** Busca un gimnasio por su nombre, sin asumir el valor de su ID. */
    public Optional<Gym> findByName(String name) throws SQLException {
        try (Connection connection = DatabaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_NAME_SQL)) {

            statement.setString(1, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new Gym(
                            resultSet.getInt("gimnasio_id"),
                            resultSet.getString("nombre"),
                            resultSet.getBoolean("gimnasio_activo"),
                            resultSet.getTimestamp("gimnasio_creado_en").toLocalDateTime()));
                }
            }
        }

        return Optional.empty();
    }
}
