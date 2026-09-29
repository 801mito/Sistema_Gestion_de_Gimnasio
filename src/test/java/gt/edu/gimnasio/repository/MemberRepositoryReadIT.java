package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

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

/** Primera tanda de #45: comprueba sólo consultas, no aislamiento de escrituras. */
class MemberRepositoryReadIT {

    private PostgresPlanFixture fixture;
    private GymContext context;
    private MemberRepository primary;

    @BeforeEach
    void prepareSchema() throws Exception {
        fixture = new PostgresPlanFixture(false);
        context = new GymContext();
        primary = new MemberRepository(context);
    }

    @AfterEach
    void removeTestSchema() throws Exception {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void listsOnlyCurrentGymSortedByLastAndFirstNamesAndPreservesOptionalFields() throws Exception {
        fixture.execute("""
                INSERT INTO miembro (gimnasio_id, nombres, apellidos, numero_documento, telefono, correo)
                VALUES (41, 'Luis', 'Zapata', 'DOC-100', '5555-5555', 'luis@example.com'),
                       (41, 'Jaime David', 'Cardona Marmol', NULL, NULL, NULL),
                       (41, 'Ana', 'Cardona Marmol', 'DOC-200', NULL, NULL),
                       (73, 'Ajeno', 'Otro gimnasio', 'DOC-100', NULL, NULL)
                """);
        List<Member> members = primary.findAll();
        assertEquals(41, context.getCurrentGymId());
        assertEquals(List.of("Ana", "Jaime David", "Luis"), members.stream().map(Member::getFirstNames).toList());
        Member jaime = members.get(1);
        assertEquals("Cardona Marmol", jaime.getLastNames());
        assertNull(jaime.getDocumentNumber());
        assertNull(jaime.getPhone());
        assertNull(jaime.getEmail());
        assertNotNull(jaime.getCreatedAt());
        assertEquals("5555-5555", members.getLast().getPhone());
        assertEquals("luis@example.com", members.getLast().getEmail());

        MemberRepository secondary = new MemberRepository(new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String ignored) throws SQLException {
                return super.findByName("Gimnasio Secundario");
            }
        }));
        assertEquals(List.of("Ajeno"), secondary.findAll().stream().map(Member::getFirstNames).toList());
        fixture.assertResourcesClosed();
    }

    @Test
    void returnsEmptyListWhenOnlyOtherGymHasMembers() throws Exception {
        fixture.execute("INSERT INTO miembro (gimnasio_id, nombres, apellidos) VALUES (73, 'Ajeno', 'Otro')");
        assertTrue(primary.findAll().isEmpty());
        fixture.assertResourcesClosed();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrInactiveGymStopsQueryAndClosesLookupResources(boolean inactive) throws Exception {
        fixture.execute(inactive
                ? "UPDATE gimnasio SET gimnasio_activo = false WHERE gimnasio_id = 41"
                : "DELETE FROM gimnasio WHERE gimnasio_id = 41");
        IllegalStateException failure = assertThrows(IllegalStateException.class, primary::findAll);
        assertTrue(failure.getMessage().contains(inactive ? "inactivo" : "No se encontró"));
        assertEquals(3, fixture.resourceCount(), "Sólo debe ejecutarse la consulta del gimnasio.");
        fixture.assertResourcesClosed();
    }

    @Test
    void closesReadResourcesOnDatabaseError() throws Exception {
        context.getCurrentGymId();
        int before = fixture.resourceCount();
        fixture.execute("ALTER TABLE miembro RENAME COLUMN nombres TO nombres_temporales");
        assertThrows(SQLException.class, primary::findAll);
        assertEquals(before + 2, fixture.resourceCount());
        fixture.assertResourcesClosed();
    }

    @Test
    void gymLookupDatabaseErrorIsNotTreatedAsAnEmptyMemberList() throws Exception {
        fixture.execute("ALTER TABLE gimnasio RENAME COLUMN nombre TO nombre_temporal");
        assertThrows(SQLException.class, primary::findAll);
        assertEquals(2, fixture.resourceCount());
        fixture.assertResourcesClosed();
    }
}
