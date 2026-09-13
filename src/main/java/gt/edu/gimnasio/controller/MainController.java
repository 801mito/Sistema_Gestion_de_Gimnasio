package gt.edu.gimnasio.controller;

import java.util.List;

import javafx.fxml.FXML;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import gt.edu.gimnasio.model.Plan;
import gt.edu.gimnasio.repository.PlanRepository;

/** Controla la navegación de los módulos principales de la aplicación. */
public class MainController {

    @FXML
    private VBox contentArea;

    @FXML
    private Button homeButton;

    @FXML
    private Button membersButton;

    @FXML
    private Button plansButton;

    @FXML
    private Button membershipsButton;

    @FXML
    private Button accessButton;

    @FXML
    private void initialize() {
        showHome();
    }

    @FXML
    private void showHome() {
        setActiveButton(homeButton);

        Label title = createLabel("Inicio", "content-title");
        Label description = createLabel(
                "Bienvenido al Sistema de Gestión de Gimnasio.", "content-description");
        VBox card = createCard("Estado del proyecto", "La base de la aplicación y la conexión a PostgreSQL están configuradas.");
        contentArea.getChildren().setAll(title, description, card);
    }

    @FXML
    private void showMembers() {
        showSection("Miembros", "Administra la información de las personas inscritas en el gimnasio.", membersButton);
    }

    @FXML
    private void showPlans() {
        setActiveButton(plansButton);

        Label title = createLabel("Planes", "content-title");
        Label description = createLabel(
                "Consulta los planes de membresía registrados en la base de datos.", "content-description");

        try {
            List<Plan> plans = new PlanRepository().findAll();
            TableView<Plan> plansTable = createPlansTable();
            plansTable.getItems().setAll(plans);

            if (plans.isEmpty()) {
                plansTable.setPlaceholder(createLabel("No hay planes registrados todavía.", "empty-table-message"));
            }

            contentArea.getChildren().setAll(title, description, plansTable);
        } catch (Exception exception) {
            Label error = createLabel(
                    "No fue posible cargar los planes. Verifica la conexión a PostgreSQL y las variables de entorno.",
                    "database-error");
            contentArea.getChildren().setAll(title, description, error);
        }
    }

    @FXML
    private void showMemberships() {
        showSection("Membresías", "Gestiona las membresías activas, congeladas y vencidas.", membershipsButton);
    }

    @FXML
    private void showAccess() {
        showSection("Accesos", "Registra y valida los intentos de acceso al gimnasio.", accessButton);
    }

    private void showSection(String titleText, String descriptionText, Button selectedButton) {
        setActiveButton(selectedButton);

        Label title = createLabel(titleText, "content-title");
        Label description = createLabel(descriptionText, "content-description");
        VBox card = createCard("Módulo en preparación", "La funcionalidad de este módulo se incorporará en las siguientes issues.");

        contentArea.getChildren().setAll(title, description, card);
    }

    private void setActiveButton(Button selectedButton) {
        List<Button> navigationButtons = List.of(
                homeButton, membersButton, plansButton, membershipsButton, accessButton);

        navigationButtons.forEach(button -> button.getStyleClass().remove("navigation-button-active"));
        selectedButton.getStyleClass().add("navigation-button-active");
    }

    private Label createLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private VBox createCard(String heading, String message) {
        Label headingLabel = createLabel(heading, "card-title");
        Label messageLabel = createLabel(message, "card-message");
        VBox card = new VBox(8, headingLabel, messageLabel);
        card.getStyleClass().add("information-card");
        return card;
    }

    private TableView<Plan> createPlansTable() {
        TableView<Plan> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPrefHeight(360);
        table.getStyleClass().add("plans-table");

        TableColumn<Plan, Number> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().getId()));

        TableColumn<Plan, String> nameColumn = new TableColumn<>("Nombre");
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));

        TableColumn<Plan, Number> durationColumn = new TableColumn<>("Duración (días)");
        durationColumn.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().getDurationDays()));

        TableColumn<Plan, String> statusColumn = new TableColumn<>("Estado");
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().isActive() ? "Activo" : "Inactivo"));

        table.getColumns().add(idColumn);
        table.getColumns().add(nameColumn);
        table.getColumns().add(durationColumn);
        table.getColumns().add(statusColumn);
        return table;
    }
}
