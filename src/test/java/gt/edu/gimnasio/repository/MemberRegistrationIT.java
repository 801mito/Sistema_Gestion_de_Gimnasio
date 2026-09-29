package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.model.Member;
import gt.edu.gimnasio.service.GymContext;
import gt.edu.gimnasio.service.MemberService;
import gt.edu.gimnasio.service.MemberValidationException;

/** Segunda tanda de #45: registro y documentos duplicados, no edición. */
class MemberRegistrationIT {

    private PostgresPlanFixture fixture;
    private GymContext primaryContext;
    private MemberRepository primary;
    private MemberRepository secondary;
    private MemberService primaryService;
    private MemberService secondaryService;

    @BeforeEach
    void prepareSchema() throws Exception {
        fixture = new PostgresPlanFixture(false);
        primaryContext = new GymContext();
        primary = new MemberRepository(primaryContext);
        secondary = new MemberRepository(new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String ignored) throws SQLException {
                return super.findByName("Gimnasio Secundario");
            }
        }));
        primaryService = new MemberService(primary);
        secondaryService = new MemberService(secondary);
    }

    @AfterEach
    void removeOnlyTestSchema() throws Exception {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void registrationUsesResolvedGymInsteadOfDefaultAndNormalizesOptionalFields() throws Exception {
        // Incluso un default de otro gimnasio no debe decidir la pertenencia.
        fixture.execute("ALTER TABLE miembro ALTER COLUMN gimnasio_id SET DEFAULT 73");
        Member saved = primaryService.createMember(" Jaime David ", " Cardona Marmol ",
                " DOC-100 ", " 5555-5555 ", " jaime@example.com ");
        assertEquals(41, primaryContext.getCurrentGymId());
        assertEquals(41, storedGymId(saved.getId()));
        assertEquals("Jaime David", saved.getFirstNames());
        assertEquals("Cardona Marmol", saved.getLastNames());
        assertEquals("DOC-100", saved.getDocumentNumber());
        assertEquals("5555-5555", saved.getPhone());
        assertEquals("jaime@example.com", saved.getEmail());
        assertNotNull(saved.getCreatedAt());
        assertEquals(List.of(saved.getId()), ids(primary.findAll()));
        assertTrue(secondary.findAll().isEmpty());
        fixture.assertResourcesClosed();
    }

    @Test
    void sameDocumentIsAllowedAcrossGymsButRejectedWithinEachGym() throws Exception {
        Member foreign = secondaryService.createMember("Ana", "Otro gimnasio", "DOC-100", null, null);
        assertFalse(primary.existsByDocument("DOC-100"));
        assertTrue(secondary.existsByDocument("DOC-100"));
        Member own = primaryService.createMember("Jaime David", "Cardona Marmol", " DOC-100 ", null, null);
        assertNotEquals(foreign.getId(), own.getId());
        assertEquals(41, storedGymId(own.getId()));
        assertEquals(73, storedGymId(foreign.getId()));

        MemberValidationException primaryFailure = assertThrows(MemberValidationException.class,
                () -> primaryService.createMember("Duplicado", "Principal", "DOC-100", null, null));
        assertTrue(primaryFailure.getMessage().contains("gimnasio actual"));
        assertThrows(MemberValidationException.class,
                () -> secondaryService.createMember("Duplicado", "Secundario", " DOC-100 ", null, null));
        assertEquals(List.of(own.getId()), ids(primary.findAll()));
        assertEquals(List.of(foreign.getId()), ids(secondary.findAll()));
        fixture.assertResourcesClosed();
    }

    @Test
    void multipleMembersWithoutDocumentRemainAllowedAndUseCurrentGym() throws Exception {
        Member first = primaryService.createMember("Jaime David", "Cardona Marmol", " ", "", null);
        Member second = primaryService.createMember("Ana", "Prueba", null, null, " ");
        assertNotEquals(first.getId(), second.getId());
        for (Member member : primary.findAll()) {
            assertEquals(41, storedGymId(member.getId()));
            assertNull(member.getDocumentNumber());
            assertNull(member.getPhone());
            assertNull(member.getEmail());
        }
        assertEquals(2, primary.findAll().size());
        assertFalse(primary.existsByDocument(null));
        fixture.assertResourcesClosed();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrInactiveGymStopsLookupAndInsertBeforeMemberSql(boolean inactive) throws Exception {
        fixture.execute(inactive
                ? "UPDATE gimnasio SET gimnasio_activo = false WHERE gimnasio_id = 41"
                : "DELETE FROM gimnasio WHERE gimnasio_id = 41");
        assertThrows(IllegalStateException.class, () -> primary.existsByDocument("DOC-100"));
        assertThrows(IllegalStateException.class,
                () -> primary.save("Jaime David", "Cardona Marmol", null, null, null));
        assertEquals(6, fixture.resourceCount(), "Sólo deben ejecutarse las dos consultas del gimnasio.");
        assertEquals(0, memberCount());
        fixture.assertResourcesClosed();
    }

    @Test
    void gymLookupSqlFailureCannotFallBackToTheDatabaseDefault() throws Exception {
        fixture.execute("ALTER TABLE miembro ALTER COLUMN gimnasio_id SET DEFAULT 41");
        fixture.execute("ALTER TABLE gimnasio RENAME COLUMN nombre TO nombre_temporal");
        assertThrows(SQLException.class, () -> primary.existsByDocument("DOC-100"));
        assertThrows(SQLException.class,
                () -> primary.save("Jaime David", "Cardona Marmol", null, null, null));
        assertEquals(4, fixture.resourceCount());
        assertEquals(0, memberCount());
        fixture.assertResourcesClosed();
    }

    @Test
    void databaseUniquenessProtectsRegistrationEvenWithoutServicePrecheck() throws Exception {
        Member own = primary.save("Jaime David", "Cardona Marmol", "DOC-100", null, null);
        SQLException failure = assertThrows(SQLException.class,
                () -> primary.save("Duplicado", "Principal", "DOC-100", null, null));
        assertEquals("23505", failure.getSQLState());
        Member foreign = secondary.save("Ana", "Otro gimnasio", "DOC-100", null, null);
        assertEquals(41, storedGymId(own.getId()));
        assertEquals(73, storedGymId(foreign.getId()));
        assertEquals(2, memberCount());
        fixture.assertResourcesClosed();
    }

    @Test
    void duplicateLookupAndInsertCloseResourcesOnRealSqlErrors() throws Exception {
        primaryContext.getCurrentGymId();
        fixture.execute("ALTER TABLE miembro RENAME COLUMN numero_documento TO documento_temporal");
        int before = fixture.resourceCount();
        assertThrows(SQLException.class, () -> primary.existsByDocument("DOC-100"));
        assertThrows(SQLException.class,
                () -> primary.save("Jaime David", "Cardona Marmol", "DOC-100", null, null));
        assertEquals(before + 4, fixture.resourceCount());
        assertEquals(0, memberCount());
        fixture.assertResourcesClosed();
    }

    @Test
    void emptyReturningResultDoesNotReportSuccessAndClosesResources() throws Exception {
        fixture.execute("""
                CREATE FUNCTION omitir_miembro() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RETURN NULL; END $$;
                CREATE TRIGGER omitir_miembro BEFORE INSERT ON miembro
                FOR EACH ROW EXECUTE FUNCTION omitir_miembro();
                """);
        SQLException failure = assertThrows(SQLException.class,
                () -> primary.save("Jaime David", "Cardona Marmol", null, null, null));
        assertEquals("PostgreSQL no devolvió el miembro registrado.", failure.getMessage());
        assertEquals(0, memberCount());
        fixture.assertResourcesClosed();
    }

    private List<Integer> ids(List<Member> members) {
        return members.stream().map(Member::getId).toList();
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

    private int memberCount() throws SQLException {
        try (Connection connection = fixture.openFixtureConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM miembro");
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
