package gt.edu.gimnasio.app;

import java.io.IOException;
import java.sql.SQLException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import gt.edu.gimnasio.controller.LoginController;
import gt.edu.gimnasio.controller.MainController;
import gt.edu.gimnasio.model.Employee;
import gt.edu.gimnasio.service.AuthenticationService;
import gt.edu.gimnasio.service.EmployeeSession;

/** Punto de entrada de la aplicación de escritorio. */
public class GimnasioApplication extends Application {

    private Stage primaryStage;
    private EmployeeSession session;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        stage.setTitle("Gestión de Gimnasio");
        showLogin();
        stage.show();
    }

    private void showLogin() throws IOException {
        session = null;
        FXMLLoader loader = new FXMLLoader(
                GimnasioApplication.class.getResource("/gt/edu/gimnasio/view/login-view.fxml"));
        loader.setControllerFactory(type -> new LoginController(new AuthenticationService(), this::showMain));
        Scene scene = createScene(loader, 520, 420);
        primaryStage.setMinWidth(480);
        primaryStage.setMinHeight(380);
        primaryStage.setScene(scene);
        primaryStage.setWidth(520);
        primaryStage.setHeight(420);
    }

    private void showMain(Employee employee) throws IOException, SQLException {
        EmployeeSession nextSession = new EmployeeSession(employee);
        nextSession.getGymContext().getCurrentGym();
        FXMLLoader loader = new FXMLLoader(
                GimnasioApplication.class.getResource("/gt/edu/gimnasio/view/main-view.fxml"));
        loader.setControllerFactory(type -> new MainController(nextSession, this::logout));
        Scene scene = createScene(loader, 1000, 650);
        session = nextSession;
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(580);
        primaryStage.setScene(scene);
        primaryStage.setWidth(1000);
        primaryStage.setHeight(650);
    }

    private void logout() {
        try {
            showLogin();
        } catch (IOException exception) {
            // Si el login no puede cargarse, no se deja accesible la ventana autenticada.
            session = null;
            primaryStage.close();
        }
    }

    private Scene createScene(FXMLLoader loader, int width, int height) throws IOException {
        Scene scene = new Scene(loader.load(), width, height);
        scene.getStylesheets().add(
                GimnasioApplication.class.getResource("/gt/edu/gimnasio/view/styles.css").toExternalForm());
        return scene;
    }
}
