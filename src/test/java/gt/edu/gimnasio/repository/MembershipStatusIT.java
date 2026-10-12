package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.model.Membership;
import gt.edu.gimnasio.service.AccessValidationService;
import gt.edu.gimnasio.service.GymContext;
import gt.edu.gimnasio.service.MembershipService;
import gt.edu.gimnasio.service.MembershipValidationException;

class MembershipStatusIT {

    private PostgresPlanFixture fixture;
    private MembershipRepository repository;
    private MembershipService service;
    private AccessValidationService access;

    @BeforeEach
    void prepare() throws Exception {
        fixture = new PostgresPlanFixture(false);
        repository = new MembershipRepository();
        service = new MembershipService(repository);
        access = new AccessValidationService(new AccessCodeRepository());
        fixture.execute("""
                INSERT INTO plan (plan_id, gimnasio_id, nombre, duracion_dias)
                VALUES (101, 41, 'Mensual', 30), (201, 73, 'Semanal', 7);
                INSERT INTO miembro (miembro_id, gimnasio_id, nombres, apellidos)
                VALUES (301, 41, 'Jaime', 'Principal'), (401, 73, 'Ana', 'Secundaria');
                INSERT INTO membresia (membresia_id, gimnasio_id, plan_id, miembro_id, estado, fecha_inicio, fecha_fin)
                VALUES (501, 41, 101, 301, 'ACTIVA', CURRENT_DATE - 7, CURRENT_DATE + 7),
                       (601, 73, 201, 401, 'ACTIVA', CURRENT_DATE - 7, CURRENT_DATE + 7);
                INSERT INTO codigo_acceso (codigo_acceso_id, membresia_id, codigo)
                VALUES (701, 501, 'GYM-PRIMARY701'), (801, 601, 'GYM-SECONDARY801');
                """);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (fixture != null) fixture.close();
    }

    @Test
    void freezingBlocksAccessAndReactivationPreservesDatesAndCode() throws Exception {
        Membership original = repository.findAll().getFirst();
        assertTrue(access.validate("GYM-PRIMARY701").isAuthorized());
        service.freeze(501);
        Membership frozen = repository.findAll().getFirst();
        assertEquals("CONGELADA", frozen.getStatus());
        assertEquals(original.getStartDate(), frozen.getStartDate());
        assertEquals(original.getEndDate(), frozen.getEndDate());
        assertFalse(access.validate("GYM-PRIMARY701").isAuthorized());
        assertEquals("La membresía está congelada.", access.validate("GYM-PRIMARY701").getReason());

        service.reactivate(501);
        assertEquals("ACTIVA", repository.findAll().getFirst().getStatus());
        assertTrue(access.validate("GYM-PRIMARY701").isAuthorized());
        fixture.execute("UPDATE codigo_acceso SET codigo_activo = FALSE WHERE codigo_acceso_id = 701");
        service.freeze(501);
        service.reactivate(501);
        assertFalse(access.validate("GYM-PRIMARY701").isAuthorized());
        assertEquals("El código de acceso está inactivo.", access.validate("GYM-PRIMARY701").getReason());
        fixture.assertResourcesClosed();
    }

    @Test
    void rejectsForeignExpiredAndConflictingTransitionsWithoutChangingHistory() throws Exception {
        GymContext other = new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String ignored) throws SQLException {
                return super.findByName("Gimnasio Secundario");
            }
        });
        MembershipService secondary = new MembershipService(new MembershipRepository(other));
        assertThrows(MembershipValidationException.class, () -> secondary.freeze(501));
        assertEquals("ACTIVA", repository.findAll().getFirst().getStatus());
        assertThrows(MembershipValidationException.class, () -> service.reactivate(501));

        service.freeze(501);
        fixture.execute("UPDATE membresia SET fecha_fin = CURRENT_DATE - 1 WHERE membresia_id = 501");
        assertThrows(MembershipValidationException.class, () -> service.reactivate(501));
        fixture.execute("UPDATE membresia SET fecha_fin = CURRENT_DATE + 7 WHERE membresia_id = 501");
        fixture.execute("""
                INSERT INTO membresia (membresia_id, gimnasio_id, plan_id, miembro_id, estado, fecha_inicio, fecha_fin)
                VALUES (502, 41, 101, 301, 'ACTIVA', CURRENT_DATE, CURRENT_DATE + 7)
                """);
        assertThrows(MembershipValidationException.class, () -> service.reactivate(501));
        assertEquals("CONGELADA", repository.findAll().stream()
                .filter(membership -> membership.getId() == 501).findFirst().orElseThrow().getStatus());
        assertEquals(2, repository.findAll().size());
        assertEquals("ACTIVA", new MembershipRepository(other).findAll().getFirst().getStatus());
        fixture.assertResourcesClosed();
    }
}
