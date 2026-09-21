package com.stegomsg.ui;

import com.stegomsg.model.User;
import com.stegomsg.service.AuthService;
import com.stegomsg.util.Validation;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Optional;

public final class SettingsPane extends BorderPane {

    private final SceneManager sceneManager;
    private final VBox content = new VBox(16);
    private final ProgressIndicator busy = new ProgressIndicator();

    public SettingsPane(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        getStyleClass().add("settings-page");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");

        busy.setPrefSize(32, 32);
        busy.setVisible(false);
        busy.setManaged(false);

        StackPane center = new StackPane(scroll, busy);
        StackPane.setAlignment(busy, Pos.CENTER);

        setTop(buildHeading());
        setCenter(center);
    }

    private Node buildHeading() {
        Label title = new Label("Settings");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Manage your account and device-local security preferences.");
        subtitle.getStyleClass().add("muted");

        HBox box = new HBox(10, UiIcon.icon(UiIcon.Name.SETTINGS, 20),
                new VBox(3, title, subtitle));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 0, 4, 0));
        return box;
    }

    public void refresh() {
        User user = sceneManager.services().sessionService.requireCurrentUser();
        content.getChildren().setAll(
                sectionHeader("Account", "Basic account details shown by the messenger."),
                accountCard(user),
                sectionHeader("Security", "Quick PIN is a convenience layer for this device."),
                securityCard(user),
                sectionHeader("Privacy", "What is retained and what this application deliberately avoids collecting."),
                privacyCard(),
                sectionHeader("Application", "Build and security-model information."),
                aboutCard()
        );
    }

    private HBox sectionHeader(String title, String subtitle) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("muted");

        HBox box = new HBox(10, UiIcon.icon(UiIcon.Name.ARROW_RIGHT, 12),
                new VBox(2, titleLabel, subtitleLabel));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private VBox accountCard(User user) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        card.getChildren().addAll(
                settingRow("Email", user.getEmail()),
                settingRow("Display alias", user.getDisplayAlias() == null || user.getDisplayAlias().isBlank()
                        ? "Not set" : user.getDisplayAlias()),
                settingRow("Account status", user.getStatus())
        );
        return card;
    }

    private VBox securityCard(User user) {
        VBox wrapper = new VBox(12);

        VBox statusCard = new VBox(4);
        statusCard.getStyleClass().add("security-status-card");

        HBox statusHeader = new HBox(8,
                UiIcon.icon(user.isPinEnabled() ? UiIcon.Name.CHECK : UiIcon.Name.LOCK, 15),
                new Label(user.isPinEnabled() ? "Quick PIN is enabled" : "Quick PIN is disabled"));
        statusHeader.setAlignment(Pos.CENTER_LEFT);
        ((Label) statusHeader.getChildren().get(1)).getStyleClass().add("security-status-title");

        Label copy = new Label(user.isPinEnabled()
                ? "A 6-digit PIN can unlock this device without entering the account password."
                : "Enable a 6-digit PIN for faster sign-in on this device.");
        copy.setWrapText(true);
        copy.getStyleClass().add("security-status-copy");
        statusCard.getChildren().addAll(statusHeader, copy);

        Button toggle = new Button(user.isPinEnabled() ? "Disable Quick PIN" : "Enable Quick PIN");
        toggle.setGraphic(UiIcon.icon(user.isPinEnabled() ? UiIcon.Name.LOGOUT : UiIcon.Name.KEY, 14));
        toggle.getStyleClass().add(user.isPinEnabled() ? "button-danger" : "button-primary");
        toggle.setOnAction(e -> {
            if (user.isPinEnabled()) {
                confirmDisablePin(user);
            } else {
                promptForNewPin(user);
            }
        });

        Label note = new Label("Quick PIN is device-local. It does not replace the account password or protect a compromised computer.");
        note.setWrapText(true);
        note.getStyleClass().add("hint");

        wrapper.getChildren().addAll(statusCard, toggle, note);
        return wrapper;
    }

    private void promptForNewPin(User user) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Enable Quick PIN");
        dialog.setHeaderText("Create a 6-digit device PIN");

        PasswordField pin = new PasswordField();
        pin.setPromptText("6 digits");

        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Repeat PIN");

        Label hint = new Label(
                "The PIN is stored only as a verifier plus protected device key material."
        );
        hint.setWrapText(true);
        hint.getStyleClass().add("dialog-helper");

        VBox body = new VBox(10, pin, confirm, hint);
        body.getStyleClass().add("dialog-content");
        dialog.getDialogPane().setContent(body);

        ButtonType save = new ButtonType(
                "Enable PIN",
                ButtonBar.ButtonData.OK_DONE
        );

        dialog.getDialogPane().getButtonTypes()
                .addAll(save, ButtonType.CANCEL);

        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.setDisable(true);

        Runnable validate = () -> {
            String first = pin.getText();
            String second = confirm.getText();

            boolean valid = Validation.isValidPin(first)
                    && first.equals(second);

            saveButton.setDisable(!valid);

            hint.getStyleClass().removeAll(
                    "status-danger",
                    "status-safe"
            );

            if (!first.isEmpty() && !Validation.isValidPin(first)) {

                hint.setText("PIN must be exactly 6 digits.");
                hint.getStyleClass().add("status-danger");

            } else if (!second.isEmpty() && !first.equals(second)) {

                hint.setText("The two PINs do not match.");
                hint.getStyleClass().add("status-danger");

            } else if (valid) {

                hint.setText(
                        "PINs match. This device can now use Quick PIN."
                );
                hint.getStyleClass().add("status-safe");

            } else {

                hint.setText(
                        "Use a 6-digit PIN you can remember for this device."
                );
            }
        };

        pin.textProperty().addListener((obs, old, value) -> {

            String digits = value.replaceAll("\\D", "");

            if (!digits.equals(value)) {
                pin.setText(digits);
            }

            if (digits.length() > 6) {
                pin.setText(digits.substring(0, 6));
            }

            validate.run();
        });

        confirm.textProperty().addListener((obs, old, value) -> {

            String digits = value.replaceAll("\\D", "");

            if (!digits.equals(value)) {
                confirm.setText(digits);
            }

            if (digits.length() > 6) {
                confirm.setText(digits.substring(0, 6));
            }

            validate.run();
        });

        validate.run();

        dialog.setResultConverter(bt ->
                bt == save ? pin.getText() : null
        );

        dialog.getDialogPane().getStylesheets().add(
                getClass()
                        .getResource("/com/stegomsg/css/theme-dark.css")
                        .toExternalForm()
        );

        Optional<String> result = dialog.showAndWait();

        result.ifPresent(value -> {
            UiTaskRunner.run(
                    () -> {
                        sceneManager.services()
                                .authService
                                .enableQuickPin(user, value.toCharArray());
                        return null;
                    },
                    () -> showBusy(true),
                    ignored -> refresh(),
                    error -> showError(
                            error instanceof AuthService.AuthException
                                    ? error.getMessage()
                                    : "We couldn't enable Quick PIN."
                    ),
                    () -> showBusy(false)
            );
        });
    }

    private void confirmDisablePin(User user) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "You will need your account password the next time you want to sign in with full authentication.",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.setTitle("Disable Quick PIN");
        confirm.setHeaderText("Disable device PIN?");
        confirm.getDialogPane().getStylesheets().add(
                getClass().getResource("/com/stegomsg/css/theme-dark.css").toExternalForm());

        confirm.showAndWait().filter(type -> type == ButtonType.OK).ifPresent(type ->
                UiTaskRunner.run(
                        () -> {
                            sceneManager.services().authService.disableQuickPin(user);
                            return null;
                        },
                        () -> showBusy(true),
                        ignored -> refresh(),
                        error -> showError(error instanceof AuthService.AuthException
                                ? error.getMessage() : "We couldn't disable Quick PIN."),
                        () -> showBusy(false)
                ));
    }

    private VBox privacyCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label retention = new Label("Sent-image history keeps only the most recent 10 entries. Older history records are automatically pruned.");
        retention.setWrapText(true);
        retention.getStyleClass().add("hint");

        Label privacy = new Label("The account model intentionally avoids collecting real names, phone numbers, physical addresses, or mandatory profile photos.");
        privacy.setWrapText(true);
        privacy.getStyleClass().add("hint");

        Button deleteAccountButton = new Button("Delete Account");
        deleteAccountButton.getStyleClass().add("button-danger");
        deleteAccountButton.setOnAction(e -> showError("Account deletion is not available in this build yet."));

        card.getChildren().addAll(retention, privacy, deleteAccountButton);
        return card;
    }

    private VBox aboutCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");

        Label version = new Label("Secure Stego Messenger • v1.0.0");
        version.getStyleClass().add("setting-value");

        Label privacy = new Label("Messages are encrypted before being hidden inside PNG images. Steganography is an additional privacy layer, not a guarantee of perfect anonymity.");
        privacy.setWrapText(true);
        privacy.getStyleClass().add("hint");

        card.getChildren().addAll(version, privacy);
        return card;
    }

    private HBox settingRow(String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("setting-label");

        Label v = new Label(value);
        v.getStyleClass().add("setting-value");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, l, spacer, v);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void showBusy(boolean showing) {
        busy.setVisible(showing);
        busy.setManaged(showing);
        content.setDisable(showing);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle("Settings");
        alert.setHeaderText(null);
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/com/stegomsg/css/theme-dark.css").toExternalForm());
        alert.showAndWait();
    }
}
