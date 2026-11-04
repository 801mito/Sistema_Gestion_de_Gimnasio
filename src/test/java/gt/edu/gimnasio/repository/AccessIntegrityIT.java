package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import gt.edu.gimnasio.model.AccessAttempt;
import gt.edu.gimnasio.model.AccessValidationResult;
import gt.edu.gimnasio.service.AccessValidationService;
import gt.edu.gimnasio.service.GymContext;

class AccessIntegrityIT {

    private static final String OWN_CODE = "GYM-AUDIT701";
    private static final String SEED = """
            INSERT INTO gimnasio (gimnasio_id, nombre)
            SELECT 73, 'Gimnasio Secundario'
            WHERE NOT EXISTS (SELECT 1 FROM gimnasio WHERE gimnasio_id = 73);
            INSERT INTO plan (plan_id, gimnasio_id, nombre, duracion_dias)
            SELECT 101, gimnasio_id, 'Prueba principal', 30 FROM gimnasio WHERE nombre = 'Gimnasio Principal';
            INSERT INTO plan (plan_id, gimnasio_id, nombre, duracion_dias) VALUES (201, 73, 'Prueba secundaria', 30);
            INSERT INTO miembro (miembro_id, gimnasio_id, nombres, apellidos)
            SELECT 301, gimnasio_id, 'Ana', 'Principal' FROM gimnasio WHERE nombre = 'Gimnasio Principal';
            INSERT INTO miembro (miembro_id, gimnasio_id, nombres, apellidos)
            VALUES (401, 73, 'Juan', 'Secundario'), (402, 73, 'Lucía', 'Secundaria');
            INSERT INTO membresia (membresia_id, gimnasio_id, miembro_id, plan_id, fecha_inicio, fecha_fin)
            SELECT 501, gimnasio_id, 301, 101, CURRENT_DATE - 7, CURRENT_DATE + 7
            FROM gimnasio WHERE nombre = 'Gimnasio Principal';
            INSERT INTO membresia (membresia_id, gimnasio_id, miembro_id, plan_id, fecha_inicio, fecha_fin)
            VALUES (601, 73, 401, 201, CURRENT_DATE - 7, CURRENT_DATE + 7);
            INSERT INTO codigo_acceso (codigo_acceso_id, membresia_id, codigo)
            VALUES (701, 501, 'GYM-AUDIT701'), (801, 601, 'GYM-AUDIT801');
            """;

    @ParameterizedTest
    @CsvSource({"false, true", "false, false", "true, true", "true, false"})
    void waitsForEarlierStatusChangeAndRecordsOnlyTheCurrentDecision(boolean migrated, boolean changeCode)
            throws Exception {
        try (var fixture = new PostgresPlanFixture(migrated, null, SEED);
             var executor = Executors.newSingleThreadExecutor();
             var writer = fixture.openFixtureConnection();
             var observer = fixture.openFixtureConnection()) {
            var gym = new GymContext();
            gym.getCurrentGymId();
            var attempts = new AccessAttemptRepository(gym);
            var service = new AccessValidationService(new AccessCodeRepository(gym), attempts);
            int writerPid = backendPid(writer);
            writer.setAutoCommit(false);
            Future<AccessValidationResult> decision;
            try {
                try (var statement = writer.createStatement()) {
                    statement.executeUpdate(changeCode
                            ? "UPDATE codigo_acceso SET codigo_activo = FALSE WHERE codigo_acceso_id = 701"
                            : "UPDATE membresia SET estado = 'CONGELADA' WHERE membresia_id = 501");
                }
                decision = executor.submit(() -> service.validate(OWN_CODE));
                awaitBlockedBy(observer, writerPid);
                assertFalse(decision.isDone());
                assertTrue(attempts.findAll().isEmpty());
                writer.commit();
            } finally {
                writer.rollback();
            }
            var result = decision.get(10, TimeUnit.SECONDS);
            assertFalse(result.isAuthorized());
            assertTrue(result.getReason().contains(changeCode ? "inactivo" : "congelada"));
            var history = attempts.findAll();
            assertEquals(1, history.size());
            assertEquals("RECHAZADO", history.getFirst().result());
            assertEquals(701, history.getFirst().accessCodeId());
        }
    }

