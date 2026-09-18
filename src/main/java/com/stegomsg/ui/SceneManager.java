package com.stegomsg.ui;

import com.stegomsg.service.AppServices;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Central navigation point. Owns the primary Stage and swaps its Scene's root between
 * the Login/Register screens and the main Dashboard shell — the single-window,
 * single-shell approach requested in ARCHITECTURE.md section 9 ("avoid opening many
 * disconnected windows; prefer a single main shell with changing content views").
 */
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
        setRoot(loadFxml("/com/stegomsg/fxml/login.fxml"), 480, 640);
    }

    public void showRegister() {
        setRoot(loadFxml("/com/stegomsg/fxml/register.fxml"), 480, 680);
    }

    public void showDashboard() {
        DashboardView dashboard = new DashboardView(this);
        setRoot(dashboard, 1100, 720);
    }

    private Parent loadFxml(String path) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
            loader.setControllerFactory(controllerClass -> {
                try {
                    // Every controller in this app takes a SceneManager in its constructor,
                    // giving it access to both navigation and the shared service layer.
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

    private void setRoot(Parent root, double width, double height) {
        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(root, width, height);
            scene.getStylesheets().add(getClass().getResource("/com/stegomsg/css/theme-dark.css").toExternalForm());
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
        stage.setTitle("Secure Stego Messenger");
        stage.setMinWidth(720);
        stage.setMinHeight(540);
        stage.show();
    }
}
