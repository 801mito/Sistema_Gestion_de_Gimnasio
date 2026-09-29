package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.model.Plan;
import gt.edu.gimnasio.service.GymContext;
import gt.edu.gimnasio.service.PlanService;
import gt.edu.gimnasio.service.PlanValidationException;

/** Ejecuta el repositorio de producción contra PostgreSQL, sin simular sus SQL. */
class PlanRepositoryIT {

    private PostgresPlanFixture fixture;
    private GymContext primaryContext;
    private PlanRepository primary;
    private PlanRepository secondary;
    private PlanService primaryService;
    private PlanService secondaryService;

    @BeforeEach
    void prepareSchema() throws Exception {
        fixture = new PostgresPlanFixture(false);
        primaryContext = new GymContext();
        primary = new PlanRepository(primaryContext);
        // Contexto inyectado sólo en las pruebas: no añade un selector a la aplicación.
        secondary = new PlanRepository(new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String ignored) throws SQLException {
                return super.findByName("Gimnasio Secundario");
            }
        }));
        primaryService = new PlanService(primary);
        secondaryService = new PlanService(secondary);
    }

    @AfterEach
    void removeOnlyTestSchema() throws SQLException {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void resolvesPrimaryGymWithoutAssumingIdOne() throws SQLException {
        assertEquals(41, primaryContext.getCurrentGymId());
        assertEquals("Gimnasio Principal", primaryContext.getCurrentGym().getName());
        fixture.assertResourcesClosed();
    }

    @Test
    void sameNameIsAllowedAcrossGymsButListsDoNotMixTheirPlans() throws Exception {
        Plan first = primaryService.createPlan("Mensual", 30);
        Plan second = secondaryService.createPlan("Mensual", 15);
        assertNotEquals(first.getId(), second.getId());
        assertEquals(List.of(first.getId()), ids(primary.findAll()));
        assertEquals(List.of(second.getId()), ids(secondary.findAll()));
        assertEquals(List.of(first.getId()), ids(primary.findActive()));
        assertEquals(List.of(second.getId()), ids(secondary.findActive()));
        assertEquals(41, storedPlan(first.getId()).gymId());
        assertEquals(73, storedPlan(second.getId()).gymId());
        fixture.assertResourcesClosed();
    }

    @Test
    void registrationSendsExplicitGymAndTrimsTheName() throws Exception {
        // El modelo para bases nuevas no tiene default de gimnasio: omitirlo falla.
        Plan plan = primaryService.createPlan("  Semanal  ", 7);
        assertEquals("Semanal", plan.getName());
        assertEquals(41, storedPlan(plan.getId()).gymId());
        assertTrue(plan.isActive());
        assertNotNull(plan.getCreatedAt());
        assertEquals(List.of(plan.getId()), ids(primary.findAll()));
        fixture.assertResourcesClosed();
    }

    @Test
    void duplicatesAreCaseInsensitiveWithinCurrentGymOnCreateAndEdit() throws Exception {
        Plan monthly = primaryService.createPlan("Mensual", 30);
        Plan weekly = primaryService.createPlan("Semanal", 7);
        secondaryService.createPlan("Mensual", 15);
        int resourcesBefore = fixture.resourceCount();

        assertThrows(PlanValidationException.class, () -> primaryService.createPlan(" MENSUAL ", 90));
        assertThrows(PlanValidationException.class,
                () -> primaryService.updatePlan(weekly.getId(), " mensual ", 90));
        assertFalse(primary.existsByNameExcludingId("MENSUAL", monthly.getId()));
        assertTrue(primary.existsByNameExcludingId("MENSUAL", weekly.getId()));
        assertEquals(2, primary.findAll().size());
        assertEquals("Semanal", storedPlan(weekly.getId()).name());
        assertTrue(fixture.resourceCount() > resourcesBefore);
        fixture.assertResourcesClosed();
    }

    @Test
    void editPreservesOwnershipStatusAndCreationDateAndIgnoresForeignDuplicates() throws Exception {
        Plan own = primaryService.createPlan("Semanal", 7);
        Plan foreign = secondaryService.createPlan("Quincenal", 15);
        primaryService.changeActiveStatus(own.getId(), false);
        StoredPlan before = storedPlan(own.getId());
        StoredPlan foreignBefore = storedPlan(foreign.getId());

        Plan edited = primaryService.updatePlan(own.getId(), " Quincenal ", 14);
        assertEquals("Quincenal", edited.getName());
        assertEquals(14, edited.getDurationDays());
        assertFalse(edited.isActive());
        assertEquals(before.gymId(), storedPlan(own.getId()).gymId());
        assertEquals(before.createdAt(), edited.getCreatedAt());
        assertEquals(foreignBefore, storedPlan(foreign.getId()));
        fixture.assertResourcesClosed();
    }

    @Test
    void foreignAndMissingIdsCannotBeEditedOrHaveTheirStateChanged() throws Exception {
        Plan foreign = secondaryService.createPlan("Mensual", 30);
        StoredPlan before = storedPlan(foreign.getId());
        for (int id : new int[] {foreign.getId(), Integer.MAX_VALUE}) {
            assertThrows(PlanNotFoundException.class, () -> primaryService.updatePlan(id, "Cambio", 7));
            assertThrows(PlanNotFoundException.class, () -> primaryService.changeActiveStatus(id, false));
            assertThrows(PlanNotFoundException.class, () -> primaryService.changeActiveStatus(id, true));
        }
        assertEquals(before, storedPlan(foreign.getId()));
        assertTrue(primary.findAll().isEmpty());
        fixture.assertResourcesClosed();
    }

    @Test
    void activatingAndDeactivatingChangesOnlyOwnPlanAndActiveList() throws Exception {
        Plan own = primaryService.createPlan("Mensual", 30);
        Plan foreign = secondaryService.createPlan("Mensual", 15);
        StoredPlan foreignBefore = storedPlan(foreign.getId());

        primaryService.changeActiveStatus(own.getId(), false);
        assertTrue(primary.findActive().isEmpty());
        assertEquals(List.of(own.getId()), ids(primary.findAll()));
        assertFalse(storedPlan(own.getId()).active());

        primaryService.changeActiveStatus(own.getId(), true);
        assertEquals(List.of(own.getId()), ids(primary.findActive()));
        assertTrue(storedPlan(own.getId()).active());
        assertEquals(foreignBefore, storedPlan(foreign.getId()));
        fixture.assertResourcesClosed();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrInactiveGymStopsEveryRepositoryOperation(boolean inactive) throws Exception {
        if (inactive) {
            fixture.execute("UPDATE gimnasio SET gimnasio_activo = false WHERE nombre = 'Gimnasio Principal'");
        } else {
            fixture.execute("DELETE FROM gimnasio WHERE nombre = 'Gimnasio Principal'");
        }
        assertThrows(IllegalStateException.class, primary::findAll);
        assertThrows(IllegalStateException.class, primary::findActive);
        assertThrows(IllegalStateException.class, () -> primary.existsByName("Mensual"));
        assertThrows(IllegalStateException.class, () -> primary.existsByNameExcludingId("Mensual", 1));
        assertThrows(IllegalStateException.class, () -> primary.save("Mensual", 30));
        assertThrows(IllegalStateException.class, () -> primary.update(1, "Mensual", 30));
        assertThrows(IllegalStateException.class, () -> primary.updateActiveStatus(1, false));
        fixture.assertResourcesClosed();
    }

    @Test
    void databaseErrorsCloseConnectionsStatementsAndResultSets() throws Exception {
        primaryContext.getCurrentGymId();
        Plan existing = primary.save("Mensual", 30);
        assertThrows(SQLException.class, () -> primary.save("Mensual", 30));
        assertThrows(SQLException.class, () -> primary.save("Inválido", 0));
        fixture.assertResourcesClosed();

        fixture.execute("ALTER TABLE plan RENAME COLUMN duracion_dias TO duracion_temporal");
        assertThrows(SQLException.class, primary::findAll);
        assertThrows(SQLException.class, primary::findActive);
        assertThrows(SQLException.class, () -> primary.save("Otro", 7));
        assertThrows(SQLException.class, () -> primary.update(existing.getId(), "Otro", 7));

        fixture.execute("ALTER TABLE plan RENAME COLUMN plan_activo TO activo_temporal");
        assertThrows(SQLException.class, () -> primary.updateActiveStatus(existing.getId(), false));
        fixture.execute("ALTER TABLE plan RENAME COLUMN nombre TO nombre_temporal");
        assertThrows(SQLException.class, () -> primary.existsByName("Mensual"));
        assertThrows(SQLException.class, () -> primary.existsByNameExcludingId("Mensual", existing.getId()));
        fixture.assertResourcesClosed();
    }

    @Test
    void emptyReturningResultIsNotReportedAsASuccessAndResourcesClose() throws Exception {
        fixture.execute("""
                CREATE FUNCTION omitir_plan() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RETURN NULL; END $$;
                CREATE TRIGGER omitir_plan BEFORE INSERT ON plan
                FOR EACH ROW EXECUTE FUNCTION omitir_plan();
                """);
        SQLException failure = assertThrows(SQLException.class, () -> primary.save("Omitido", 7));
        assertEquals("PostgreSQL no devolvió el plan registrado.", failure.getMessage());
        assertTrue(primary.findAll().isEmpty());
        fixture.assertResourcesClosed();
    }

    @Test
    void migratedExistingPlansRemainVisibleAndNewPlansCanBeRegistered() throws Exception {
        fixture.close();
        fixture = null;
        fixture = new PostgresPlanFixture(true);
        PlanRepository migratedRepository = new PlanRepository(new GymContext());
        PlanService migratedService = new PlanService(migratedRepository);

        List<Plan> migratedPlans = migratedRepository.findAll();
        assertEquals(1, migratedPlans.size());
        Plan prior = migratedPlans.getFirst();
        assertEquals("Plan previo", prior.getName());
        assertEquals(30, prior.getDurationDays());
        assertTrue(prior.isActive());
        StoredPlan before = storedPlan(prior.getId());

        Plan added = migratedService.createPlan("Nuevo después de migrar", 7);
        assertEquals(before.gymId(), storedPlan(added.getId()).gymId());
        assertEquals(before, storedPlan(prior.getId()));
        assertEquals(2, migratedRepository.findAll().size());
        fixture.assertResourcesClosed();
    }

    private List<Integer> ids(List<Plan> plans) {
        return plans.stream().map(Plan::getId).toList();
    }

    private StoredPlan storedPlan(int id) throws SQLException {
        try (Connection connection = fixture.openFixtureConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT gimnasio_id, nombre, duracion_dias, plan_activo, plan_creado_en
                     FROM plan WHERE plan_id = ?
                     """)) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "Falta el plan ficticio " + id);
                return new StoredPlan(result.getInt("gimnasio_id"), result.getString("nombre"),
                        result.getInt("duracion_dias"), result.getBoolean("plan_activo"),
                        result.getTimestamp("plan_creado_en").toLocalDateTime());
            }
        }
    }

    private record StoredPlan(int gymId, String name, int days, boolean active, LocalDateTime createdAt) { }
}
