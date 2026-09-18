package com.stegomsg;

import com.stegomsg.service.AppServices;
import com.stegomsg.ui.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;

import java.nio.file.Path;

/**
 * Application entry point.
 *
 * All application data (SQLite database, sent stego-images, device-local Quick-PIN key
 * material, generated default image library) lives under a single per-user app-data
 * directory, kept out of the source tree so a fresh checkout always starts clean.
 */
public final class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        Path appDataDir = resolveAppDataDir();
        AppServices services = new AppServices(appDataDir);
        SceneManager sceneManager = new SceneManager(primaryStage, services);
        sceneManager.showLogin();
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