    @ParameterizedTest
    @CsvSource({"false, true", "false, false", "true, true", "true, false"})
    void earlierValidationKeepsStatusStableUntilAttemptIsCommitted(boolean migrated, boolean changeCode)
            throws Exception {
        try (var fixture = new PostgresPlanFixture(migrated, null, SEED);
             var executor = Executors.newFixedThreadPool(2);
             var observer = fixture.openFixtureConnection()) {
            var gym = new GymContext();
            gym.getCurrentGymId();
            var allowSave = new CountDownLatch(1);
            var validationPid = new CompletableFuture<Integer>();
            var attempts = new AccessAttemptRepository(gym) {
                @Override
                public int save(Connection connection, int gymId, Integer codeId, boolean authorized, String reason)
                        throws SQLException {
                    validationPid.complete(backendPid(connection));
                    try {
                        if (!allowSave.await(10, TimeUnit.SECONDS)) {
                            throw new SQLException("No se liberó la validación.");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new SQLException(exception);
                    }
                    return super.save(connection, gymId, codeId, authorized, reason);
                }
            };
            var service = new AccessValidationService(new AccessCodeRepository(gym), attempts);
            var decision = executor.submit(() -> service.validate(OWN_CODE));
            try {
                int blockerPid = validationPid.get(5, TimeUnit.SECONDS);
                var change = executor.submit(() -> {
                    fixture.execute(changeCode
                            ? "UPDATE codigo_acceso SET codigo_activo = FALSE WHERE codigo_acceso_id = 701"
                            : "UPDATE membresia SET estado = 'CONGELADA' WHERE membresia_id = 501");
                    return null;
                });
                awaitBlockedBy(observer, blockerPid);
                assertFalse(change.isDone());
                assertTrue(attempts.findAll().isEmpty());
                allowSave.countDown();
                assertTrue(decision.get(10, TimeUnit.SECONDS).isAuthorized());
                change.get(10, TimeUnit.SECONDS);
                assertFalse(new AccessValidationService(new AccessCodeRepository(gym),
                        new AccessAttemptRepository(gym)).validate(OWN_CODE).isAuthorized());
                assertEquals(List.of("RECHAZADO", "AUTORIZADO"),
                        attempts.findAll().stream().map(AccessAttempt::result).toList());
            } finally {
                allowSave.countDown();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void protectsNewAssociationsAndMasksLegacyMismatchWithoutRewritingHistory(boolean migrated) throws Exception {
        String legacy = SEED + """
                INSERT INTO intento_acceso (intento_acceso_id, gimnasio_id, codigo_acceso_id, resultado, motivo, fecha_hora)
                SELECT 901, gimnasio_id, 801, 'RECHAZADO', 'Registro anterior', TIMESTAMP '2025-01-01 08:00:00'
                FROM gimnasio WHERE nombre = 'Gimnasio Principal';
                """;
        try (var fixture = new PostgresPlanFixture(migrated, null, legacy)) {
            var gym = new GymContext();
            int gymId = gym.getCurrentGymId();
            var attempts = new AccessAttemptRepository(gym);
            assertEquals(1, attempts.findAll().size());
            var masked = attempts.findAll().getFirst();
            assertEquals(901, masked.id());
            assertEquals(gymId, masked.gymId());
            assertEquals("Registro anterior", masked.reason());
            assertEquals("RECHAZADO", masked.result());
            assertEquals(LocalDateTime.of(2025, 1, 1, 8, 0), masked.occurredAt());
            assertNull(masked.accessCodeId());
            assertNull(masked.code());
            try (var connection = fixture.openFixtureConnection();
                 var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT codigo_acceso_id FROM intento_acceso WHERE intento_acceso_id = 901")) {
                assertTrue(result.next());
                assertEquals(801, result.getInt(1), "La migración no debe reescribir la auditoría histórica.");
            }

            assertInvalid(fixture, """
                    INSERT INTO intento_acceso (gimnasio_id, codigo_acceso_id, resultado, motivo)
                    VALUES (%d, 801, 'RECHAZADO', 'Código ajeno')
                    """.formatted(gymId));
            int ownAttempt = attempts.save(701, true, "Código propio");
            assertInvalid(fixture, "UPDATE intento_acceso SET gimnasio_id = 73 WHERE intento_acceso_id = " + ownAttempt);
            assertInvalid(fixture, "UPDATE intento_acceso SET codigo_acceso_id = 801 WHERE intento_acceso_id = " + ownAttempt);
            assertInvalid(fixture, "UPDATE codigo_acceso SET membresia_id = 601 WHERE codigo_acceso_id = 701");
            assertInvalid(fixture, "UPDATE membresia SET gimnasio_id = 73, miembro_id = 402, plan_id = 201 WHERE membresia_id = 501");

            var service = new AccessValidationService(new AccessCodeRepository(gym), attempts);
            assertFalse(service.validate("GYM-AUDIT801").isAuthorized());
            assertFalse(service.validate("GYM-DESCONOCIDO").isAuthorized());
            assertFalse(service.validate(" ").isAuthorized());
            var history = attempts.findAll();
            assertEquals(5, history.size());
            assertEquals(4, history.stream().filter(attempt -> attempt.accessCodeId() == null && attempt.code() == null).count());
            assertTrue(history.stream().noneMatch(attempt -> "GYM-AUDIT801".equals(attempt.code())));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void neverReturnsAuthorizationIfCommitFails(boolean migrated) throws Exception {
        try (var fixture = new PostgresPlanFixture(migrated, null, SEED)) {
            fixture.execute("""
                    CREATE FUNCTION fallar_confirmacion_intento() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        RAISE EXCEPTION 'Fallo de confirmación inyectado';
                    END;
                    $$;
                    CREATE CONSTRAINT TRIGGER prueba_fallo_confirmacion
                    AFTER INSERT ON intento_acceso DEFERRABLE INITIALLY DEFERRED
                    FOR EACH ROW EXECUTE FUNCTION fallar_confirmacion_intento();
                    """);
            var gym = new GymContext();
            var attempts = new AccessAttemptRepository(gym);
            var service = new AccessValidationService(new AccessCodeRepository(gym), attempts);
            assertThrows(SQLException.class, () -> service.validate(OWN_CODE));
            assertTrue(attempts.findAll().isEmpty());
        }
    }

    private static void assertInvalid(PostgresPlanFixture fixture, String sql) {
        assertEquals("23514", assertThrows(SQLException.class, () -> fixture.execute(sql)).getSQLState());
    }

    private static int backendPid(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT pg_backend_pid()");
             var result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static void awaitBlockedBy(Connection observer, int blockerPid) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        try (var statement = observer.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_stat_activity WHERE ? = ANY (pg_blocking_pids(pid))
                )
                """)) {
            statement.setInt(1, blockerPid);
            do {
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    if (result.getBoolean(1)) return;
                }
                Thread.sleep(20);
            } while (System.nanoTime() < deadline);
        }
        fail("No se observó el bloqueo esperado en PostgreSQL.");
    }
}
