package gt.edu.gimnasio.service;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import gt.edu.gimnasio.model.Employee;
import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.repository.GymRepository;

class GymContextTest {

    @Test
    void resolvesAuthenticatedEmployeesGymByIdWithoutUsingInitialGymName() throws SQLException {
        AtomicInteger lookups = new AtomicInteger();
        Employee employee = new Employee(5, 73, "Gimnasio Secundario", "jaime",
                true, true, LocalDateTime.now());
        GymContext context = new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findById(int id) {
                assertEquals(73, id);
                lookups.incrementAndGet();
                return Optional.of(new Gym(id, "Gimnasio Secundario", true, LocalDateTime.now()));
            }

            @Override
            public Optional<Gym> findByName(String name) {
                fail("El contexto autenticado no debe usar el gimnasio fijo.");
                return Optional.empty();
            }
        }, employee);

        assertEquals(73, context.getCurrentGymId());
        assertEquals(73, context.getCurrentGymId());
        assertEquals(1, lookups.get());
    }

    @Test
    void rejectsUnavailableAuthenticatedGymAndInactiveAccounts() {
        Employee employee = new Employee(5, 73, "Gimnasio Secundario", "jaime",
                true, true, LocalDateTime.now());
        GymContext context = new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findById(int id) {
                return Optional.of(new Gym(id, "Gimnasio Secundario", false, LocalDateTime.now()));
            }
        }, employee);
        assertThrows(IllegalStateException.class, context::getCurrentGymId);

        assertThrows(IllegalArgumentException.class, () -> new GymContext(
                new Employee(5, 73, "Gimnasio Secundario", "jaime",
                        false, true, LocalDateTime.now())));
    }

    @Test
    void resolvesInitialGymByNameAndCachesItsActualId() throws SQLException {
        AtomicInteger lookups = new AtomicInteger();
        Gym gym = new Gym(73, "Gimnasio Principal", true, LocalDateTime.now());
        GymContext context = new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String name) {
                assertEquals("Gimnasio Principal", name);
                lookups.incrementAndGet();
                return Optional.of(gym);
            }
        });

        assertSame(gym, context.getCurrentGym());
        assertEquals(73, context.getCurrentGymId());
        assertEquals(1, lookups.get());
    }

    @Test
    void reportsMissingInitialGym() {
        GymContext context = new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String name) {
                return Optional.empty();
            }
        });

        assertEquals("No se encontró el gimnasio inicial en PostgreSQL.",
                assertThrows(IllegalStateException.class, context::getCurrentGymId).getMessage());
    }

    @Test
    void rejectsInactiveInitialGym() {
        GymContext context = new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String name) {
                return Optional.of(new Gym(42, name, false, LocalDateTime.now()));
            }
        });

        assertEquals("El gimnasio inicial está inactivo en PostgreSQL.",
                assertThrows(IllegalStateException.class, context::getCurrentGymId).getMessage());
    }

    @Test
    void propagatesDatabaseFailureWithoutCachingAFailedLookup() throws SQLException {
        AtomicInteger attempts = new AtomicInteger();
        GymContext context = new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String name) throws SQLException {
                if (attempts.getAndIncrement() == 0) {
                    throw new SQLException("Fallo simulado");
                }
                return Optional.of(new Gym(42, name, true, LocalDateTime.now()));
            }
        });

        assertThrows(SQLException.class, context::getCurrentGymId);
        assertEquals(42, context.getCurrentGymId());
        assertEquals(2, attempts.get());
    }
}
