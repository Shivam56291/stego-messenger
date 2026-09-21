package com.stegomsg;

import com.stegomsg.service.AppServices;
import com.stegomsg.ui.SceneManager;
import com.stegomsg.ui.StartupView;
import com.stegomsg.ui.UiIcon;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Path;

public final class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        StartupView startupView = new StartupView();
        Scene scene = new Scene(startupView, 760, 520);
        scene.getStylesheets().add(getClass().getResource("/com/stegomsg/css/theme-dark.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("Secure Stego Messenger");
        primaryStage.setMinWidth(620);
        primaryStage.setMinHeight(460);
        primaryStage.setResizable(false);
        primaryStage.centerOnScreen();
        primaryStage.show();

        Task<AppServices> startupTask = new Task<>() {
            @Override
            protected AppServices call() {
                updateMessage("Connecting to the secure database…");
                return new AppServices(resolveAppDataDir());
            }
        };
        startupTask.messageProperty().addListener((obs, old, message) -> startupView.setStatus(message));

        startupTask.setOnSucceeded(event -> {
            SceneManager sceneManager = new SceneManager(primaryStage, startupTask.getValue());
            sceneManager.showLogin();
        });

        startupTask.setOnFailed(event -> showStartupError(primaryStage, startupTask.getException()));

        Thread thread = new Thread(startupTask, "stegomsg-startup");
        thread.setDaemon(true);
        thread.start();
    }

    private void showStartupError(Stage stage, Throwable throwable) {
        String message = "We couldn't connect to the secure services. Check your internet connection "
                + "and Supabase database configuration, then retry.";

        Label title = new Label("Couldn't start Secure Stego Messenger");
        title.getStyleClass().add("startup-title");

        Label detail = new Label(message);
        detail.getStyleClass().add("startup-error");
        detail.setWrapText(true);
        detail.setMaxWidth(520);

        Button retry = new Button("Retry");
        retry.getStyleClass().add("button-primary");
        retry.setOnAction(e -> {
            stage.close();
            start(new Stage());
        });

        VBox box = new VBox(14, UiIcon.icon(UiIcon.Name.WARNING, 48), title, detail, retry);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        box.getStyleClass().add("startup-view");
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("clean-scroll");

        Scene scene = new Scene(scroll, 760, 520);
        scene.getStylesheets().add(getClass().getResource("/com/stegomsg/css/theme-dark.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Secure Stego Messenger");
        stage.centerOnScreen();
        stage.show();
    }

    private Path resolveAppDataDir() {
        String override = System.getProperty("stegomsg.dataDir");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".stegomsg");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
