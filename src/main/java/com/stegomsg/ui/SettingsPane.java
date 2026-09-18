package com.stegomsg.ui;

import com.stegomsg.model.User;
import com.stegomsg.service.AuthService;
import com.stegomsg.util.Validation;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Settings screen (ARCHITECTURE.md section 8). Never displays password hashes, PIN
 * hashes, encryption keys, or session tokens — only the account-facing summary and the
 * controls to change them.
 */
public final class SettingsPane extends VBox {

    private final SceneManager sceneManager;
    private final VBox securitySection = new VBox(10);

    public SettingsPane(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        setSpacing(20);
        setPadding(new Insets(20));
        setMaxWidth(560);

        getChildren().addAll(
                sectionTitle("Account"),
                accountCard(),
                sectionTitle("Security"),
                securitySection,
                sectionTitle("Privacy"),
                privacyCard(),
                sectionTitle("Application"),
                aboutCard()
        );
    }

    public void refresh() {
        securitySection.getChildren().setAll(securityCard());
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private VBox accountCard() {
        User user = sceneManager.services().sessionService.requireCurrentUser();
        VBox card = new VBox(6,
                row("Email", user.getEmail()),
                row("Display alias", user.getDisplayAlias() == null ? "(none set)" : user.getDisplayAlias()),
                row("Account status", user.getStatus())
        );
        card.getStyleClass().add("card");
        return card;
    }

    private VBox securityCard() {
        User user = sceneManager.services().sessionService.requireCurrentUser();
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label pinStatus = new Label("Quick PIN Login: " + (user.isPinEnabled() ? "Enabled" : "Disabled"));

        Button toggleButton = new Button(user.isPinEnabled() ? "Disable Quick PIN" : "Enable Quick PIN");
        toggleButton.getStyleClass().add(user.isPinEnabled() ? "button-danger" : "button-primary");
        toggleButton.setOnAction(e -> {
            if (user.isPinEnabled()) {
                sceneManager.services().authService.disableQuickPin(user);
                refresh();
            } else {
                promptForNewPin(user);
            }
        });

        Label note = new Label("Quick PIN is a local convenience for this device only — it does not "
                + "protect your account if this computer is compromised, and a 6-digit PIN alone is "
                + "never sufficient to protect your account remotely.");
        note.setWrapText(true);
        note.getStyleClass().add("hint");

        card.getChildren().addAll(pinStatus, toggleButton, note);
        return card;
    }

    private void promptForNewPin(User user) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Enable Quick PIN");
        dialog.setHeaderText("Choose a 6-digit PIN for this device");
        dialog.setContentText("New PIN:");
        Optional<String> pin = dialog.showAndWait();
        pin.ifPresent(p -> {
            if (!Validation.isValidPin(p)) {
                alert("PIN must be exactly 6 digits.");
                return;
            }
            try {
                sceneManager.services().authService.enableQuickPin(user, p.toCharArray());
                refresh();
            } catch (AuthService.AuthException e) {
                alert(e.getMessage());
            }
        });
    }

    private VBox privacyCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label retention = new Label("Only your last 10 sent images are retained in your history; "
                + "older entries are automatically removed.");
        retention.setWrapText(true);
        retention.getStyleClass().add("hint");

        Button deleteAccountButton = new Button("Delete Account");
        deleteAccountButton.getStyleClass().add("button-danger");
        deleteAccountButton.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "This will permanently delete your account and cannot be undone.", ButtonType.CANCEL, ButtonType.OK);
            confirm.setHeaderText("Delete your account?");
            confirm.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt ->
                    alert("Account deletion is not wired up in this demo build — see AuthService for where to add it."));
        });

        card.getChildren().addAll(retention, deleteAccountButton);
        return card;
    }

    private VBox aboutCard() {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");
        Label version = new Label("Secure Stego Messenger \u2014 v1.0.0 (academic demo build)");
        Label privacy = new Label("This application is privacy-oriented and pseudonymous, not perfectly "
                + "anonymous. Messages are encrypted end-to-end and hidden inside images; steganography "
                + "is a privacy layer, not a substitute for encryption, and can in principle be detected "
                + "by dedicated steganalysis. See ARCHITECTURE.md for the full privacy model.");
        privacy.setWrapText(true);
        privacy.getStyleClass().add("hint");
        card.getChildren().addAll(version, privacy);
        return card;
    }

    private HBox row(String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label v = new Label(value);
        HBox box = new HBox(8, l, spacer, v);
        return box;
    }

    private void alert(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
