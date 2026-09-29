package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.sql.SQLException;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import gt.edu.gimnasio.model.Plan;
import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.service.GymContext;
import gt.edu.gimnasio.service.PlanService;

/** Carga el FXML y pulsa los botones en el hilo JavaFX, sin mostrar ventanas. */
class PlansViewIT {

    private PostgresPlanFixture fixture;
    private PlanRepository repository;
    private PlanService service;

    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.countDown();
        });
        assertTrue(started.await(10, TimeUnit.SECONDS), "JavaFX no inició a tiempo.");
    }

    @AfterAll
    static void stopJavaFx() {
        Platform.exit();
    }

    @BeforeEach
    void preparePostgres() throws Exception {
        fixture = new PostgresPlanFixture(false);
        repository = new PlanRepository();
        service = new PlanService(repository);
    }

    @AfterEach
    void removeTestSchema() throws Exception {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void buttonsSaveEditToggleAndReloadOnlyOwnPlans() throws Exception {
        PlanRepository otherGym = secondaryRepository();
        otherGym.save("Mensual", 15);
        onFxThread(() -> {
            View view = loadView();
            assertTrue(view.table().getItems().isEmpty());
            assertFalse(view.save().isDisabled());
            view.name().setText("Mensual");
            view.save().fire();
            assertTrue(view.feedback().getText().contains("registrado correctamente"));
            assertEquals(1, view.table().getItems().size());

            view.table().getSelectionModel().selectFirst();
            view.name().setText("Quincenal");
            view.duration().getValueFactory().setValue(14);
            view.update().fire();
            assertTrue(view.feedback().getText().contains("actualizado correctamente"));
            assertEquals("Quincenal", view.table().getItems().getFirst().getName());
            assertEquals(14, view.table().getItems().getFirst().getDurationDays());

            view.table().getSelectionModel().selectFirst();
            view.deactivate().fire();
            assertFalse(view.table().getItems().getFirst().isActive());
            view.table().getSelectionModel().selectFirst();
            assertFalse(view.activate().isDisabled());
            assertTrue(view.deactivate().isDisabled());
            view.activate().fire();
            assertTrue(view.table().getItems().getFirst().isActive());
            assertEquals("Quincenal", loadView().table().getItems().getFirst().getName());
        });
        assertEquals(1, repository.findActive().size());
        Plan foreign = otherGym.findAll().getFirst();
        assertEquals("Mensual", foreign.getName());
        assertEquals(15, foreign.getDurationDays());
        assertTrue(foreign.isActive());
        fixture.assertResourcesClosed();
    }

    @Test
    void invalidAndDuplicateNamesShowErrorsWithoutChangingRows() throws Exception {
        service.createPlan("Mensual", 30);
        Plan weekly = service.createPlan("Semanal", 7);
        onFxThread(() -> {
            View view = loadView();
            view.save().fire();
            assertTrue(view.feedback().getText().contains("obligatorio"));
            view.name().setText(" MENSUAL ");
            view.save().fire();
            assertTrue(view.feedback().getText().contains("gimnasio actual"));
            view.table().getSelectionModel().select(view.table().getItems().stream()
                    .filter(plan -> plan.getId() == weekly.getId()).findFirst().orElseThrow());
            view.name().setText("mensual");
            view.update().fire();
            assertTrue(view.feedback().getText().contains("otro plan"));
            assertEquals(2, view.table().getItems().size());
        });
        assertEquals("Semanal", repository.findAll().stream()
                .filter(plan -> plan.getId() == weekly.getId()).findFirst().orElseThrow().getName());
        fixture.assertResourcesClosed();
    }

    @Test
    void staleForeignSelectionIsRejectedAndRemovedFromTable() throws Exception {
        PlanRepository otherGym = secondaryRepository();
        Plan foreign = otherGym.save("Ajeno", 15);
        onFxThread(() -> {
            View view = loadView();
            for (String action : new String[] {"edit", "deactivate", "activate"}) {
                Plan stale = new Plan(foreign.getId(), foreign.getName(), 15,
                        !action.equals("activate"), foreign.getCreatedAt());
                view.table().getItems().add(stale);
                view.table().getSelectionModel().select(stale);
                if (action.equals("edit")) {
                    view.name().setText("Intento ajeno");
                    view.update().fire();
                } else if (action.equals("deactivate")) {
                    view.deactivate().fire();
                } else {
                    view.activate().fire();
                }
                assertEquals("No se encontró el plan en el gimnasio actual.", view.feedback().getText());
                assertTrue(view.table().getItems().isEmpty());
                assertTrue(view.update().isDisabled());
            }
        });
        Plan unchanged = otherGym.findAll().getFirst();
        assertEquals("Ajeno", unchanged.getName());
        assertEquals(15, unchanged.getDurationDays());
        assertTrue(unchanged.isActive());
        fixture.assertResourcesClosed();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrInactiveGymDisablesSaving(boolean inactive) throws Exception {
        fixture.execute(inactive
                ? "UPDATE gimnasio SET gimnasio_activo = false WHERE gimnasio_id = 41"
                : "DELETE FROM gimnasio WHERE gimnasio_id = 41");
        onFxThread(() -> {
            View view = loadView();
            assertTrue(view.save().isDisabled());
            assertTrue(view.update().isDisabled());
            assertTrue(view.table().getItems().isEmpty());
            assertTrue(view.feedback().getText().contains(inactive ? "inactivo" : "No se encontró"));
        });
        fixture.assertResourcesClosed();
    }

    @Test
    void completedWritesWithFailedReloadShowWarningAndDisableButtons() throws Exception {
        onFxThread(() -> {
            View view = loadView();
            view.name().setText("Mensual");
            fixture.rejectPlanReads(true);
            view.save().fire();
            assertTrue(view.feedback().getText().contains("registrado, pero"));
            assertTrue(view.save().isDisabled());
            fixture.rejectPlanReads(false);

            view = loadView();
            assertEquals(1, view.table().getItems().size());
            view.table().getSelectionModel().selectFirst();
            view.name().setText("Mensual editado");
            fixture.rejectPlanReads(true);
            view.update().fire();
            assertTrue(view.feedback().getText().contains("actualizado, pero"));
            assertTrue(view.update().isDisabled());
            fixture.rejectPlanReads(false);

            view = loadView();
            assertEquals("Mensual editado", view.table().getItems().getFirst().getName());
            view.table().getSelectionModel().selectFirst();
            fixture.rejectPlanReads(true);
            view.deactivate().fire();
            assertTrue(view.feedback().getText().contains("desactivado, pero"));
            assertTrue(view.save().isDisabled());
            assertTrue(view.deactivate().isDisabled());
            fixture.rejectPlanReads(false);
            assertFalse(loadView().table().getItems().getFirst().isActive());
        });
        assertTrue(repository.findActive().isEmpty());
        fixture.assertResourcesClosed();
    }

    @SuppressWarnings("unchecked")
    private View loadView() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gt/edu/gimnasio/view/plans-view.fxml"));
        loader.load();
        var controls = loader.getNamespace();
        return new View((TextField) controls.get("nameField"), (Spinner<Integer>) controls.get("durationSpinner"),
                (TableView<Plan>) controls.get("plansTable"), (Label) controls.get("feedbackLabel"),
                (Button) controls.get("saveButton"), (Button) controls.get("updateButton"),
                (Button) controls.get("activateButton"), (Button) controls.get("deactivateButton"));
    }

    private PlanRepository secondaryRepository() {
        return new PlanRepository(new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String ignored) throws SQLException {
                return super.findByName("Gimnasio Secundario");
            }
        }));
    }

    private static void onFxThread(CheckedAction action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface CheckedAction { void run() throws Exception; }

    private record View(TextField name, Spinner<Integer> duration, TableView<Plan> table, Label feedback,
                        Button save, Button update, Button activate, Button deactivate) { }
}
