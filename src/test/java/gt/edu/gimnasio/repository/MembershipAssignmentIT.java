package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.model.Member;
import gt.edu.gimnasio.model.Plan;
import gt.edu.gimnasio.service.GymContext;
import gt.edu.gimnasio.service.MembershipService;
import gt.edu.gimnasio.service.MembershipValidationException;

/** Asignaciones reales en PostgreSQL, aisladas en esquemas temporales. */
class MembershipAssignmentIT {

    private PostgresPlanFixture fixture;
    private GymContext context;
    private MemberRepository members;
    private PlanRepository plans;
    private MembershipRepository memberships;
    private MembershipService service;

    @BeforeEach
    void prepareSchema() throws Exception {
        fixture = new PostgresPlanFixture(false);
        context = new GymContext();
        members = new MemberRepository(context);
        plans = new PlanRepository(context);
        memberships = new MembershipRepository(context);
        service = new MembershipService(memberships);
    }

    @AfterEach
    void removeTestSchema() throws Exception {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void assignsMembershipAndCodeTogetherWithExplicitGymIdAndCurrentPlanDuration() throws Exception {
        Member member = members.save("Jaime David", "Cardona Marmol", "DOC-100", null, null);
        Plan plan = plans.save("Mensual", 30);
        fixture.execute("UPDATE plan SET duracion_dias = 7 WHERE plan_id = " + plan.getId());

        String code = service.assignMembership(member, plan, LocalDate.of(2026, 10, 1));
        assertTrue(code.startsWith("GYM-"));
        assertEquals(1, memberships.findAll().size());
        try (Connection connection = fixture.openFixtureConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT membresia.gimnasio_id, membresia.miembro_id, membresia.plan_id,
                            membresia.fecha_fin, codigo_acceso.codigo
                     FROM membresia JOIN codigo_acceso
                       ON codigo_acceso.membresia_id = membresia.membresia_id
                     WHERE membresia.miembro_id = ?
                     """)) {
            statement.setInt(1, member.getId());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(41, result.getInt("gimnasio_id"));
                assertEquals(member.getId(), result.getInt("miembro_id"));
                assertEquals(plan.getId(), result.getInt("plan_id"));
                assertEquals(LocalDate.of(2026, 10, 7), result.getObject("fecha_fin", LocalDate.class));
                assertEquals(code, result.getString("codigo"));
                assertFalse(result.next());
            }
        }
        fixture.assertResourcesClosed();
    }

    @Test
    void rejectsForeignMemberAndPlanIdsWithoutWritingAnything() throws Exception {
        Member ownMember = members.save("Propio", "Principal", null, null, null);
        Plan ownPlan = plans.save("Propio", 30);
        GymContext secondary = secondaryGymContext();
        Member foreignMember = new MemberRepository(secondary).save("Ajeno", "Secundario", null, null, null);
        Plan foreignPlan = new PlanRepository(secondary).save("Ajeno", 7);

        MembershipValidationException foreignMemberFailure = assertThrows(MembershipValidationException.class,
                () -> service.assignMembership(foreignMember, ownPlan, LocalDate.of(2026, 10, 1)));
        assertTrue(foreignMemberFailure.getMessage().contains("miembro en el gimnasio actual"));
        MembershipValidationException foreignPlanFailure = assertThrows(MembershipValidationException.class,
                () -> service.assignMembership(ownMember, foreignPlan, LocalDate.of(2026, 10, 1)));
        assertTrue(foreignPlanFailure.getMessage().contains("plan activo en el gimnasio actual"));
        assertEquals(0, countRows("membresia"));
        assertEquals(0, countRows("codigo_acceso"));

        String code = new MembershipService(new MembershipRepository(secondary))
                .assignMembership(foreignMember, foreignPlan, LocalDate.of(2026, 10, 1));
        assertTrue(code.startsWith("GYM-"));
        assertTrue(memberships.findAll().isEmpty());
        assertEquals(1, new MembershipRepository(secondary).findAll().size());
        assertEquals(1, countRows("membresia"));
        fixture.assertResourcesClosed();
    }

    @Test
    void activeMembershipCheckIsScopedAndPreventsASecondAssignment() throws Exception {
        Member member = members.save("Jaime", "Principal", null, null, null);
        Plan plan = plans.save("Mensual", 30);
        service.assignMembership(member, plan, LocalDate.of(2026, 10, 1));

        MembershipValidationException failure = assertThrows(MembershipValidationException.class,
                () -> service.assignMembership(member, plan, LocalDate.of(2026, 11, 1)));
        assertTrue(failure.getMessage().contains("ya tiene una membresía activa"));
        assertEquals(1, countRows("membresia"));
        assertEquals(1, countRows("codigo_acceso"));
        fixture.assertResourcesClosed();
    }

    @Test
    void staleMemberAndDeactivatedPlanAreRejectedBeforeInsert() throws Exception {
        Member member = members.save("Jaime", "Principal", null, null, null);
        Plan plan = plans.save("Mensual", 30);
        fixture.execute("DELETE FROM miembro WHERE miembro_id = " + member.getId());
        MembershipValidationException missingMember = assertThrows(MembershipValidationException.class,
                () -> service.assignMembership(member, plan, LocalDate.of(2026, 10, 1)));
        assertTrue(missingMember.getMessage().contains("miembro en el gimnasio actual"));

        Member another = members.save("Ana", "Principal", null, null, null);
        plans.updateActiveStatus(plan.getId(), false);
        MembershipValidationException inactivePlan = assertThrows(MembershipValidationException.class,
                () -> service.assignMembership(another, plan, LocalDate.of(2026, 10, 1)));
        assertTrue(inactivePlan.getMessage().contains("plan activo en el gimnasio actual"));
        assertEquals(0, countRows("membresia"));
        fixture.assertResourcesClosed();
    }

    @Test
    void cachedGymThatWasDeactivatedCannotAssign() throws Exception {
        Member member = members.save("Jaime", "Principal", null, null, null);
        Plan plan = plans.save("Mensual", 30);
        context.getCurrentGymId();
        fixture.execute("UPDATE gimnasio SET gimnasio_activo = FALSE WHERE gimnasio_id = 41");

        MembershipValidationException failure = assertThrows(MembershipValidationException.class,
                () -> service.assignMembership(member, plan, LocalDate.of(2026, 10, 1)));
        assertTrue(failure.getMessage().contains("gimnasio actual ya no está disponible"));
        assertEquals(0, countRows("membresia"));
        fixture.assertResourcesClosed();
    }

    @Test
    void failedCodeInsertRollsBackTheMembership() throws Exception {
        Member member = members.save("Jaime", "Principal", null, null, null);
        Plan plan = plans.save("Mensual", 30);
        fixture.execute("ALTER TABLE codigo_acceso ADD CONSTRAINT ck_codigo_prueba CHECK (FALSE)");

        assertThrows(SQLException.class,
                () -> service.assignMembership(member, plan, LocalDate.of(2026, 10, 1)));
        assertEquals(0, countRows("membresia"));
        assertEquals(0, countRows("codigo_acceso"));
        fixture.assertResourcesClosed();
    }

    @Test
    void migratedMemberAndPlanCanReceiveNewMembershipWithoutCrossingGyms() throws Exception {
        fixture.close();
        fixture = null;
        fixture = new PostgresPlanFixture(true, PostgresPlanFixture.LEGACY_MEMBER_SETUP_SQL);
        Member member = new MemberRepository().findAll().stream()
                .filter(candidate -> candidate.getId() == 102).findFirst().orElseThrow();
        Plan plan = new PlanRepository().findAll().getFirst();

        String code = new MembershipService(new MembershipRepository())
                .assignMembership(member, plan, LocalDate.of(2026, 10, 1));
        assertTrue(code.startsWith("GYM-"));
        assertEquals(2, countRows("membresia"));
        assertEquals(2, countRows("codigo_acceso"));
        fixture.assertResourcesClosed();
    }

    private GymContext secondaryGymContext() {
        return new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String ignored) throws SQLException {
                return super.findByName("Gimnasio Secundario");
            }
        });
    }

    private int countRows(String table) throws SQLException {
        if (!table.equals("membresia") && !table.equals("codigo_acceso")) {
            throw new IllegalArgumentException("Tabla de prueba no permitida.");
        }
        try (Connection connection = fixture.openFixtureConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
