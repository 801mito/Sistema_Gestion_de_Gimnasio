package gt.edu.gimnasio.app;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Punto de entrada de la aplicación de escritorio. */
public class GimnasioApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                GimnasioApplication.class.getResource("/gt/edu/gimnasio/view/main-view.fxml"));
        Scene scene = new Scene(loader.load(), 900, 600);
        scene.getStylesheets().add(
                GimnasioApplication.class.getResource("/gt/edu/gimnasio/view/styles.css").toExternalForm());

        stage.setTitle("Sistema de Gestión de Gimnasio");
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        stage.setScene(scene);
        stage.show();
    }
}
