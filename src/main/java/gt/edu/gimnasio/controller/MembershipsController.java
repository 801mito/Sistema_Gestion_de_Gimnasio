package gt.edu.gimnasio.controller;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import gt.edu.gimnasio.model.Membership;
import gt.edu.gimnasio.model.Member;
import gt.edu.gimnasio.model.Plan;
import gt.edu.gimnasio.repository.MemberRepository;
import gt.edu.gimnasio.repository.MembershipRepository;
import gt.edu.gimnasio.repository.PlanRepository;
import gt.edu.gimnasio.service.MembershipService;
import gt.edu.gimnasio.service.MembershipValidationException;

/** Controla la consulta del historial de membresías. */
public class MembershipsController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final MembershipRepository membershipRepository = new MembershipRepository();
    private final MemberRepository memberRepository = new MemberRepository();
    private final PlanRepository planRepository = new PlanRepository();
    private final MembershipService membershipService = new MembershipService(membershipRepository);

    @FXML
    private ComboBox<Member> memberComboBox;

    @FXML
    private ComboBox<Plan> planComboBox;

    @FXML
    private DatePicker startDatePicker;

    @FXML
    private Label endDateLabel;

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
        memberComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Member member) {
                return member == null ? "" : member.getFullName();
            }

            @Override
            public Member fromString(String value) {
                return null;
            }
        });
        planComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Plan plan) {
                return plan == null ? "" : plan.getName() + " (" + plan.getDurationDays() + " días)";
            }

            @Override
            public Plan fromString(String value) {
                return null;
            }
        });
        startDatePicker.setValue(LocalDate.now());
        planComboBox.valueProperty().addListener((observable, oldPlan, newPlan) -> updateEndDate());
        startDatePicker.valueProperty().addListener((observable, oldDate, newDate) -> updateEndDate());
        loadFormOptions();
        loadMemberships();
    }

    @FXML
    private void assignMembership() {
        try {
            Member member = memberComboBox.getValue();
            Plan plan = planComboBox.getValue();
            LocalDate startDate = startDatePicker.getValue();
            membershipService.assignMembership(member, plan, startDate);
            loadMemberships();
            clearForm();
            showFeedback("Membresía asignada correctamente a " + member.getFullName() + ".", true);
        } catch (MembershipValidationException exception) {
            showFeedback(exception.getMessage(), false);
        } catch (SQLException exception) {
            showFeedback("No fue posible asignar la membresía. Verifica la conexión a PostgreSQL.", false);
        }
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

    private void loadFormOptions() {
        try {
            memberComboBox.setItems(FXCollections.observableArrayList(memberRepository.findAll()));
            planComboBox.setItems(FXCollections.observableArrayList(planRepository.findActive()));
        } catch (SQLException | IllegalStateException exception) {
            showFeedback("No fue posible cargar miembros y planes activos.", false);
        }
    }

    private void updateEndDate() {
        Plan plan = planComboBox.getValue();
        LocalDate startDate = startDatePicker.getValue();

        if (plan == null || startDate == null) {
            endDateLabel.setText("Selecciona un plan y una fecha de inicio.");
            return;
        }

        endDateLabel.setText("Fecha final: " + membershipService.calculateEndDate(plan, startDate).format(DATE_FORMATTER));
    }

    private void clearForm() {
        memberComboBox.getSelectionModel().clearSelection();
        planComboBox.getSelectionModel().clearSelection();
        startDatePicker.setValue(LocalDate.now());
        updateEndDate();
    }

    private void showFeedback(String message) {
        showFeedback(message, false);
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
