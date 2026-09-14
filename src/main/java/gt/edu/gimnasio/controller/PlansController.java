package gt.edu.gimnasio.controller;

import java.sql.SQLException;
import java.util.List;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import gt.edu.gimnasio.model.Plan;
import gt.edu.gimnasio.repository.PlanRepository;
import gt.edu.gimnasio.service.PlanService;
import gt.edu.gimnasio.service.PlanValidationException;

/** Controla la consulta y el registro de planes desde la vista FXML. */
public class PlansController {

    private final PlanRepository planRepository = new PlanRepository();
    private final PlanService planService = new PlanService(planRepository);
    private Plan selectedPlan;

    @FXML
    private TextField nameField;

    @FXML
    private Spinner<Integer> durationSpinner;

    @FXML
    private TableView<Plan> plansTable;

    @FXML
    private TableColumn<Plan, Number> idColumn;

    @FXML
    private TableColumn<Plan, String> nameColumn;

    @FXML
    private TableColumn<Plan, Number> durationColumn;

    @FXML
    private TableColumn<Plan, String> statusColumn;

    @FXML
    private Label feedbackLabel;

    @FXML
    private Button updateButton;

    @FXML
    private Button activateButton;

    @FXML
    private Button deactivateButton;

    @FXML
    private void initialize() {
        durationSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 3650, 30));

        idColumn.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().getId()));
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        durationColumn.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().getDurationDays()));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().isActive() ? "Activo" : "Inactivo"));

        plansTable.setPlaceholder(new Label("No hay planes registrados todavía."));
        plansTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previousPlan, currentPlan) -> selectPlan(currentPlan));
        updateSelectionControls(null);
        loadPlans();
    }

    @FXML
    private void savePlan() {
        try {
            Plan plan = planService.createPlan(nameField.getText(), durationSpinner.getValue());
            nameField.clear();
            durationSpinner.getValueFactory().setValue(30);
            loadPlans();
            showFeedback("Plan \"" + plan.getName() + "\" registrado correctamente.", true);
        } catch (PlanValidationException exception) {
            showFeedback(exception.getMessage(), false);
        } catch (SQLException exception) {
            showFeedback("No fue posible registrar el plan. Verifica la conexión a PostgreSQL.", false);
        }
    }

    @FXML
    private void updatePlan() {
        if (selectedPlan == null) {
            showFeedback("Selecciona un plan para actualizarlo.", false);
            return;
        }

        try {
            Plan updatedPlan = planService.updatePlan(
                    selectedPlan.getId(), nameField.getText(), durationSpinner.getValue());
            loadPlans();
            clearForm();
            showFeedback("Plan \"" + updatedPlan.getName() + "\" actualizado correctamente.", true);
        } catch (PlanValidationException exception) {
            showFeedback(exception.getMessage(), false);
        } catch (SQLException exception) {
            showFeedback("No fue posible actualizar el plan. Verifica la conexión a PostgreSQL.", false);
        }
    }

    @FXML
    private void activatePlan() {
        changeActiveStatus(true);
    }

    @FXML
    private void deactivatePlan() {
        changeActiveStatus(false);
    }

    @FXML
    private void clearForm() {
        plansTable.getSelectionModel().clearSelection();
        nameField.clear();
        durationSpinner.getValueFactory().setValue(30);
        selectedPlan = null;
        updateSelectionControls(null);
    }

    private void loadPlans() {
        try {
            List<Plan> plans = planRepository.findAll();
            plansTable.setItems(FXCollections.observableArrayList(plans));
            showFeedback("", true);
        } catch (SQLException | IllegalStateException exception) {
            plansTable.setItems(FXCollections.observableArrayList());
            showFeedback("No fue posible cargar los planes. Configura la conexión a PostgreSQL.", false);
        }
    }

    private void selectPlan(Plan plan) {
        selectedPlan = plan;

        if (plan != null) {
            nameField.setText(plan.getName());
            durationSpinner.getValueFactory().setValue(plan.getDurationDays());
        }

        updateSelectionControls(plan);
    }

    private void changeActiveStatus(boolean active) {
        if (selectedPlan == null) {
            showFeedback("Selecciona un plan para cambiar su estado.", false);
            return;
        }

        try {
            planService.changeActiveStatus(selectedPlan.getId(), active);
            String action = active ? "activado" : "desactivado";
            String planName = selectedPlan.getName();
            loadPlans();
            clearForm();
            showFeedback("Plan \"" + planName + "\" " + action + " correctamente.", true);
        } catch (SQLException exception) {
            showFeedback("No fue posible cambiar el estado del plan.", false);
        }
    }

    private void updateSelectionControls(Plan plan) {
        boolean hasSelection = plan != null;
        updateButton.setDisable(!hasSelection);
        activateButton.setDisable(!hasSelection || plan.isActive());
        deactivateButton.setDisable(!hasSelection || !plan.isActive());
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.setVisible(!message.isBlank());
        feedbackLabel.setManaged(!message.isBlank());
        feedbackLabel.getStyleClass().removeAll("feedback-success", "feedback-error");

        if (!message.isBlank()) {
            feedbackLabel.getStyleClass().add(success ? "feedback-success" : "feedback-error");
        }
    }
}
