package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.model.Member;
import gt.edu.gimnasio.service.GymContext;
import gt.edu.gimnasio.service.MemberService;
import gt.edu.gimnasio.service.MemberValidationException;

/** Comprueba miembros y relaciones reales del modelo 1.0 después de migrar a 1.1. */
class MemberMigrationIT {

    private PostgresPlanFixture fixture;
    private GymContext context;
    private MemberRepository repository;
    private MemberService service;

    @BeforeEach
    void prepareMigratedSchema() throws Exception {
        fixture = new PostgresPlanFixture(true, PostgresPlanFixture.LEGACY_MEMBER_SETUP_SQL);
        context = new GymContext();
        repository = new MemberRepository(context);
        service = new MemberService(repository);
    }

    @AfterEach
    void removeOnlyTestSchema() throws Exception {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void legacyMembersStayVisibleWithTheirIdsDataAndExistingRelations() throws Exception {
        int gymId = context.getCurrentGymId();
        List<Member> members = repository.findAll();
        assertEquals(List.of(101, 102), members.stream().map(Member::getId).toList());
        Member jaime = members.getFirst();
        assertEquals("Jaime David", jaime.getFirstNames());
        assertEquals("Cardona Marmol", jaime.getLastNames());
        assertEquals("DOC-LEGACY", jaime.getDocumentNumber());
        assertEquals("5555-5555", jaime.getPhone());
        assertEquals("jaime@example.com", jaime.getEmail());
        assertEquals(LocalDateTime.of(2026, 8, 15, 10, 20, 30), jaime.getCreatedAt());
        Member ana = members.getLast();
        assertNull(ana.getDocumentNumber());
        assertNull(ana.getPhone());
        assertNull(ana.getEmail());
        assertEquals(LocalDateTime.of(2026, 8, 16, 11, 21, 31), ana.getCreatedAt());

        try (Connection connection = fixture.openFixtureConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT miembro.gimnasio_id AS gimnasio_miembro,
                            membresia.gimnasio_id AS gimnasio_membresia,
                            plan.gimnasio_id AS gimnasio_plan,
                            membresia.miembro_id, membresia.fecha_inicio, membresia.fecha_fin,
                            codigo_acceso.codigo, codigo_acceso.codigo_activo
                     FROM codigo_acceso
                     JOIN membresia ON membresia.membresia_id = codigo_acceso.membresia_id
                     JOIN miembro ON miembro.miembro_id = membresia.miembro_id
                     JOIN plan ON plan.plan_id = membresia.plan_id
                     WHERE codigo_acceso.codigo = 'GYM-LEGACY101'
                     """);
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals(gymId, result.getInt("gimnasio_miembro"));
            assertEquals(gymId, result.getInt("gimnasio_membresia"));
            assertEquals(gymId, result.getInt("gimnasio_plan"));
            assertEquals(101, result.getInt("miembro_id"));
            assertEquals(LocalDate.of(2026, 8, 1), result.getObject("fecha_inicio", LocalDate.class));
            assertEquals(LocalDate.of(2026, 8, 30), result.getObject("fecha_fin", LocalDate.class));
            assertEquals("GYM-LEGACY101", result.getString("codigo"));
            assertTrue(result.getBoolean("codigo_activo"));
            assertFalse(result.next());
        }
        fixture.assertResourcesClosed();
    }

    @Test
    void registrationAndEditAfterMigrationKeepOldMembersAndTheirRelationships() throws Exception {
        int gymId = context.getCurrentGymId();
        Member added = service.createMember("Nuevo", "Después", "DOC-NEW", null, null);
        assertNotEquals(101, added.getId());
        assertEquals(gymId, storedGymId(added.getId()));
        assertThrows(MemberValidationException.class,
                () -> service.createMember("Duplicado", "Después", "DOC-LEGACY", null, null));

        Member edited = service.updateMember(101, " Jaime David ", " Cardona Marmol editado ",
                " DOC-LEGACY ", " 5555-5555 ", " jaime@example.com ");
        assertEquals(101, edited.getId());
        assertEquals("Cardona Marmol editado", edited.getLastNames());
        assertEquals(LocalDateTime.of(2026, 8, 15, 10, 20, 30), edited.getCreatedAt());
        assertEquals(gymId, storedGymId(101));
        assertEquals(gymId, storedGymId(102));
        assertEquals(3, repository.findAll().size());
        assertThrows(MemberValidationException.class,
                () -> service.updateMember(102, "Ana", "Prueba", "DOC-NEW", null, null));
        assertNull(repository.findAll().stream().filter(member -> member.getId() == 102)
                .findFirst().orElseThrow().getDocumentNumber());

        try (Connection connection = fixture.openFixtureConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM membresia
                     JOIN codigo_acceso ON codigo_acceso.membresia_id = membresia.membresia_id
                     WHERE membresia.miembro_id = 101 AND codigo_acceso.codigo = 'GYM-LEGACY101'
                     """);
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
        fixture.assertResourcesClosed();
    }

    @Test
    void migratedDocumentMayExistInAnotherGymWithoutLeakingOrPermittingForeignEdit() throws Exception {
        fixture.execute("INSERT INTO gimnasio (nombre) VALUES ('Gimnasio Secundario')");
        MemberRepository secondary = new MemberRepository(new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String ignored) throws SQLException {
                return super.findByName("Gimnasio Secundario");
            }
        }));
        Member foreign = new MemberService(secondary)
                .createMember("Ajeno", "Secundario", "DOC-LEGACY", null, null);
        assertEquals(2, repository.findAll().size());
        assertEquals(List.of(foreign.getId()), secondary.findAll().stream().map(Member::getId).toList());
        service.updateMember(101, "Jaime David", "Cardona Marmol", "DOC-LEGACY", null, null);

        assertThrows(MemberNotFoundException.class,
                () -> repository.update(foreign.getId(), "Intento", "Ajeno", null, null, null));
        assertEquals("Ajeno", secondary.findAll().getFirst().getFirstNames());
        assertEquals("DOC-LEGACY", secondary.findAll().getFirst().getDocumentNumber());
        assertNotEquals(context.getCurrentGymId(), storedGymId(foreign.getId()));
        fixture.assertResourcesClosed();
    }

    @Test
    void aMappingFailureAfterResultSetCreationStillClosesEveryJdbcResource() throws Exception {
        context.getCurrentGymId();
        int resourcesBefore = fixture.resourceCount();
        fixture.rejectMemberMapping(true);
        try {
            SQLException failure = assertThrows(SQLException.class, repository::findAll);
            assertTrue(failure.getMessage().contains("Fallo de lectura de miembro"));
        } finally {
            fixture.rejectMemberMapping(false);
        }
        assertEquals(resourcesBefore + 3, fixture.resourceCount());
        fixture.assertResourcesClosed();
        assertEquals(2, repository.findAll().size());
        fixture.assertResourcesClosed();
    }

    private int storedGymId(int id) throws SQLException {
        try (Connection connection = fixture.openFixtureConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT gimnasio_id FROM miembro WHERE miembro_id = ?")) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "Falta el miembro ficticio " + id);
                return result.getInt(1);
            }
        }
    }
}
