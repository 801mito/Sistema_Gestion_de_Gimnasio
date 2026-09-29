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
import gt.edu.gimnasio.model.Member;
import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.service.GymContext;
import gt.edu.gimnasio.service.PlanService;
import gt.edu.gimnasio.controller.MembersController;
import gt.edu.gimnasio.controller.MembershipsController;
import gt.edu.gimnasio.controller.MainController;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;

/** Carga el FXML y pulsa los botones en el hilo JavaFX, sin mostrar ventanas. */
class ScopedViewsIT {

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

    @Test
    @SuppressWarnings("unchecked")
    void memberTableAndMembershipMemberOptionsUseInjectedGymContext() throws Exception {
        seedMembersForRead();
        fixture.execute("""
                INSERT INTO plan (gimnasio_id, nombre, duracion_dias)
                VALUES (41, 'Plan principal', 30), (73, 'Plan secundario', 7)
                """);
        GymContext otherGym = new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String ignored) throws SQLException {
                return super.findByName("Gimnasio Secundario");
            }
        });
        onFxThread(() -> {
            FXMLLoader membersLoader = new FXMLLoader(getClass().getResource("/gt/edu/gimnasio/view/members-view.fxml"));
            membersLoader.setControllerFactory(type -> new MembersController(otherGym));
            membersLoader.load();
            TableView<Member> table = (TableView<Member>) membersLoader.getNamespace().get("membersTable");
            assertEquals(java.util.List.of("Ajeno"), table.getItems().stream().map(Member::getFirstNames).toList());
            assertFalse(((Button) membersLoader.getNamespace().get("saveButton")).isDisabled());
            assertTrue(((Button) membersLoader.getNamespace().get("updateButton")).isDisabled());
            table.getSelectionModel().selectFirst();
            assertFalse(((Button) membersLoader.getNamespace().get("updateButton")).isDisabled());

            FXMLLoader membershipsLoader = new FXMLLoader(getClass().getResource("/gt/edu/gimnasio/view/memberships-view.fxml"));
            membershipsLoader.setControllerFactory(type -> new MembershipsController(otherGym));
            membershipsLoader.load();
            ComboBox<Member> members = (ComboBox<Member>) membershipsLoader.getNamespace().get("memberComboBox");
            ComboBox<Plan> plans = (ComboBox<Plan>) membershipsLoader.getNamespace().get("planComboBox");
            assertEquals(java.util.List.of("Ajeno"), members.getItems().stream().map(Member::getFirstNames).toList());
            assertEquals(java.util.List.of("Plan secundario"), plans.getItems().stream().map(Plan::getName).toList());
        });
        fixture.assertResourcesClosed();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void memberViewBlocksActionsWhenGymIsMissingOrInactive(boolean inactive) throws Exception {
        fixture.execute(inactive
                ? "UPDATE gimnasio SET gimnasio_activo = false WHERE gimnasio_id = 41"
                : "DELETE FROM gimnasio WHERE gimnasio_id = 41");
        onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gt/edu/gimnasio/view/members-view.fxml"));
            loader.load();
            assertTrue(((TableView<Member>) loader.getNamespace().get("membersTable")).getItems().isEmpty());
            assertTrue(((Button) loader.getNamespace().get("saveButton")).isDisabled());
            assertTrue(((Button) loader.getNamespace().get("updateButton")).isDisabled());
            assertTrue(((Label) loader.getNamespace().get("feedbackLabel")).getText()
                    .contains(inactive ? "inactivo" : "No se encontró"));
        });
        fixture.assertResourcesClosed();
    }

    @Test
    void memberViewReportsSqlErrorAndDisablesActions() throws Exception {
        fixture.execute("ALTER TABLE miembro RENAME COLUMN nombres TO nombres_temporales");
        onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gt/edu/gimnasio/view/members-view.fxml"));
            loader.load();
            assertTrue(((Button) loader.getNamespace().get("saveButton")).isDisabled());
            assertTrue(((Button) loader.getNamespace().get("updateButton")).isDisabled());
            assertTrue(((Label) loader.getNamespace().get("feedbackLabel")).getText()
                    .contains("gimnasio actual o sus miembros"));
        });
        fixture.assertResourcesClosed();
    }

    @Test
    void navigationSharesItsExistingContextWithMembersAndMembershipOptions() throws Exception {
        seedMembersForRead();
        onFxThread(() -> {
            FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/gt/edu/gimnasio/view/main-view.fxml"));
            mainLoader.load();
            MainController main = mainLoader.getController();
            GymContext shared = (GymContext) readField(main, "gymContext");
            VBox content = (VBox) mainLoader.getNamespace().get("contentArea");
            ((Button) mainLoader.getNamespace().get("membersButton")).fire();
            VBox membersView = (VBox) content.getChildren().getFirst();
            TableView<?> membersTable = (TableView<?>) membersView.lookup("#membersTable");
            assertNotNull(membersTable);
            assertEquals(1, membersTable.getItems().size());
            assertEquals(41, shared.getCurrentGymId());
            // Una segunda carga debe reutilizar el contexto ya resuelto por la navegación.
            fixture.execute("UPDATE gimnasio SET nombre = 'Principal temporal' WHERE gimnasio_id = 41");
            ((Button) mainLoader.getNamespace().get("membersButton")).fire();
            membersView = (VBox) content.getChildren().getFirst();
            assertFalse(((Button) membersView.lookup("#saveButton")).isDisabled());
            ((Button) mainLoader.getNamespace().get("membershipsButton")).fire();
            VBox membershipsView = (VBox) content.getChildren().getFirst();
            assertEquals(1, ((ComboBox<?>) membershipsView.lookup("#memberComboBox")).getItems().size());
        });
        fixture.assertResourcesClosed();
    }

    private void seedMembersForRead() throws SQLException {
        fixture.execute("""
                INSERT INTO miembro (gimnasio_id, nombres, apellidos, numero_documento)
                VALUES (41, 'Jaime David', 'Cardona Marmol', 'DOC-100'),
                       (73, 'Ajeno', 'Otro gimnasio', 'DOC-100')
                """);
    }

    @Test
    @SuppressWarnings("unchecked")
    void existingMemberWritesStillWorkAndFailedRefreshDoesNotHideTheWarning() throws Exception {
        fixture.close();
        fixture = null;
        // Comprueba el flujo previo sobre una instalación migrada a 1.1.
        fixture = new PostgresPlanFixture(true);
        onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gt/edu/gimnasio/view/members-view.fxml"));
            loader.load();
            var controls = loader.getNamespace();
            ((TextField) controls.get("firstNamesField")).setText("Jaime David");
            ((TextField) controls.get("lastNamesField")).setText("Cardona Marmol");
            ((Button) controls.get("saveButton")).fire();
            assertTrue(((Label) controls.get("feedbackLabel")).getText().contains("registrado correctamente"));
            TableView<Member> table = (TableView<Member>) controls.get("membersTable");
            assertEquals(1, table.getItems().size());

            table.getSelectionModel().selectFirst();
            ((TextField) controls.get("lastNamesField")).setText("Cardona Marmol actualizado");
            fixture.rejectMemberReads(true);
            ((Button) controls.get("updateButton")).fire();
            assertTrue(((Label) controls.get("feedbackLabel")).getText().contains("actualizado, pero"));
            assertTrue(((Button) controls.get("saveButton")).isDisabled());
            assertTrue(((Button) controls.get("updateButton")).isDisabled());
            fixture.rejectMemberReads(false);

            loader = new FXMLLoader(getClass().getResource("/gt/edu/gimnasio/view/members-view.fxml"));
            loader.load();
            controls = loader.getNamespace();
            table = (TableView<Member>) controls.get("membersTable");
            assertEquals("Cardona Marmol actualizado", table.getItems().getFirst().getLastNames());
            ((TextField) controls.get("firstNamesField")).setText("Ana");
            ((TextField) controls.get("lastNamesField")).setText("Prueba");
            fixture.rejectMemberReads(true);
            ((Button) controls.get("saveButton")).fire();
            assertTrue(((Label) controls.get("feedbackLabel")).getText().contains("registrado, pero"));
            assertTrue(((Button) controls.get("saveButton")).isDisabled());
            fixture.rejectMemberReads(false);
        });
        assertEquals(2, new MemberRepository().findAll().size());
        fixture.assertResourcesClosed();
    }

    @Test
    @SuppressWarnings("unchecked")
    void memberRegistrationUsesInjectedGymAndRejectsOnlyItsDuplicateDocuments() throws Exception {
        fixture.execute("""
                INSERT INTO miembro (gimnasio_id, nombres, apellidos, numero_documento)
                VALUES (41, 'Jaime David', 'Cardona Marmol', 'DOC-200'),
                       (73, 'Ana', 'Secundario', 'DOC-300')
                """);
        GymContext otherGym = new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String ignored) throws SQLException {
                return super.findByName("Gimnasio Secundario");
            }
        });
        onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gt/edu/gimnasio/view/members-view.fxml"));
            loader.setControllerFactory(type -> new MembersController(otherGym));
            loader.load();
            var controls = loader.getNamespace();
            TextField firstNames = (TextField) controls.get("firstNamesField");
            TextField lastNames = (TextField) controls.get("lastNamesField");
            TextField document = (TextField) controls.get("documentField");
            Button save = (Button) controls.get("saveButton");
            Label feedback = (Label) controls.get("feedbackLabel");
            TableView<Member> table = (TableView<Member>) controls.get("membersTable");

            firstNames.setText("Jaime David");
            lastNames.setText("Cardona Marmol");
            document.setText(" DOC-200 ");
            save.fire();
            assertTrue(feedback.getText().contains("registrado correctamente"));
            assertEquals(2, table.getItems().size());
            assertTrue(firstNames.getText().isEmpty());

            firstNames.setText("Duplicado");
            lastNames.setText("Secundario");
            document.setText("DOC-200");
            save.fire();
            assertTrue(feedback.getText().contains("gimnasio actual"));
            assertEquals(2, table.getItems().size());
            assertEquals("DOC-200", document.getText());
        });
        assertEquals(1, new MemberRepository().findAll().size());
        assertEquals(2, new MemberRepository(otherGym).findAll().size());
        fixture.assertResourcesClosed();
    }

    private Object readField(Object instance, String name) throws ReflectiveOperationException {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
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
