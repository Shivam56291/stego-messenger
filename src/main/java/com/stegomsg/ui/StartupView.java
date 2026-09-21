package com.stegomsg.ui;

import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Startup screen displayed while the remote database/application services initialize. */
public final class StartupView extends VBox {

    private final Label statusLabel = new Label("Connecting securely…");

    public StartupView() {
        setAlignment(Pos.CENTER);
        setSpacing(16);
        getStyleClass().add("startup-view");

        var logo = UiIcon.icon(UiIcon.Name.SHIELD, 64);
        logo.getStyleClass().add("brand-icon");

        Label title = new Label("Secure Stego Messenger");
        title.getStyleClass().add("startup-title");

        Label subtitle = new Label("Preparing your secure workspace");
        subtitle.getStyleClass().add("startup-subtitle");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(44, 44);

        statusLabel.getStyleClass().add("startup-status");

        getChildren().addAll(logo, title, subtitle, spinner, statusLabel);

        FadeTransition fade = new FadeTransition(Duration.seconds(1.3), logo);
        fade.setFromValue(0.65);
        fade.setToValue(1.0);
        fade.setAutoReverse(true);
        fade.setCycleCount(FadeTransition.INDEFINITE);
        fade.play();
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }
}
