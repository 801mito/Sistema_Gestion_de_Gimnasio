package gt.edu.gimnasio.controller;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import gt.edu.gimnasio.model.Membership;
import gt.edu.gimnasio.repository.MembershipRepository;

/** Controla la consulta del historial de membresías. */
public class MembershipsController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final MembershipRepository membershipRepository = new MembershipRepository();

    @FXML
    private TableView<Membership> membershipsTable;

    @FXML
    private TableColumn<Membership, Number> idColumn;

    @FXML
    private TableColumn<Membership, String> memberColumn;

    @FXML
    private TableColumn<Membership, String> planColumn;

    @FXML
    private TableColumn<Membership, String> statusColumn;

    @FXML
    private TableColumn<Membership, String> startDateColumn;

    @FXML
    private TableColumn<Membership, String> endDateColumn;

    @FXML
    private Label feedbackLabel;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().getId()));
        memberColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getMemberName()));
        planColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getPlanName()));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getStatus()));
        startDateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getStartDate().format(DATE_FORMATTER)));
        endDateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getEndDate().format(DATE_FORMATTER)));

        membershipsTable.setPlaceholder(new Label("No hay membresías registradas todavía."));
        loadMemberships();
    }

    private void loadMemberships() {
        try {
            List<Membership> memberships = membershipRepository.findAll();
            membershipsTable.setItems(FXCollections.observableArrayList(memberships));
            showFeedback("");
        } catch (SQLException | IllegalStateException exception) {
            membershipsTable.setItems(FXCollections.observableArrayList());
            showFeedback("No fue posible cargar las membresías. Configura la conexión a PostgreSQL.");
        }
    }

    private void showFeedback(String message) {
        feedbackLabel.setText(message);
        feedbackLabel.setVisible(!message.isBlank());
        feedbackLabel.setManaged(!message.isBlank());
        feedbackLabel.getStyleClass().remove("feedback-error");

        if (!message.isBlank()) {
            feedbackLabel.getStyleClass().add("feedback-error");
        }
    }
}
