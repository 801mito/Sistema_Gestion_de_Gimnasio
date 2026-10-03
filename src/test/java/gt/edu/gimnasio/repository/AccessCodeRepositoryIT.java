package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gt.edu.gimnasio.model.AccessCode;
import gt.edu.gimnasio.model.AccessValidationResult;
import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.service.AccessValidationService;
import gt.edu.gimnasio.service.GymContext;

/** Verifica los códigos y accesos de dos gimnasios en esquemas temporales. */
class AccessCodeRepositoryIT {

    private PostgresPlanFixture fixture;
    private GymContext context;
    private AccessCodeRepository repository;

    @BeforeEach
    void prepareSchema() throws Exception {
        fixture = new PostgresPlanFixture(false);
        context = new GymContext();
        repository = new AccessCodeRepository(context);
        seedCodesInTwoGyms();
    }

    @AfterEach
    void removeTestSchema() throws Exception {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void listsOnlyOwnCodesInOriginalOrderAndKeepsRelatedNames() throws Exception {
        List<AccessCode> own = repository.findAll();
        assertEquals(List.of(702, 701), own.stream().map(AccessCode::getId).toList());
        assertEquals("Ana Principal", own.getFirst().getMemberName());
        assertEquals("Plan principal", own.getFirst().getPlanName());
        assertFalse(own.getFirst().isActive());
        assertEquals(List.of(801), new AccessCodeRepository(secondaryGymContext()).findAll()
                .stream().map(AccessCode::getId).toList());
        fixture.assertResourcesClosed();
    }

    @Test
    void returnsEmptyListWhenOnlyAnotherGymHasCodes() throws Exception {
        fixture.execute("DELETE FROM codigo_acceso WHERE codigo_acceso_id IN (701, 702)");
        assertTrue(repository.findAll().isEmpty());
        assertEquals(1, new AccessCodeRepository(secondaryGymContext()).findAll().size());
        fixture.assertResourcesClosed();
    }

    @Test
    void changesOnlyOwnCodeAndRejectsForeignOrMissingIds() throws Exception {
        repository.updateActiveStatus(701, false);
        assertFalse(repository.findAll().stream().filter(code -> code.getId() == 701)
                .findFirst().orElseThrow().isActive());
        AccessCodeNotFoundException foreign = assertThrows(AccessCodeNotFoundException.class,
                () -> repository.updateActiveStatus(801, false));
        assertEquals("No se encontró el código de acceso en el gimnasio actual.", foreign.getMessage());
        assertThrows(AccessCodeNotFoundException.class, () -> repository.updateActiveStatus(9999, false));
        assertTrue(new AccessCodeRepository(secondaryGymContext()).findAll().getFirst().isActive());

        new AccessCodeRepository(secondaryGymContext()).updateActiveStatus(801, false);
        assertFalse(new AccessCodeRepository(secondaryGymContext()).findAll().getFirst().isActive());
        fixture.assertResourcesClosed();
    }

    @Test
    void inactiveGymCannotChangeCodesEvenWithCachedContext() throws Exception {
        context.getCurrentGymId();
        fixture.execute("UPDATE gimnasio SET gimnasio_activo = FALSE WHERE gimnasio_id = 41");
        assertThrows(AccessCodeNotFoundException.class, () -> repository.updateActiveStatus(701, false));
        assertTrue(repository.findAll().isEmpty());
        assertNull(repository.findValidationDataByCode("GYM-OWN701"));
        assertEquals(List.of(801), new AccessCodeRepository(secondaryGymContext()).findAll()
                .stream().map(AccessCode::getId).toList());
        fixture.assertResourcesClosed();
    }

    @Test
    void missingOrInitiallyInactiveGymStopsQueriesBeforeTheyReachCodes() throws Exception {
        fixture.execute("UPDATE gimnasio SET gimnasio_activo = FALSE WHERE gimnasio_id = 41");
        assertThrows(IllegalStateException.class, repository::findAll);
        assertThrows(IllegalStateException.class, () -> repository.updateActiveStatus(701, false));
        assertThrows(IllegalStateException.class, () -> repository.findValidationDataByCode("GYM-OWN701"));
        fixture.assertResourcesClosed();
    }

    @Test
    void validationRejectsForeignCodeExactlyLikeMissingCodeWithoutLeakingData() throws Exception {
        AccessValidationService service = new AccessValidationService(repository);
        AccessValidationResult foreign = service.validate(" GYM-FOREIGN801 ");
        AccessValidationResult missing = service.validate("GYM-MISSING");
        assertFalse(foreign.isAuthorized());
        assertEquals(missing.getReason(), foreign.getReason());
        assertNull(foreign.getValidationData());
        assertNull(missing.getValidationData());
        assertTrue(service.validate(" GYM-OWN701 ").isAuthorized());
        assertFalse(service.validate("GYM-OWN702").isAuthorized());
        assertTrue(service.validate("GYM-OWN702").getReason().contains("inactivo"));
        assertTrue(service.validate(" ").getReason().contains("Ingresa"));
        assertTrue(new AccessValidationService(new AccessCodeRepository(secondaryGymContext()))
                .validate("GYM-FOREIGN801").isAuthorized());
        fixture.assertResourcesClosed();
    }

    @Test
    void existingMembershipStatusAndDateRulesRemainInForce() throws Exception {
        AccessValidationService service = new AccessValidationService(repository);
        fixture.execute("UPDATE membresia SET estado = 'CONGELADA' WHERE membresia_id = 501");
        assertTrue(service.validate("GYM-OWN701").getReason().contains("congelada"));
        fixture.execute("UPDATE membresia SET estado = 'ACTIVA', fecha_inicio = CURRENT_DATE + 1 "
                + "WHERE membresia_id = 501");
        assertTrue(service.validate("GYM-OWN701").getReason().contains("aún no inicia"));
        fixture.execute("UPDATE membresia SET fecha_inicio = CURRENT_DATE - 10, "
                + "fecha_fin = CURRENT_DATE - 1 WHERE membresia_id = 501");
        assertTrue(service.validate("GYM-OWN701").getReason().contains("vencida"));
        fixture.assertResourcesClosed();
    }

    @Test
    void globalUniquenessRemainsIndependentOfGymFilter() throws Exception {
        try (Connection connection = fixture.openFixtureConnection()) {
            assertTrue(repository.existsByCode(connection, "GYM-FOREIGN801"));
            assertFalse(repository.existsByCode(connection, "GYM-NEW"));
            assertThrows(SQLException.class, () -> repository.save(connection, 501, "GYM-FOREIGN801"));
        }
        fixture.assertResourcesClosed();
    }

    @Test
    void migratedCodesRemainVisibleAndCanStillBeChanged() throws Exception {
        fixture.close();
        fixture = null;
        fixture = new PostgresPlanFixture(true, PostgresPlanFixture.LEGACY_MEMBER_SETUP_SQL);
        repository = new AccessCodeRepository();

        AccessCode previous = repository.findAll().getFirst();
        assertEquals(401, previous.getId());
        assertEquals("GYM-LEGACY101", previous.getCode());
        assertNotNull(repository.findValidationDataByCode(previous.getCode()));
        repository.updateActiveStatus(previous.getId(), false);
        assertFalse(repository.findAll().getFirst().isActive());
        fixture.assertResourcesClosed();
    }

    @Test
    void closesJdbcResourcesWhenAReadOrUpdateFails() throws Exception {
        context.getCurrentGymId();
        fixture.execute("ALTER TABLE codigo_acceso RENAME COLUMN codigo_activo TO codigo_activo_temporal");
        assertThrows(SQLException.class, repository::findAll);
        assertThrows(SQLException.class, () -> repository.updateActiveStatus(701, false));
        assertThrows(SQLException.class, () -> repository.findValidationDataByCode("GYM-OWN701"));
        fixture.assertResourcesClosed();
    }

    private void seedCodesInTwoGyms() throws SQLException {
        fixture.execute("""
                INSERT INTO plan (plan_id, gimnasio_id, nombre, duracion_dias)
                VALUES (101, 41, 'Plan principal', 30), (201, 73, 'Plan secundario', 30);
                INSERT INTO miembro (miembro_id, gimnasio_id, nombres, apellidos)
                VALUES (301, 41, 'Ana', 'Principal'), (401, 73, 'Juan', 'Secundario');
                INSERT INTO membresia (membresia_id, gimnasio_id, plan_id, miembro_id,
                                       estado, fecha_inicio, fecha_fin)
                VALUES (501, 41, 101, 301, 'ACTIVA', CURRENT_DATE - 7, CURRENT_DATE + 7),
                       (601, 73, 201, 401, 'ACTIVA', CURRENT_DATE - 7, CURRENT_DATE + 7);
                INSERT INTO codigo_acceso (codigo_acceso_id, membresia_id, codigo,
                                           codigo_activo, codigo_creado_en)
                VALUES (701, 501, 'GYM-OWN701', TRUE, TIMESTAMP '2026-10-01 09:00:00'),
                       (702, 501, 'GYM-OWN702', FALSE, TIMESTAMP '2026-10-02 09:00:00'),
                       (801, 601, 'GYM-FOREIGN801', TRUE, TIMESTAMP '2026-10-03 09:00:00');
                """);
    }

    private GymContext secondaryGymContext() {
        return new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String ignored) throws SQLException {
                return super.findByName("Gimnasio Secundario");
            }
        });
    }
}
