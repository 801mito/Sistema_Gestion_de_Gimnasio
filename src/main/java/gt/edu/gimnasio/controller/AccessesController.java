package gt.edu.gimnasio.controller;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import gt.edu.gimnasio.model.AccessCode;
import gt.edu.gimnasio.model.AccessValidationResult;
import gt.edu.gimnasio.repository.AccessCodeRepository;
import gt.edu.gimnasio.service.AccessValidationService;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/** Controla la consulta de códigos de acceso generados para membresías. */
public class AccessesController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AccessCodeRepository accessCodeRepository = new AccessCodeRepository();
    private final AccessValidationService accessValidationService =
            new AccessValidationService(accessCodeRepository);

    @FXML private TextField accessCodeField;
    @FXML private Label validationResultLabel;
    @FXML private TableView<AccessCode> accessCodesTable;
    @FXML private TableColumn<AccessCode, Number> idColumn;
    @FXML private TableColumn<AccessCode, String> memberColumn;
    @FXML private TableColumn<AccessCode, String> planColumn;
    @FXML private TableColumn<AccessCode, String> codeColumn;
    @FXML private TableColumn<AccessCode, String> statusColumn;
    @FXML private TableColumn<AccessCode, String> createdAtColumn;
    @FXML private Label feedbackLabel;
    @FXML private Button activateButton;
    @FXML private Button deactivateButton;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().getId()));
        memberColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getMemberName()));
        planColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getPlanName()));
        codeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getCode()));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().isActive() ? "Activo" : "Inactivo"));
        createdAtColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getCreatedAt().format(DATE_TIME_FORMATTER)));
        accessCodesTable.setPlaceholder(new Label("No hay códigos de acceso registrados todavía."));
        accessCodesTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previousSelection, selectedCode) -> updateActionButtons(selectedCode));
        updateActionButtons(null);
        loadAccessCodes();
    }

    @FXML
    private void activateSelectedCode() {
        changeSelectedCodeStatus(true);
    }

    @FXML
    private void deactivateSelectedCode() {
        changeSelectedCodeStatus(false);
    }

    @FXML
    private void validateAccess() {
        try {
            AccessValidationResult result = accessValidationService.validate(accessCodeField.getText());
            showValidationResult(result.getReason(), result.isAuthorized());
        } catch (SQLException | IllegalStateException exception) {
            showValidationResult("No fue posible validar el acceso. Verifica la conexión a PostgreSQL.", false);
        }
    }

    private void changeSelectedCodeStatus(boolean active) {
        AccessCode selectedCode = accessCodesTable.getSelectionModel().getSelectedItem();

        if (selectedCode == null) {
            showFeedback("Selecciona un código de acceso.", false);
            return;
        }

        try {
            accessCodeRepository.updateActiveStatus(selectedCode.getId(), active);

            if (loadAccessCodes()) {
                String status = active ? "activado" : "desactivado";
                showFeedback("Código " + selectedCode.getCode() + " " + status + " correctamente.", true);
            }
        } catch (SQLException | IllegalStateException exception) {
            showFeedback("No fue posible cambiar el estado del código. Verifica la conexión a PostgreSQL.", false);
        }
    }

    private boolean loadAccessCodes() {
        try {
            List<AccessCode> accessCodes = accessCodeRepository.findAll();
            accessCodesTable.setItems(FXCollections.observableArrayList(accessCodes));
            return true;
        } catch (SQLException | IllegalStateException exception) {
            accessCodesTable.setItems(FXCollections.observableArrayList());
            showFeedback("No fue posible cargar los códigos. Configura la conexión a PostgreSQL.", false);
            return false;
        }
    }

    private void updateActionButtons(AccessCode selectedCode) {
        boolean codeSelected = selectedCode != null;
        activateButton.setDisable(!codeSelected || selectedCode.isActive());
        deactivateButton.setDisable(!codeSelected || !selectedCode.isActive());
    }

    private void showValidationResult(String message, boolean authorized) {
        validationResultLabel.setText(message);
        validationResultLabel.setVisible(!message.isBlank());
        validationResultLabel.setManaged(!message.isBlank());
        validationResultLabel.getStyleClass().removeAll("feedback-success", "feedback-error");

        if (!message.isBlank()) {
            validationResultLabel.getStyleClass().add(authorized ? "feedback-success" : "feedback-error");
        }
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
