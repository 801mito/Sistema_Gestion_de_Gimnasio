package gt.edu.gimnasio.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import gt.edu.gimnasio.model.Employee;
import gt.edu.gimnasio.service.AuthenticationException;
import gt.edu.gimnasio.service.AuthenticationService;

/** Recoge las credenciales sin permitir elegir un gimnasio manualmente. */
public class LoginController {

    @FunctionalInterface
    public interface SessionOpener {
        void open(Employee employee) throws IOException, SQLException;
    }

    private final AuthenticationService authenticationService;
    private final SessionOpener sessionOpener;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private Label feedbackLabel;

    public LoginController(AuthenticationService authenticationService, SessionOpener sessionOpener) {
        this.authenticationService = Objects.requireNonNull(authenticationService);
        this.sessionOpener = Objects.requireNonNull(sessionOpener);
    }

    @FXML
    private void login() {
        char[] password = passwordField.getText().toCharArray();
        passwordField.clear();
        feedbackLabel.setText("");
        feedbackLabel.setManaged(false);
        feedbackLabel.setVisible(false);
        loginButton.setDisable(true);

        try {
            Employee employee = authenticationService.authenticate(usernameField.getText(), password);
            sessionOpener.open(employee);
        } catch (AuthenticationException exception) {
            showError(exception.getMessage());
        } catch (SQLException | IOException | IllegalStateException exception) {
            showError("No fue posible abrir la sesión. Revisa la conexión a PostgreSQL.");
        } finally {
            Arrays.fill(password, '\0');
            loginButton.setDisable(false);
        }
    }

    private void showError(String message) {
        feedbackLabel.setText(message);
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
    }
}
