package com.stegomsg.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Full-screen, modal loading layer used for remote/database operations. */
public final class LoadingOverlay extends StackPane {

    private final Label title = new Label("Please wait…");
    private final Label message = new Label("Working securely in the background.");
    private final ProgressIndicator spinner = new ProgressIndicator();

    public LoadingOverlay() {
        setAlignment(Pos.CENTER);
        getStyleClass().add("loading-overlay");
        setVisible(false);
        setManaged(false);

        spinner.setPrefSize(46, 46);
        spinner.getStyleClass().add("loading-spinner");

        title.getStyleClass().add("loading-title");
        message.getStyleClass().add("loading-message");
        message.setWrapText(true);
        message.setMaxWidth(300);
        message.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        VBox card = new VBox(10, spinner, title, message);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("loading-card");
        getChildren().add(card);
    }

    public void show(String titleText, String messageText) {
        title.setText(titleText);
        message.setText(messageText);
        setManaged(true);
        setVisible(true);
        toFront();
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
    }

    public boolean isShowing() {
        return isVisible();
    }
}
