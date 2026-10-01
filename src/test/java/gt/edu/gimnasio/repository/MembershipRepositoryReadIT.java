package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.model.Membership;
import gt.edu.gimnasio.service.GymContext;

/** Primera tanda de #55: aislamiento de lectura, sin cambiar todavía la asignación. */
class MembershipRepositoryReadIT {

    private PostgresPlanFixture fixture;
    private GymContext context;
    private MembershipRepository repository;

    @BeforeEach
    void prepareSchema() throws Exception {
        fixture = new PostgresPlanFixture(false);
        context = new GymContext();
        repository = new MembershipRepository(context);
    }

    @AfterEach
    void removeTestSchema() throws Exception {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void listsOnlyCurrentGymHistoryInPreviousOrderWithMemberAndPlan() throws Exception {
        seedMembershipsInTwoGyms();

        List<Membership> own = repository.findAll();
        assertEquals(41, context.getCurrentGymId());
        assertEquals(List.of(502, 501), own.stream().map(Membership::getId).toList());
        assertEquals("Ana Prueba", own.getFirst().getMemberName());
        assertEquals("Plan principal", own.getFirst().getPlanName());
        assertEquals("CONGELADA", own.getFirst().getStatus());
        assertEquals(LocalDate.of(2026, 10, 1), own.getFirst().getStartDate());
        assertEquals(LocalDate.of(2026, 10, 7), own.getFirst().getEndDate());
        assertEquals("ACTIVA", own.getLast().getStatus());

        MembershipRepository secondary = new MembershipRepository(secondaryGymContext());
        List<Membership> foreign = secondary.findAll();
        assertEquals(List.of(601), foreign.stream().map(Membership::getId).toList());
        assertEquals("Juan Otro", foreign.getFirst().getMemberName());
        assertEquals("Plan secundario", foreign.getFirst().getPlanName());
        fixture.assertResourcesClosed();
    }

    @Test
    void returnsEmptyListWhenOnlyAnotherGymHasMemberships() throws Exception {
        seedMembershipsInTwoGyms();
        fixture.execute("DELETE FROM membresia WHERE gimnasio_id = 41");
        assertTrue(repository.findAll().isEmpty());
        assertEquals(1, new MembershipRepository(secondaryGymContext()).findAll().size());
        fixture.assertResourcesClosed();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrInactiveGymStopsHistoryQuery(boolean inactive) throws Exception {
        fixture.execute(inactive
                ? "UPDATE gimnasio SET gimnasio_activo = false WHERE gimnasio_id = 41"
                : "DELETE FROM gimnasio WHERE gimnasio_id = 41");
        IllegalStateException failure = assertThrows(IllegalStateException.class, repository::findAll);
        assertTrue(failure.getMessage().contains(inactive ? "inactivo" : "No se encontró"));
        assertEquals(3, fixture.resourceCount(), "Sólo debe ejecutarse la consulta del gimnasio.");
        fixture.assertResourcesClosed();
    }

    @Test
    void closesJdbcResourcesWhenHistoryQueryFails() throws Exception {
        context.getCurrentGymId();
        int before = fixture.resourceCount();
        fixture.execute("ALTER TABLE membresia RENAME COLUMN estado TO estado_temporal");
        assertThrows(SQLException.class, repository::findAll);
        assertEquals(before + 2, fixture.resourceCount());
        fixture.assertResourcesClosed();
    }

    @Test
    void historyCreatedBeforeMigrationIsStillVisible() throws Exception {
        fixture.close();
        fixture = null;
        fixture = new PostgresPlanFixture(true, PostgresPlanFixture.LEGACY_MEMBER_SETUP_SQL);
        repository = new MembershipRepository();

        List<Membership> history = repository.findAll();
        assertEquals(List.of(301), history.stream().map(Membership::getId).toList());
        assertEquals(101, history.getFirst().getMemberId());
        assertEquals("Jaime David Cardona Marmol", history.getFirst().getMemberName());
        assertEquals("Plan previo", history.getFirst().getPlanName());
        fixture.assertResourcesClosed();
    }

    private void seedMembershipsInTwoGyms() throws SQLException {
        fixture.execute("""
                INSERT INTO plan (plan_id, gimnasio_id, nombre, duracion_dias)
                VALUES (101, 41, 'Plan principal', 30), (201, 73, 'Plan secundario', 7);
                INSERT INTO miembro (miembro_id, gimnasio_id, nombres, apellidos)
                VALUES (301, 41, 'Ana', 'Prueba'), (401, 73, 'Juan', 'Otro');
                INSERT INTO membresia (membresia_id, gimnasio_id, plan_id, miembro_id,
                                       estado, fecha_inicio, fecha_fin)
                VALUES (501, 41, 101, 301, 'ACTIVA', DATE '2026-09-01', DATE '2026-09-30'),
                       (502, 41, 101, 301, 'CONGELADA', DATE '2026-10-01', DATE '2026-10-07'),
                       (601, 73, 201, 401, 'ACTIVA', DATE '2026-09-01', DATE '2026-09-07');
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
