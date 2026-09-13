package gt.edu.gimnasio.controller;

import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

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
        Label message = createLabel(
                "Selecciona un módulo en el menú para comenzar.", "welcome-message");

        VBox card = createCard("Estado del proyecto", "La base de la aplicación y la conexión a PostgreSQL están configuradas.");
        contentArea.getChildren().setAll(title, description, message, card);
    }

    @FXML
    private void showMembers() {
        showSection("Miembros", "Administra la información de las personas inscritas en el gimnasio.", membersButton);
    }

    @FXML
    private void showPlans() {
        showSection("Planes", "Consulta y administra los planes de membresía disponibles.", plansButton);
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
}
