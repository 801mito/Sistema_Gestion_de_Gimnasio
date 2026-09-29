package gt.edu.gimnasio.service;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import gt.edu.gimnasio.model.Plan;
import gt.edu.gimnasio.repository.PlanRepository;

class PlanServiceTest {

    private final RecordingRepository repository = new RecordingRepository();
    private final PlanService service = new PlanService(repository);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankNamesBeforeAccessingDatabase(String name) {
        assertThrows(PlanValidationException.class, () -> service.createPlan(name, 30));
        assertThrows(PlanValidationException.class, () -> service.updatePlan(7, name, 30));
        assertEquals(0, repository.calls);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsInvalidDurationsBeforeAccessingDatabase(int duration) {
        assertThrows(PlanValidationException.class, () -> service.createPlan("Mensual", duration));
        assertThrows(PlanValidationException.class, () -> service.updatePlan(7, "Mensual", duration));
        assertEquals(0, repository.calls);
    }

    @Test
    void trimsNamesForBothDuplicateLookupAndWrite() throws Exception {
        assertEquals("Mensual", service.createPlan("  Mensual  ", 30).getName());
        assertEquals("Mensual", repository.lookedUpName);
        assertEquals("Semanal", service.updatePlan(7, "  Semanal  ", 7).getName());
        assertEquals("Semanal", repository.lookedUpName);
        assertEquals(7, repository.excludedId);
    }

    @Test
    void duplicateNamesPreventInsertAndUpdate() {
        repository.duplicate = true;
        assertTrue(assertThrows(PlanValidationException.class,
                () -> service.createPlan("Mensual", 30)).getMessage().contains("gimnasio actual"));
        assertTrue(assertThrows(PlanValidationException.class,
                () -> service.updatePlan(7, "Mensual", 30)).getMessage().contains("gimnasio actual"));
        assertEquals(0, repository.writes);
    }

    private static class RecordingRepository extends PlanRepository {
        int calls;
        int writes;
        int excludedId;
        boolean duplicate;
        String lookedUpName;

        @Override
        public boolean existsByName(String name) {
            calls++;
            lookedUpName = name;
            return duplicate;
        }

        @Override
        public boolean existsByNameExcludingId(String name, int id) {
            excludedId = id;
            return existsByName(name);
        }

        @Override
        public Plan save(String name, int days) throws SQLException {
            calls++;
            writes++;
            return new Plan(7, name, days, true, LocalDateTime.now());
        }

        @Override
        public Plan update(int id, String name, int days) throws SQLException {
            return save(name, days);
        }
    }
}
