package gt.edu.gimnasio.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.sql.SQLException;
import java.util.List;
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
import javafx.stage.Stage;
import gt.edu.gimnasio.app.GimnasioApplication;
import gt.edu.gimnasio.model.Plan;
import gt.edu.gimnasio.model.Member;
import gt.edu.gimnasio.model.Membership;
import gt.edu.gimnasio.model.AccessCode;
import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.service.GymContext;
import gt.edu.gimnasio.service.MemberService;
import gt.edu.gimnasio.service.PlanService;
import gt.edu.gimnasio.controller.MembersController;
import gt.edu.gimnasio.controller.MembershipsController;
import gt.edu.gimnasio.controller.AccessesController;
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
    @SuppressWarnings("unchecked")
    void loginLogoutAndSecondGymCannotReadOrChangeFirstGymPlans() throws Exception {
        EmployeeRepository accounts = new EmployeeRepository();
        accounts.save(41, "jaime", "clave-principal-larga".toCharArray());
        accounts.save(73, "ana", "clave-secundaria-larga".toCharArray());
        Plan firstGymPlan = repository.save("Plan de Jaime", 30);
        PlanRepository secondGym = secondaryRepository();
        secondGym.save("Plan de Ana", 7);
        int resourcesBeforeLogin = fixture.resourceCount();

        onFxThread(() -> {
            GimnasioApplication app = new GimnasioApplication();
            Stage stage = new Stage();
            try {
                app.start(stage);
                assertNull(stage.getScene().lookup("#plansButton"));
                assertNotNull(stage.getScene().lookup("#loginButton"));
                assertNull(readField(app, "session"));
                assertEquals(resourcesBeforeLogin, fixture.resourceCount(),
                        "La pantalla inicial no debe consultar datos de ningún gimnasio.");

                ((TextField) stage.getScene().lookup("#usernameField")).setText("jaime");
                ((TextField) stage.getScene().lookup("#passwordField")).setText("clave-principal-larga");
                ((Button) stage.getScene().lookup("#loginButton")).fire();
                assertNotNull(readField(app, "session"));
                assertEquals("jaime · Gimnasio Principal",
                        ((Label) stage.getScene().lookup("#currentSessionLabel")).getText());

                Button oldPlansButton = (Button) stage.getScene().lookup("#plansButton");
                oldPlansButton.fire();
                TableView<Plan> firstTable = (TableView<Plan>) stage.getScene().lookup("#plansTable");
                assertEquals(List.of("Plan de Jaime"),
                        firstTable.getItems().stream().map(Plan::getName).toList());

                ((Button) stage.getScene().lookup("#logoutButton")).fire();
                assertNull(readField(app, "session"));
                assertNull(stage.getScene().lookup("#plansButton"));
                assertNotNull(stage.getScene().lookup("#loginButton"));
                assertTrue(oldPlansButton.isDisabled());
                int resourcesAfterLogout = fixture.resourceCount();
                oldPlansButton.setDisable(false);
                oldPlansButton.fire();
                assertEquals(resourcesAfterLogout, fixture.resourceCount(),
                        "Una vista anterior no debe consultar PostgreSQL después de salir.");

                ((TextField) stage.getScene().lookup("#usernameField")).setText("ana");
                ((TextField) stage.getScene().lookup("#passwordField")).setText("clave-secundaria-larga");
                ((Button) stage.getScene().lookup("#loginButton")).fire();
                assertEquals("ana · Gimnasio Secundario",
                        ((Label) stage.getScene().lookup("#currentSessionLabel")).getText());
                ((Button) stage.getScene().lookup("#plansButton")).fire();
                TableView<Plan> secondTable = (TableView<Plan>) stage.getScene().lookup("#plansTable");
                assertEquals(List.of("Plan de Ana"),
                        secondTable.getItems().stream().map(Plan::getName).toList());

                // Incluso si llega un registro ajeno a la tabla, su cambio queda restringido por gimnasio.
                secondTable.getItems().add(firstGymPlan);
                secondTable.getSelectionModel().select(firstGymPlan);
                ((Button) stage.getScene().lookup("#deactivateButton")).fire();
                assertEquals("No se encontró el plan en el gimnasio actual.",
                        ((Label) stage.getScene().lookup("#feedbackLabel")).getText());
            } finally {
                stage.close();
            }
        });

        assertTrue(repository.findAll().getFirst().isActive());
        fixture.assertResourcesClosed();
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
    void memberTableAndMembershipHistoryAndOptionsUseInjectedGymContext() throws Exception {
        seedMembersForRead();
        fixture.execute("""
                INSERT INTO plan (gimnasio_id, nombre, duracion_dias)
                VALUES (41, 'Plan principal', 30), (73, 'Plan secundario', 7)
                """);
        fixture.execute("""
                INSERT INTO membresia (gimnasio_id, plan_id, miembro_id, fecha_inicio, fecha_fin)
                SELECT plan.gimnasio_id, plan.plan_id, miembro.miembro_id,
                       DATE '2026-09-01', DATE '2026-09-30'
                FROM plan JOIN miembro ON miembro.gimnasio_id = plan.gimnasio_id
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
            TableView<Membership> secondaryHistory =
                    (TableView<Membership>) membershipsLoader.getNamespace().get("membershipsTable");
            assertEquals(java.util.List.of("Ajeno Otro gimnasio"),
                    secondaryHistory.getItems().stream().map(Membership::getMemberName).toList());

            FXMLLoader primaryLoader = new FXMLLoader(getClass().getResource("/gt/edu/gimnasio/view/memberships-view.fxml"));
            primaryLoader.load();
            TableView<Membership> primaryHistory =
                    (TableView<Membership>) primaryLoader.getNamespace().get("membershipsTable");
            assertEquals(java.util.List.of("Jaime David Cardona Marmol"),
                    primaryHistory.getItems().stream().map(Membership::getMemberName).toList());
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
            ((Button) mainLoader.getNamespace().get("accessButton")).fire();
            VBox accessesView = (VBox) content.getChildren().getFirst();
            assertNotNull(accessesView.lookup("#accessCodesTable"));
            assertTrue(((Label) accessesView.lookup("#feedbackLabel")).getText().isEmpty());
        });
        fixture.assertResourcesClosed();
    }

    @Test
    @SuppressWarnings("unchecked")
    void accessViewFiltersCodesAndRejectsForeignSelectionAndValidation() throws Exception {
        fixture.execute("""
                INSERT INTO plan (plan_id, gimnasio_id, nombre, duracion_dias)
                VALUES (101, 41, 'Plan principal', 30), (201, 73, 'Plan secundario', 30);
                INSERT INTO miembro (miembro_id, gimnasio_id, nombres, apellidos)
                VALUES (301, 41, 'Jaime', 'Principal'), (401, 73, 'Ana', 'Secundaria');
                INSERT INTO membresia (membresia_id, gimnasio_id, plan_id, miembro_id,
                                       estado, fecha_inicio, fecha_fin)
                VALUES (501, 41, 101, 301, 'ACTIVA', CURRENT_DATE - 7, CURRENT_DATE + 7),
                       (601, 73, 201, 401, 'ACTIVA', CURRENT_DATE - 7, CURRENT_DATE + 7);
                INSERT INTO codigo_acceso (codigo_acceso_id, membresia_id, codigo)
                VALUES (701, 501, 'GYM-PRIMARY701'), (801, 601, 'GYM-SECONDARY801');
                """);
        GymContext secondary = new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String ignored) throws SQLException {
                return super.findByName("Gimnasio Secundario");
            }
        });

        onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gt/edu/gimnasio/view/accesses-view.fxml"));
            loader.setControllerFactory(type -> new AccessesController(secondary));
            loader.load();
            var controls = loader.getNamespace();
            TableView<AccessCode> table = (TableView<AccessCode>) controls.get("accessCodesTable");
            TextField input = (TextField) controls.get("accessCodeField");
            Label result = (Label) controls.get("validationResultLabel");
            Label feedback = (Label) controls.get("feedbackLabel");
            Button activate = (Button) controls.get("activateButton");
            Button deactivate = (Button) controls.get("deactivateButton");
            Button validate = (Button) ((VBox) loader.getRoot()).lookup(".primary-button");

            assertEquals(List.of(801), table.getItems().stream().map(AccessCode::getId).toList());
            input.setText("GYM-PRIMARY701");
            validate.fire();
            assertEquals("El código ingresado no existe.", result.getText());
            input.setText("GYM-SECONDARY801");
            validate.fire();
            assertTrue(result.getText().contains("Acceso autorizado"));

            table.getSelectionModel().selectFirst();
            deactivate.fire();
            assertFalse(table.getItems().getFirst().isActive());
            table.getSelectionModel().selectFirst();
            activate.fire();
            assertTrue(table.getItems().getFirst().isActive());

            AccessCode foreign = new AccessCode(701, 501, "Jaime Principal", "Plan principal",
                    "GYM-PRIMARY701", true, table.getItems().getFirst().getCreatedAt());
            table.getItems().add(foreign);
            table.getSelectionModel().select(foreign);
            deactivate.fire();
            assertEquals("No se encontró el código de acceso en el gimnasio actual.", feedback.getText());
            assertEquals(List.of(801), table.getItems().stream().map(AccessCode::getId).toList());
        });
        assertTrue(new AccessCodeRepository().findAll().getFirst().isActive());
        assertTrue(new AccessCodeRepository(secondary).findAll().getFirst().isActive());
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
    void membershipViewAssignsOwnMemberAndRejectsInjectedForeignSelection() throws Exception {
        seedMembersForRead();
        fixture.execute("""
                INSERT INTO plan (gimnasio_id, nombre, duracion_dias)
                VALUES (41, 'Mensual', 30), (73, 'Semanal', 7)
                """);
        GymContext secondary = new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String ignored) throws SQLException {
                return super.findByName("Gimnasio Secundario");
            }
        });
        Member foreign = new MemberRepository(secondary).findAll().getFirst();

        onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gt/edu/gimnasio/view/memberships-view.fxml"));
            loader.load();
            ComboBox<Member> members = (ComboBox<Member>) loader.getNamespace().get("memberComboBox");
            ComboBox<Plan> plans = (ComboBox<Plan>) loader.getNamespace().get("planComboBox");
            Button assign = (Button) loader.getNamespace().get("assignButton");
            Label feedback = (Label) loader.getNamespace().get("feedbackLabel");
            TableView<Membership> history = (TableView<Membership>) loader.getNamespace().get("membershipsTable");

            Plan ownPlan = plans.getItems().getFirst();
            members.setValue(members.getItems().getFirst());
            plans.setValue(ownPlan);
            assign.fire();
            assertTrue(feedback.getText().contains("Código generado"));
            assertEquals(1, history.getItems().size());

            members.setValue(foreign);
            plans.setValue(ownPlan);
            assign.fire();
            assertTrue(feedback.getText().contains("miembro en el gimnasio actual"));
            assertEquals(1, history.getItems().size());
        });
        fixture.assertResourcesClosed();
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

    @Test
    void memberEditButtonAcceptsOwnDocumentAndRejectsOnlyAnotherOwnMembersDocument() throws Exception {
        MemberRepository members = new MemberRepository();
        Member own = members.save("Jaime David", "Cardona Marmol", "DOC-100", null, null);
        Member other = members.save("Luis", "Prueba", "DOC-200", null, null);
        MemberRepository otherGym = secondaryMemberRepository();
        Member foreign = otherGym.save("Ana", "Otro gimnasio", "DOC-100", null, null);
        onFxThread(() -> {
            MemberView view = loadMemberView();
            view.table().getSelectionModel().select(view.table().getItems().stream()
                    .filter(member -> member.getId() == own.getId()).findFirst().orElseThrow());
            view.lastNames().setText(" Cardona Marmol editado ");
            view.document().setText(" DOC-100 ");
            view.update().fire();
            assertTrue(view.feedback().getText().contains("actualizado correctamente"));
            assertTrue(view.firstNames().getText().isEmpty());
            assertTrue(view.update().isDisabled());

            view.table().getSelectionModel().select(view.table().getItems().stream()
                    .filter(member -> member.getId() == own.getId()).findFirst().orElseThrow());
            view.document().setText("DOC-200");
            view.update().fire();
            assertTrue(view.feedback().getText().contains("otro miembro"));
            assertTrue(view.feedback().getText().contains("gimnasio actual"));
            assertEquals("DOC-200", view.document().getText());
            assertEquals("DOC-100", view.table().getSelectionModel().getSelectedItem().getDocumentNumber());
            assertEquals(2, view.table().getItems().size());

            view.document().setText(" DOC-300 ");
            view.phone().setText(" 5555-5555 ");
            view.email().setText(" jaime@example.com ");
            view.update().fire();
            assertTrue(view.feedback().getText().contains("actualizado correctamente"));
            assertTrue(view.update().isDisabled());
            assertFalse(view.save().isDisabled());
        });
        Member updated = members.findAll().stream().filter(member -> member.getId() == own.getId()).findFirst().orElseThrow();
        assertEquals("Cardona Marmol editado", updated.getLastNames());
        assertEquals("DOC-300", updated.getDocumentNumber());
        assertEquals("5555-5555", updated.getPhone());
        assertEquals("jaime@example.com", updated.getEmail());
        assertEquals("DOC-200", members.findAll().stream()
                .filter(member -> member.getId() == other.getId()).findFirst().orElseThrow().getDocumentNumber());
        assertEquals(foreign.getFullName(), otherGym.findAll().getFirst().getFullName());
        assertEquals("DOC-100", otherGym.findAll().getFirst().getDocumentNumber());
        fixture.assertResourcesClosed();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void foreignOrMissingMemberSelectionIsRejectedAndClearedEvenIfReloadFails(boolean reloadFails) throws Exception {
        MemberRepository members = new MemberRepository();
        Member own = members.save("Jaime David", "Cardona Marmol", "DOC-200", null, null);
        MemberRepository otherGym = secondaryMemberRepository();
        Member foreign = otherGym.save("Ana", "Otro gimnasio", "DOC-100", null, null);
        onFxThread(() -> {
            for (int id : new int[] {foreign.getId(), Integer.MAX_VALUE}) {
                MemberView view = loadMemberView();
                Member stale = new Member(id, "Selección", "Ajena u obsoleta", null, null, null, foreign.getCreatedAt());
                view.table().getItems().add(stale);
                view.table().getSelectionModel().select(stale);
                assertFalse(view.update().isDisabled());
                view.firstNames().setText("Intento de cambio");
                fixture.rejectMemberReads(reloadFails);
                try {
                    view.update().fire();
                    assertEquals("No se encontró el miembro en el gimnasio actual.", view.feedback().getText());
                    assertTrue(view.firstNames().getText().isEmpty());
                    assertTrue(view.lastNames().getText().isEmpty());
                    assertTrue(view.document().getText().isEmpty());
                    assertNull(view.table().getSelectionModel().getSelectedItem());
                    assertTrue(view.update().isDisabled());
                    assertEquals(reloadFails, view.save().isDisabled());
                    assertEquals(reloadFails ? 0 : 1, view.table().getItems().size());
                } finally {
                    fixture.rejectMemberReads(false);
                }
            }
        });
        assertEquals(own.getFullName(), members.findAll().getFirst().getFullName());
        assertEquals(foreign.getFullName(), otherGym.findAll().getFirst().getFullName());
        assertEquals("DOC-100", otherGym.findAll().getFirst().getDocumentNumber());
        fixture.assertResourcesClosed();
    }

    @Test
    void memberDeletedAfterSelectionShowsUnavailableInsteadOfDatabaseConnectionError() throws Exception {
        Member own = new MemberService(new MemberRepository())
                .createMember("Jaime David", "Cardona Marmol", "DOC-100", null, null);
        onFxThread(() -> {
            MemberView view = loadMemberView();
            view.table().getSelectionModel().selectFirst();
            fixture.execute("DELETE FROM miembro WHERE miembro_id = " + own.getId());
            view.update().fire();
            assertEquals("No se encontró el miembro en el gimnasio actual.", view.feedback().getText());
            assertTrue(view.table().getItems().isEmpty());
            assertTrue(view.update().isDisabled());
            assertFalse(view.save().isDisabled());
            assertTrue(view.document().getText().isEmpty());
        });
        fixture.assertResourcesClosed();
    }

    @Test
    void membersCreatedBeforeMigrationStayVisibleAndCanBeEditedBesideNewRegistrations() throws Exception {
        fixture.close();
        fixture = null;
        fixture = new PostgresPlanFixture(true, PostgresPlanFixture.LEGACY_MEMBER_SETUP_SQL);
        onFxThread(() -> {
            MemberView view = loadMemberView();
            assertEquals(List.of(101, 102), view.table().getItems().stream().map(Member::getId).toList());
            assertFalse(view.save().isDisabled());
            Member original = view.table().getItems().stream()
                    .filter(member -> member.getId() == 101).findFirst().orElseThrow();
            view.table().getSelectionModel().select(original);
            assertFalse(view.update().isDisabled());
            view.lastNames().setText("Cardona Marmol actualizado");
            view.update().fire();
            assertTrue(view.feedback().getText().contains("actualizado correctamente"));
            assertEquals("Cardona Marmol actualizado", view.table().getItems().stream()
                    .filter(member -> member.getId() == 101).findFirst().orElseThrow().getLastNames());

            view.firstNames().setText("Nuevo");
            view.lastNames().setText("Posterior");
            view.document().setText("DOC-AFTER");
            view.save().fire();
            assertTrue(view.feedback().getText().contains("registrado correctamente"));
            assertEquals(3, view.table().getItems().size());
            assertTrue(view.table().getItems().stream().anyMatch(member -> member.getId() == 102));
            assertTrue(view.table().getItems().stream().anyMatch(member -> "DOC-AFTER".equals(member.getDocumentNumber())));
        });
        assertEquals(3, new MemberRepository().findAll().size());
        fixture.assertResourcesClosed();
    }

    @SuppressWarnings("unchecked")
    private MemberView loadMemberView() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gt/edu/gimnasio/view/members-view.fxml"));
        loader.load();
        var controls = loader.getNamespace();
        return new MemberView((TextField) controls.get("firstNamesField"), (TextField) controls.get("lastNamesField"),
                (TextField) controls.get("documentField"), (TextField) controls.get("phoneField"),
                (TextField) controls.get("emailField"), (TableView<Member>) controls.get("membersTable"),
                (Label) controls.get("feedbackLabel"), (Button) controls.get("saveButton"),
                (Button) controls.get("updateButton"));
    }

    private MemberRepository secondaryMemberRepository() {
        return new MemberRepository(new GymContext(new GymRepository() {
            @Override
            public Optional<Gym> findByName(String ignored) throws SQLException {
                return super.findByName("Gimnasio Secundario");
            }
        }));
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

    private record MemberView(TextField firstNames, TextField lastNames, TextField document, TextField phone,
                              TextField email, TableView<Member> table, Label feedback, Button save, Button update) { }
}
