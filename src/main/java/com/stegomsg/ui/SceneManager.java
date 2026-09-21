package com.stegomsg.ui;

import com.stegomsg.service.AppServices;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class SceneManager {

    private final Stage stage;
    private final AppServices services;

    public SceneManager(Stage stage, AppServices services) {
        this.stage = stage;
        this.services = services;
    }

    public AppServices services() {
        return services;
    }

    public void showLogin() {
        setRoot(loadFxml("/com/stegomsg/fxml/login.fxml"), 980, 650, 860, 590, true);
    }

    public void showRegister() {
        setRoot(loadFxml("/com/stegomsg/fxml/register.fxml"), 1040, 760, 880, 650, true);
    }

    public void showDashboard() {
        DashboardView dashboard = new DashboardView(this);
        setRoot(dashboard, 1240, 780, 1040, 680, true);
    }

    private Parent loadFxml(String path) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
            loader.setControllerFactory(controllerClass -> {
                try {
                    return controllerClass.getConstructor(SceneManager.class).newInstance(this);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Failed to construct controller " + controllerClass, e);
                }
            });
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void setRoot(Parent root, double width, double height, double minWidth, double minHeight,
                         boolean resizable) {
        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(root, width, height);
            scene.getStylesheets().add(getClass().getResource("/com/stegomsg/css/theme-dark.css").toExternalForm());
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
            stage.setWidth(width);
            stage.setHeight(height);
        }

        stage.setTitle("Secure Stego Messenger");
        stage.setMinWidth(minWidth);
        stage.setMinHeight(minHeight);
        stage.setResizable(resizable);
        stage.centerOnScreen();
        stage.show();
    }
}
