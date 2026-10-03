package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gt.edu.gimnasio.model.Employee;
import gt.edu.gimnasio.service.PasswordHasher;

/** Primera tanda de #61: esquema, hash y consultas, sin pantalla de login. */
class EmployeeRepositoryIT {

    private PostgresPlanFixture fixture;
    private EmployeeRepository repository;

    @BeforeEach
    void prepareSchema() throws Exception {
        fixture = new PostgresPlanFixture(false);
        repository = new EmployeeRepository();
    }

    @AfterEach
    void removeOnlyTestSchema() throws Exception {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void savesHashedPasswordAndFindsEmployeeWithOnlyAssignedGym() throws Exception {
        int id = repository.save(41, "  JAIME.CARDONA  ", "clave-de-prueba-larga".toCharArray());
        EmployeeRepository.Credentials credentials = repository.findByUsername("Jaime.Cardona")
                .orElseThrow();
        Employee employee = credentials.getEmployee();

        assertEquals(id, employee.getId());
        assertEquals(41, employee.getGymId());
        assertEquals("Gimnasio Principal", employee.getGymName());
        assertEquals("jaime.cardona", employee.getUsername());
        assertTrue(employee.isActive());
        assertTrue(employee.isGymActive());
        assertNotNull(employee.getCreatedAt());
        assertTrue(credentials.getPasswordHash().startsWith("pbkdf2-sha256$600000$"));
        assertTrue(new PasswordHasher().matches("clave-de-prueba-larga".toCharArray(),
                credentials.getPasswordHash()));
        assertFalse(credentials.getPasswordHash().contains("clave-de-prueba-larga"));
        assertEquals(credentials.getPasswordHash(), storedHash(id));
        fixture.assertResourcesClosed();
    }

    @Test
    void usernamesAreGloballyUniqueButEmployeesCanBelongToDifferentGyms() throws Exception {
        int first = repository.save(41, "ana", "primera-clave-larga".toCharArray());
        int second = repository.save(73, "luis", "segunda-clave-larga".toCharArray());

        assertNotEquals(first, second);
        assertEquals(41, repository.findByUsername("ana").orElseThrow().getEmployee().getGymId());
        assertEquals(73, repository.findByUsername("luis").orElseThrow().getEmployee().getGymId());
        SQLException duplicate = assertThrows(SQLException.class,
                () -> repository.save(73, " ANA ", "otra-clave-larga".toCharArray()));
        assertEquals("23505", duplicate.getSQLState());
        assertEquals(2, employeeCount());
        fixture.assertResourcesClosed();
    }

    @Test
    void missingGymAndInvalidUsernamesAreRejectedWithoutCreatingAccounts() throws Exception {
        SQLException missingGym = assertThrows(SQLException.class,
                () -> repository.save(99999, "empleado", "clave-de-prueba-larga".toCharArray()));
        assertEquals("23503", missingGym.getSQLState());
        assertThrows(IllegalArgumentException.class,
                () -> repository.save(41, "mal usuario", "clave-de-prueba-larga".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> repository.save(41, "empleado", "  ".toCharArray()));
        assertEquals(0, employeeCount());
        fixture.assertResourcesClosed();
    }

    @Test
    void lookupIncludesAccountAndGymStatesButDoesNotAuthenticate() throws Exception {
        repository.save(73, "empleado", "clave-de-prueba-larga".toCharArray());
        fixture.execute("UPDATE empleado SET empleado_activo = FALSE WHERE usuario = 'empleado'");
        fixture.execute("UPDATE gimnasio SET gimnasio_activo = FALSE WHERE gimnasio_id = 73");

        Employee employee = repository.findByUsername("empleado").orElseThrow().getEmployee();
        assertFalse(employee.isActive());
        assertFalse(employee.isGymActive());
        assertTrue(repository.findByUsername("desconocido").isEmpty());
        fixture.assertResourcesClosed();
    }

    @Test
    void sqlFailureClosesConnectionAndStatement() throws Exception {
        fixture.execute("ALTER TABLE empleado RENAME COLUMN usuario TO usuario_temporal");
        assertThrows(SQLException.class, () -> repository.findByUsername("empleado"));
        fixture.assertResourcesClosed();
    }

    private String storedHash(int id) throws SQLException {
        try (Connection connection = fixture.openFixtureConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT contrasena_hash FROM empleado WHERE empleado_id = ?")) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getString(1);
            }
        }
    }

    private int employeeCount() throws SQLException {
        try (Connection connection = fixture.openFixtureConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM empleado");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
