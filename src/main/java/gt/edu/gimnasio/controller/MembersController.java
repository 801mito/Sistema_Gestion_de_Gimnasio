package gt.edu.gimnasio.controller;

import java.sql.SQLException;
import java.util.List;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import gt.edu.gimnasio.model.Member;
import gt.edu.gimnasio.repository.MemberRepository;

/** Controla la consulta de miembros desde la vista FXML. */
public class MembersController {

    private final MemberRepository memberRepository = new MemberRepository();

    @FXML
    private TableView<Member> membersTable;

    @FXML
    private TableColumn<Member, Number> idColumn;

    @FXML
    private TableColumn<Member, String> firstNamesColumn;

    @FXML
    private TableColumn<Member, String> lastNamesColumn;

    @FXML
    private TableColumn<Member, String> documentColumn;

    @FXML
    private TableColumn<Member, String> phoneColumn;

    @FXML
    private TableColumn<Member, String> emailColumn;

    @FXML
    private Label feedbackLabel;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().getId()));
        firstNamesColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getFirstNames()));
        lastNamesColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getLastNames()));
        documentColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(valueOrEmpty(cell.getValue().getDocumentNumber())));
        phoneColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(valueOrEmpty(cell.getValue().getPhone())));
        emailColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(valueOrEmpty(cell.getValue().getEmail())));

        membersTable.setPlaceholder(new Label("No hay miembros registrados todavía."));
        loadMembers();
    }

    private void loadMembers() {
        try {
            List<Member> members = memberRepository.findAll();
            membersTable.setItems(FXCollections.observableArrayList(members));
            showFeedback("");
        } catch (SQLException | IllegalStateException exception) {
            membersTable.setItems(FXCollections.observableArrayList());
            showFeedback("No fue posible cargar los miembros. Configura la conexión a PostgreSQL.");
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
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
