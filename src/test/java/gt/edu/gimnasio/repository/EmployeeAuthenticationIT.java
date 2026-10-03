package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gt.edu.gimnasio.model.Employee;
import gt.edu.gimnasio.service.AuthenticationException;
import gt.edu.gimnasio.service.AuthenticationService;
import gt.edu.gimnasio.service.PasswordHasher;

/** Segunda tanda de #61: identidad y rechazo de credenciales en PostgreSQL real. */
class EmployeeAuthenticationIT {

    private PostgresPlanFixture fixture;
    private EmployeeRepository repository;
    private AuthenticationService authentication;

    @BeforeEach
    void prepareSchema() throws Exception {
        fixture = new PostgresPlanFixture(false);
        repository = new EmployeeRepository();
        authentication = new AuthenticationService(repository, new PasswordHasher());
    }

    @AfterEach
    void removeOnlyTestSchema() throws Exception {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void authenticatesEmployeesOfTwoGymsWithoutAcceptingAGymSelector() throws Exception {
        int principalId = repository.save(41, "jaime", "clave-principal-larga".toCharArray());
        int secondaryId = repository.save(73, "ana", "clave-secundaria-larga".toCharArray());

        Employee principal = authentication.authenticate(" JAIME ", "clave-principal-larga".toCharArray());
        Employee secondary = authentication.authenticate("ana", "clave-secundaria-larga".toCharArray());

        assertEquals(principalId, principal.getId());
        assertEquals(41, principal.getGymId());
        assertEquals("Gimnasio Principal", principal.getGymName());
        assertEquals(secondaryId, secondary.getId());
        assertEquals(73, secondary.getGymId());
        assertEquals("Gimnasio Secundario", secondary.getGymName());
        assertNotEquals(principal.getGymId(), secondary.getGymId());
        fixture.assertResourcesClosed();
    }

    @Test
    void wrongPasswordUnknownUserAndInvalidInputHaveIdenticalErrors() throws Exception {
        repository.save(41, "jaime", "clave-principal-larga".toCharArray());

        String wrongPassword = denialMessage("jaime", "otra-clave-larga".toCharArray());
        assertEquals(wrongPassword, denialMessage("desconocido", "otra-clave-larga".toCharArray()));
        assertEquals(wrongPassword, denialMessage("mal usuario", "otra-clave-larga".toCharArray()));
        assertEquals(wrongPassword, denialMessage("' OR 1=1 --", "otra-clave-larga".toCharArray()));
        assertEquals(wrongPassword, denialMessage(null, "otra-clave-larga".toCharArray()));
        assertEquals(wrongPassword, denialMessage("jaime", null));
        assertEquals(wrongPassword, denialMessage("jaime", new char[0]));
        assertFalse(wrongPassword.contains("jaime"));
        fixture.assertResourcesClosed();
    }

    @Test
    void inactiveEmployeeIsRejectedWithSameMessageAndCanLaterBeReactivated() throws Exception {
        repository.save(41, "jaime", "clave-principal-larga".toCharArray());
        String baseline = denialMessage("jaime", "clave-equivocada".toCharArray());
        fixture.execute("UPDATE empleado SET empleado_activo = FALSE WHERE usuario = 'jaime'");

        assertEquals(baseline, denialMessage("jaime", "clave-principal-larga".toCharArray()));
        fixture.execute("UPDATE empleado SET empleado_activo = TRUE WHERE usuario = 'jaime'");
        assertEquals(41, authentication.authenticate("jaime", "clave-principal-larga".toCharArray())
                .getGymId());
        fixture.assertResourcesClosed();
    }

    @Test
    void inactiveGymIsRejectedWithSameMessageAndCanLaterBeReactivated() throws Exception {
        repository.save(73, "ana", "clave-secundaria-larga".toCharArray());
        String baseline = denialMessage("ana", "clave-equivocada".toCharArray());
        fixture.execute("UPDATE gimnasio SET gimnasio_activo = FALSE WHERE gimnasio_id = 73");

        assertEquals(baseline, denialMessage("ana", "clave-secundaria-larga".toCharArray()));
        fixture.execute("UPDATE gimnasio SET gimnasio_activo = TRUE WHERE gimnasio_id = 73");
        assertEquals(73, authentication.authenticate("ana", "clave-secundaria-larga".toCharArray())
                .getGymId());
        fixture.assertResourcesClosed();
    }

    @Test
    void authenticationReadsCurrentGymAssociationInsteadOfCachingOrGuessingIt() throws Exception {
        repository.save(41, "jaime", "clave-principal-larga".toCharArray());
        assertEquals(41, authentication.authenticate("jaime", "clave-principal-larga".toCharArray())
                .getGymId());
        fixture.execute("UPDATE empleado SET gimnasio_id = 73 WHERE usuario = 'jaime'");

        Employee moved = authentication.authenticate("jaime", "clave-principal-larga".toCharArray());
        assertEquals(73, moved.getGymId());
        assertEquals("Gimnasio Secundario", moved.getGymName());
        fixture.assertResourcesClosed();
    }

    @Test
    void corruptedHashAndSqlFailureNeverReturnAnEmployee() throws Exception {
        repository.save(41, "jaime", "clave-principal-larga".toCharArray());
        String baseline = denialMessage("jaime", "clave-equivocada".toCharArray());
        fixture.execute("UPDATE empleado SET contrasena_hash = 'invalid' WHERE usuario = 'jaime'");
        assertEquals(baseline, denialMessage("jaime", "clave-principal-larga".toCharArray()));

        fixture.execute("ALTER TABLE empleado RENAME COLUMN usuario TO usuario_temporal");
        assertThrows(SQLException.class,
                () -> authentication.authenticate("jaime", "clave-principal-larga".toCharArray()));
        fixture.assertResourcesClosed();
    }

    private String denialMessage(String username, char[] password) {
        return assertThrows(AuthenticationException.class,
                () -> authentication.authenticate(username, password)).getMessage();
    }
}
