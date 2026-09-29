package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
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

/** Tercera tanda de #45: comprueba edición de miembros con SQL real. */
class MemberEditIT {

    private PostgresPlanFixture fixture;
    private GymContext primaryContext;
    private MemberRepository primary;
    private MemberRepository secondary;
    private MemberService primaryService;

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
    }

    @AfterEach
    void removeOnlyTestSchema() throws Exception {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void editsOnlyOwnMemberAndPreservesGymAndCreationDateIgnoringForeignDocument() throws Exception {
        Member own = primary.save("Jaime", "Cardona", "DOC-100", null, null);
        Member foreign = secondary.save("Ana", "Otro gimnasio", "DOC-200", null, null);
        StoredMember before = storedMember(own.getId());
        StoredMember foreignBefore = storedMember(foreign.getId());

        Member edited = primaryService.updateMember(own.getId(), " Jaime David ", " Cardona Marmol ",
                " DOC-200 ", " 5555-5555 ", " jaime@example.com ");
        assertEquals(own.getId(), edited.getId());
        assertEquals("Jaime David", edited.getFirstNames());
        assertEquals("Cardona Marmol", edited.getLastNames());
        assertEquals("DOC-200", edited.getDocumentNumber());
        assertEquals("5555-5555", edited.getPhone());
        assertEquals("jaime@example.com", edited.getEmail());
        StoredMember after = storedMember(own.getId());
        assertEquals(before.gymId(), after.gymId());
        assertEquals(41, after.gymId());
        assertEquals(before.createdAt(), after.createdAt());
        assertEquals(before.createdAt(), edited.getCreatedAt());
        assertEquals(foreignBefore, storedMember(foreign.getId()));
        assertEquals(1, primary.findAll().size());
        assertEquals(1, secondary.findAll().size());
        fixture.assertResourcesClosed();
    }

    @Test
    void duplicateLookupExcludesOnlySelectedMemberWithinCurrentGym() throws Exception {
        Member own = primary.save("Jaime David", "Cardona Marmol", "DOC-100", null, null);
        Member other = primary.save("Luis", "Prueba", "DOC-200", null, null);
        Member foreign = secondary.save("Ana", "Otro gimnasio", "DOC-100", null, null);
        StoredMember otherBefore = storedMember(other.getId());
        StoredMember foreignBefore = storedMember(foreign.getId());

        assertFalse(primary.existsByDocumentExcludingId("DOC-100", own.getId()));
        assertTrue(primary.existsByDocumentExcludingId("DOC-100", other.getId()));
        assertTrue(primary.existsByDocumentExcludingId("DOC-100", foreign.getId()));
        assertTrue(primary.existsByDocumentExcludingId("DOC-100", Integer.MAX_VALUE));
        primaryService.updateMember(own.getId(), "Jaime David", "Cardona Marmol editado", "DOC-100", null, null);
        StoredMember ownBeforeDuplicate = storedMember(own.getId());

        MemberValidationException failure = assertThrows(MemberValidationException.class,
                () -> primaryService.updateMember(own.getId(), "Cambio", "Rechazado", " DOC-200 ", null, null));
        assertTrue(failure.getMessage().contains("gimnasio actual"));
        assertEquals(ownBeforeDuplicate, storedMember(own.getId()));
        assertEquals(otherBefore, storedMember(other.getId()));
        assertEquals(foreignBefore, storedMember(foreign.getId()));
        fixture.assertResourcesClosed();
    }

    @Test
    void editingCanRemoveOptionalDataForSeveralMembersWithoutDuplicateConflict() throws Exception {
        Member first = primary.save("Jaime David", "Cardona Marmol", "DOC-100", "5555-5555", "jaime@example.com");
        Member second = primary.save("Ana", "Prueba", "DOC-200", null, null);
        for (Member member : new Member[] {first, second}) {
            Member edited = primaryService.updateMember(member.getId(), member.getFirstNames(), member.getLastNames(),
                    " ", "", null);
            assertNull(edited.getDocumentNumber());
            assertNull(edited.getPhone());
            assertNull(edited.getEmail());
            assertEquals(41, storedMember(member.getId()).gymId());
        }
        assertEquals(2, primary.findAll().size());
        fixture.assertResourcesClosed();
    }

    @Test
    void foreignAndMissingIdsCannotBeEditedEvenThroughRepositoryOrWithoutDocument() throws Exception {
        Member foreign = secondary.save("Ana", "Otro gimnasio", "DOC-100", null, null);
        StoredMember before = storedMember(foreign.getId());
        for (int id : new int[] {foreign.getId(), Integer.MAX_VALUE}) {
            MemberNotFoundException failure = assertThrows(MemberNotFoundException.class,
                    () -> primaryService.updateMember(id, "Cambio", "Rechazado", "DOC-200", null, null));
            assertEquals("No se encontró el miembro en el gimnasio actual.", failure.getMessage());
            assertThrows(MemberNotFoundException.class,
                    () -> primaryService.updateMember(id, "Cambio", "Rechazado", null, null, null));
            assertThrows(MemberNotFoundException.class,
                    () -> primary.update(id, "Cambio directo", "Rechazado", null, null, null));
        }
        assertEquals(before, storedMember(foreign.getId()));
        assertTrue(primary.findAll().isEmpty());
        fixture.assertResourcesClosed();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrInactiveGymStopsEditingAndDuplicateLookup(boolean inactive) throws Exception {
        Member foreign = secondary.save("Ana", "Otro gimnasio", "DOC-100", null, null);
        StoredMember before = storedMember(foreign.getId());
        fixture.execute(inactive
                ? "UPDATE gimnasio SET gimnasio_activo = false WHERE gimnasio_id = 41"
                : "DELETE FROM gimnasio WHERE gimnasio_id = 41");
        int resourcesBefore = fixture.resourceCount();
        assertThrows(IllegalStateException.class,
                () -> primary.existsByDocumentExcludingId("DOC-100", foreign.getId()));
        assertThrows(IllegalStateException.class,
                () -> primary.update(foreign.getId(), "Cambio", "Rechazado", null, null, null));
        assertEquals(resourcesBefore + 6, fixture.resourceCount(), "Sólo deben consultarse los datos del gimnasio.");
        assertEquals(before, storedMember(foreign.getId()));
        fixture.assertResourcesClosed();
    }

    @Test
    void gymLookupSqlErrorDoesNotPermitEditingAForeignMember() throws Exception {
        Member foreign = secondary.save("Ana", "Otro gimnasio", "DOC-100", null, null);
        StoredMember before = storedMember(foreign.getId());
        fixture.execute("ALTER TABLE gimnasio RENAME COLUMN nombre TO nombre_temporal");
        int resourcesBefore = fixture.resourceCount();
        assertThrows(SQLException.class,
                () -> primary.existsByDocumentExcludingId("DOC-100", foreign.getId()));
        assertThrows(SQLException.class,
                () -> primary.update(foreign.getId(), "Cambio", "Rechazado", null, null, null));
        assertEquals(resourcesBefore + 4, fixture.resourceCount());
        assertEquals(before, storedMember(foreign.getId()));
        fixture.assertResourcesClosed();
    }

    @Test
    void duplicateConstraintAndRealSqlErrorsCloseEditingResourcesWithoutChangingRows() throws Exception {
        Member own = primary.save("Jaime David", "Cardona Marmol", "DOC-100", null, null);
        primary.save("Ana", "Prueba", "DOC-200", null, null);
        StoredMember before = storedMember(own.getId());
        SQLException duplicate = assertThrows(SQLException.class,
                () -> primary.update(own.getId(), "Cambio", "Rechazado", "DOC-200", null, null));
        assertEquals("23505", duplicate.getSQLState());
        assertEquals(before, storedMember(own.getId()));

        fixture.execute("ALTER TABLE miembro RENAME COLUMN numero_documento TO documento_temporal");
        int resourcesBefore = fixture.resourceCount();
        assertThrows(SQLException.class,
                () -> primary.existsByDocumentExcludingId("DOC-100", own.getId()));
        assertThrows(SQLException.class,
                () -> primary.update(own.getId(), "Cambio", "Rechazado", null, null, null));
        assertEquals(resourcesBefore + 4, fixture.resourceCount());
        fixture.execute("ALTER TABLE miembro RENAME COLUMN documento_temporal TO numero_documento");
        assertEquals(before, storedMember(own.getId()));
        fixture.assertResourcesClosed();
    }

    @Test
    void emptyUpdateReturningResultIsNotReportedAsSuccess() throws Exception {
        Member own = primary.save("Jaime David", "Cardona Marmol", "DOC-100", null, null);
        StoredMember before = storedMember(own.getId());
        fixture.execute("""
                CREATE FUNCTION omitir_edicion_miembro() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RETURN NULL; END $$;
                CREATE TRIGGER omitir_edicion_miembro BEFORE UPDATE ON miembro
                FOR EACH ROW EXECUTE FUNCTION omitir_edicion_miembro();
                """);
        assertThrows(MemberNotFoundException.class,
                () -> primary.update(own.getId(), "Cambio", "Omitido", null, null, null));
        assertEquals(before, storedMember(own.getId()));
        fixture.assertResourcesClosed();
    }

    private StoredMember storedMember(int id) throws SQLException {
        try (Connection connection = fixture.openFixtureConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT gimnasio_id, nombres, apellidos, numero_documento, telefono, correo, miembro_creado_en
                     FROM miembro WHERE miembro_id = ?
                     """)) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "Falta el miembro ficticio " + id);
                return new StoredMember(result.getInt("gimnasio_id"), result.getString("nombres"),
                        result.getString("apellidos"), result.getString("numero_documento"),
                        result.getString("telefono"), result.getString("correo"),
                        result.getTimestamp("miembro_creado_en").toLocalDateTime());
            }
        }
    }

    private record StoredMember(int gymId, String firstNames, String lastNames, String document,
                                String phone, String email, LocalDateTime createdAt) { }
}
