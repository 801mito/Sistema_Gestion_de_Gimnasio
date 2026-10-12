package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import gt.edu.gimnasio.model.AccessAttempt;
import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.service.GymContext;

class AccessAttemptRepositoryIT {

    @Test
    void savesKnownAndUnknownAttemptsOnlyInTheirGym() throws Exception {
        try (PostgresPlanFixture fixture = new PostgresPlanFixture(false)) {
            fixture.execute("""
                    INSERT INTO plan (plan_id, gimnasio_id, nombre, duracion_dias)
                    VALUES (101, 41, 'Mensual', 30), (201, 73, 'Semanal', 7);
                    INSERT INTO miembro (miembro_id, gimnasio_id, nombres, apellidos)
                    VALUES (301, 41, 'Jaime', 'Principal'), (401, 73, 'Ana', 'Secundaria');
                    INSERT INTO membresia (membresia_id, gimnasio_id, plan_id, miembro_id, fecha_inicio, fecha_fin)
                    VALUES (501, 41, 101, 301, CURRENT_DATE, CURRENT_DATE + 30),
                           (601, 73, 201, 401, CURRENT_DATE, CURRENT_DATE + 7);
                    INSERT INTO codigo_acceso (codigo_acceso_id, membresia_id, codigo)
                    VALUES (701, 501, 'GYM-PRINCIPAL'), (801, 601, 'GYM-SECUNDARIO');
                    """);
            AccessAttemptRepository principal = new AccessAttemptRepository(new GymContext());
            AccessAttemptRepository secondary = new AccessAttemptRepository(secondaryGym());

            principal.save(701, true, "Acceso autorizado.");
            principal.save(null, false, "Código no reconocido.");
            secondary.save(801, false, "Código inactivo.");
            assertThrows(SQLException.class, () -> principal.save(801, false, "Código ajeno."));
            assertThrows(IllegalArgumentException.class, () -> principal.save(null, true, "Acceso autorizado."));

            List<AccessAttempt> attempts = principal.findAll();
            assertEquals(2, attempts.size());
            assertTrue(attempts.stream().allMatch(attempt -> attempt.gymId() == 41));
            AccessAttempt unknown = attempts.stream().filter(attempt -> attempt.accessCodeId() == null)
                    .findFirst().orElseThrow();
            assertNull(unknown.code());
            assertEquals("RECHAZADO", unknown.result());
            assertNotNull(unknown.occurredAt());
            assertEquals(1, secondary.findAll().size());
            fixture.assertResourcesClosed();
        }
    }

    @Test
    void legacyAttemptsReceiveOriginalGymAfterMigration() throws Exception {
        try (PostgresPlanFixture fixture = new PostgresPlanFixture(true,
                PostgresPlanFixture.LEGACY_MEMBER_SETUP_SQL + """
                INSERT INTO intento_acceso (codigo_acceso_id, resultado, motivo)
                VALUES (401, 'AUTORIZADO', 'Registro anterior'),
                       (NULL, 'RECHAZADO', 'Código no reconocido');
                """)) {
            List<AccessAttempt> attempts = new AccessAttemptRepository(new GymContext()).findAll();
            assertEquals(2, attempts.size());
            assertTrue(attempts.stream().allMatch(attempt -> attempt.gymId() > 0));
            fixture.assertResourcesClosed();
        }
    }

    private GymContext secondaryGym() {
        return new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String ignored) throws SQLException {
                return super.findByName("Gimnasio Secundario");
            }
        });
    }
}
