package gt.edu.gimnasio.service;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import gt.edu.gimnasio.model.Member;
import gt.edu.gimnasio.repository.MemberRepository;

/** Reglas de registro; el aislamiento SQL se comprueba con PostgreSQL en los IT. */
class MemberServiceTest {

    private final RecordingRepository repository = new RecordingRepository();
    private final MemberService service = new MemberService(repository);

    @Test
    void normalizesFieldsBeforeDuplicateLookupAndRegistration() throws Exception {
        Member saved = service.createMember(" Jaime David ", " Cardona Marmol ",
                " DOC-100 ", " 5555-5555 ", " jaime@example.com ");
        assertEquals("DOC-100", repository.lookedUpDocument);
        assertEquals("Jaime David", saved.getFirstNames());
        assertEquals("Cardona Marmol", saved.getLastNames());
        assertEquals("DOC-100", saved.getDocumentNumber());
        assertEquals("5555-5555", saved.getPhone());
        assertEquals("jaime@example.com", saved.getEmail());
        assertEquals(1, repository.lookups);
        assertEquals(1, repository.writes);
    }

    @Test
    void blankOptionalFieldsBecomeNullWithoutCheckingADocument() throws Exception {
        Member saved = service.createMember("Jaime David", "Cardona Marmol", " \t ", "", null);
        assertNull(saved.getDocumentNumber());
        assertNull(saved.getPhone());
        assertNull(saved.getEmail());
        assertEquals(0, repository.lookups);
        assertEquals(1, repository.writes);
    }

    @Test
    void invalidNamesAndDocumentDoNotAccessTheRepository() {
        assertThrows(MemberValidationException.class,
                () -> service.createMember(" ", "Cardona Marmol", "DOC-100", null, null));
        assertThrows(MemberValidationException.class,
                () -> service.createMember("Jaime David", null, "DOC-100", null, null));
        assertThrows(MemberValidationException.class,
                () -> service.createMember("Jaime David", "Cardona Marmol", "ABC", null, null));
        assertEquals(0, repository.lookups);
        assertEquals(0, repository.writes);
    }

    @Test
    void duplicateInCurrentGymPreventsRegistrationAndExplainsTheScope() {
        repository.duplicate = true;
        MemberValidationException failure = assertThrows(MemberValidationException.class,
                () -> service.createMember("Jaime David", "Cardona Marmol", " DOC-100 ", null, null));
        assertEquals("DOC-100", repository.lookedUpDocument);
        assertTrue(failure.getMessage().contains("gimnasio actual"));
        assertEquals(0, repository.writes);
    }

    @Test
    void lookupAndWriteFailuresAreNotReportedAsSuccessfulRegistrations() {
        repository.lookupFailure = new SQLException("Fallo de consulta");
        assertSame(repository.lookupFailure, assertThrows(SQLException.class,
                () -> service.createMember("Jaime David", "Cardona Marmol", "DOC-100", null, null)));
        assertEquals(0, repository.writes);

        repository.lookupFailure = null;
        repository.writeFailure = new SQLException("Fallo de escritura");
        assertSame(repository.writeFailure, assertThrows(SQLException.class,
                () -> service.createMember("Jaime David", "Cardona Marmol", null, null, null)));
    }

    private static class RecordingRepository extends MemberRepository {
        int lookups;
        int writes;
        boolean duplicate;
        String lookedUpDocument;
        SQLException lookupFailure;
        SQLException writeFailure;

        @Override
        public boolean existsByDocument(String documentNumber) throws SQLException {
            lookups++;
            lookedUpDocument = documentNumber;
            if (lookupFailure != null) {
                throw lookupFailure;
            }
            return duplicate;
        }

        @Override
        public Member save(String firstNames, String lastNames, String documentNumber,
                           String phone, String email) throws SQLException {
            writes++;
            if (writeFailure != null) {
                throw writeFailure;
            }
            return new Member(7, firstNames, lastNames, documentNumber, phone, email, LocalDateTime.now());
        }
    }
}
