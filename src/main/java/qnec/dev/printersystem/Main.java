package qnec.dev.printersystem;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    //public static final String DESTINATION = "./Reports/";
    public static final Logger logger = LogManager.getLogger(Main.class);

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("Views/DetectionSystemView.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("QNEC - APP FORKLIFT");
        stage.setScene(scene);
        stage.show();
        logger.info("Qnec Info - Application started successfully.");
    }

    public static void main(String[] args) {
        launch();
    }
}
