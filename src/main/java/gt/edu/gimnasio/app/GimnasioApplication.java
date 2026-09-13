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
        Scene scene = new Scene(loader.load(), 1000, 650);
        scene.getStylesheets().add(
                GimnasioApplication.class.getResource("/gt/edu/gimnasio/view/styles.css").toExternalForm());

        stage.setTitle("Gestión de Gimnasio");
        stage.setMinWidth(900);
        stage.setMinHeight(580);
        stage.setScene(scene);
        stage.show();
    }
}
