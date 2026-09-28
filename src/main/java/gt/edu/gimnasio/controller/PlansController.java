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
import gt.edu.gimnasio.service.GymContext;
import gt.edu.gimnasio.service.PlanService;
import gt.edu.gimnasio.service.PlanValidationException;

/** Controla la consulta y el registro de planes desde la vista FXML. */
public class PlansController {

    private final PlanRepository planRepository;
    private final PlanService planService;
    private Plan selectedPlan;
    private boolean plansLoaded;

    public PlansController() {
        this(new GymContext());
    }

    public PlansController(GymContext gymContext) {
        planRepository = new PlanRepository(gymContext);
        planService = new PlanService(planRepository);
    }

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
    private Button saveButton;

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
            if (loadPlans()) {
                showFeedback("Plan \"" + plan.getName() + "\" registrado correctamente.", true);
            } else {
                showFeedback("Plan \"" + plan.getName() + "\" registrado, pero no fue posible actualizar la lista. "
                        + "Vuelve a abrir Planes para consultar los datos.", false);
            }
        } catch (PlanValidationException exception) {
            showFeedback(exception.getMessage(), false);
        } catch (IllegalStateException exception) {
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

    private boolean loadPlans() {
        plansLoaded = false;
        updateSelectionControls(null);

        try {
            List<Plan> plans = planRepository.findAll();
            plansLoaded = true;
            plansTable.setItems(FXCollections.observableArrayList(plans));
            updateSelectionControls(selectedPlan);
            showFeedback("", true);
            return true;
        } catch (IllegalStateException exception) {
            plansTable.setItems(FXCollections.observableArrayList());
            showFeedback(exception.getMessage(), false);
        } catch (SQLException exception) {
            plansTable.setItems(FXCollections.observableArrayList());
            showFeedback("No fue posible consultar el gimnasio actual o sus planes. "
                    + "Verifica la conexión y la migración multitenant en PostgreSQL.", false);
        }

        return false;
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
        saveButton.setDisable(!plansLoaded);
        boolean hasSelection = plansLoaded && plan != null;
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
