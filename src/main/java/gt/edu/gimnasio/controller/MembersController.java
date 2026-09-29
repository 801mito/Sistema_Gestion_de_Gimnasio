package gt.edu.gimnasio.controller;

import java.sql.SQLException;
import java.util.List;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import gt.edu.gimnasio.model.Member;
import gt.edu.gimnasio.repository.MemberNotFoundException;
import gt.edu.gimnasio.repository.MemberRepository;
import gt.edu.gimnasio.service.GymContext;
import gt.edu.gimnasio.service.MemberService;
import gt.edu.gimnasio.service.MemberValidationException;

/** Controla la consulta de miembros desde la vista FXML. */
public class MembersController {

    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private Member selectedMember;
    private boolean membersLoaded;

    public MembersController() {
        this(new GymContext());
    }

    public MembersController(GymContext gymContext) {
        memberRepository = new MemberRepository(gymContext);
        memberService = new MemberService(memberRepository);
    }

    @FXML
    private TextField firstNamesField;

    @FXML
    private TextField lastNamesField;

    @FXML
    private TextField documentField;

    @FXML
    private TextField phoneField;

    @FXML
    private TextField emailField;

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
    private Button saveButton;

    @FXML
    private Button updateButton;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().getId()));
        firstNamesColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getFirstNames()));
        lastNamesColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getLastNames()));
        documentColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(valueOrEmpty(cell.getValue().getDocumentNumber())));
        phoneColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(valueOrEmpty(cell.getValue().getPhone())));
        emailColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(valueOrEmpty(cell.getValue().getEmail())));

        membersTable.setPlaceholder(new Label("No hay miembros registrados todavía."));
        membersTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previousMember, currentMember) -> selectMember(currentMember));
        updateSelectionControls(null);
        loadMembers();
    }

    @FXML
    private void saveMember() {
        try {
            Member member = memberService.createMember(
                    firstNamesField.getText(), lastNamesField.getText(), documentField.getText(),
                    phoneField.getText(), emailField.getText());
            clearForm();
            if (loadMembers()) {
                showFeedback("Miembro \"" + member.getFullName() + "\" registrado correctamente.", true);
            } else {
                showFeedback("Miembro \"" + member.getFullName() + "\" registrado, pero no fue posible recargar la lista. "
                        + "Vuelve a abrir Miembros para consultar los datos.", false);
            }
        } catch (MemberValidationException exception) {
            showFeedback(exception.getMessage(), false);
        } catch (IllegalStateException exception) {
            showFeedback(exception.getMessage(), false);
        } catch (SQLException exception) {
            showFeedback("No fue posible registrar el miembro. Verifica la conexión a PostgreSQL.", false);
        }
    }

    @FXML
    private void updateMember() {
        if (selectedMember == null) {
            showFeedback("Selecciona un miembro para actualizarlo.", false);
            return;
        }

        try {
            Member updatedMember = memberService.updateMember(
                    selectedMember.getId(), firstNamesField.getText(), lastNamesField.getText(),
                    documentField.getText(), phoneField.getText(), emailField.getText());
            boolean refreshed = loadMembers();
            clearForm();
            if (refreshed) {
                showFeedback("Miembro \"" + updatedMember.getFullName() + "\" actualizado correctamente.", true);
            } else {
                showFeedback("Miembro \"" + updatedMember.getFullName() + "\" actualizado, pero no fue posible recargar la lista. "
                        + "Vuelve a abrir Miembros para consultar los datos.", false);
            }
        } catch (MemberValidationException exception) {
            showFeedback(exception.getMessage(), false);
        } catch (MemberNotFoundException exception) {
            loadMembers();
            clearForm();
            showFeedback(exception.getMessage(), false);
        } catch (IllegalStateException exception) {
            showFeedback(exception.getMessage(), false);
        } catch (SQLException exception) {
            showFeedback("No fue posible actualizar el miembro. Verifica la conexión a PostgreSQL.", false);
        }
    }

    private boolean loadMembers() {
        membersLoaded = false;
        updateSelectionControls(null);

        try {
            List<Member> members = memberRepository.findAll();
            membersLoaded = true;
            membersTable.setItems(FXCollections.observableArrayList(members));
            updateSelectionControls(selectedMember);
            showFeedback("", true);
            return true;
        } catch (IllegalStateException exception) {
            membersTable.setItems(FXCollections.observableArrayList());
            showFeedback(exception.getMessage(), false);
        } catch (SQLException exception) {
            membersTable.setItems(FXCollections.observableArrayList());
            showFeedback("No fue posible consultar el gimnasio actual o sus miembros. "
                    + "Verifica la conexión y la migración multitenant en PostgreSQL.", false);
        }

        return false;
    }

    @FXML
    private void clearForm() {
        membersTable.getSelectionModel().clearSelection();
        firstNamesField.clear();
        lastNamesField.clear();
        documentField.clear();
        phoneField.clear();
        emailField.clear();
        selectedMember = null;
        updateSelectionControls(null);
    }

    private void selectMember(Member member) {
        selectedMember = member;
        updateSelectionControls(member);

        if (member != null) {
            firstNamesField.setText(member.getFirstNames());
            lastNamesField.setText(member.getLastNames());
            documentField.setText(valueOrEmpty(member.getDocumentNumber()));
            phoneField.setText(valueOrEmpty(member.getPhone()));
            emailField.setText(valueOrEmpty(member.getEmail()));
        }
    }

    private void updateSelectionControls(Member member) {
        saveButton.setDisable(!membersLoaded);
        updateButton.setDisable(!membersLoaded || member == null);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
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
