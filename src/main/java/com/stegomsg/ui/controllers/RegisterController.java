package com.stegomsg.ui.controllers;

import com.stegomsg.service.AuthService;
import com.stegomsg.ui.LoadingOverlay;
import com.stegomsg.ui.SceneManager;
import com.stegomsg.ui.UiTaskRunner;
import com.stegomsg.util.Validation;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public final class RegisterController {

    private final SceneManager sceneManager;

    @FXML private StackPane root;
    @FXML private TextField emailField;
    @FXML private TextField aliasField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ProgressBar strengthBar;
    @FXML private Label strengthLabel;
    @FXML private Label errorLabel;
    @FXML private Label warningLabel;
    @FXML private Label emailHint;
    @FXML private Label confirmHint;
    @FXML private Button passwordToggleButton;
    @FXML private Button registerButton;

    private LoadingOverlay loadingOverlay;
    private boolean passwordVisible;

    public RegisterController(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    @FXML
    private void initialize() {
        loadingOverlay = new LoadingOverlay();
        root.getChildren().add(loadingOverlay);
        errorLabel.getStyleClass().add("banner-danger");
        warningLabel.getStyleClass().add("banner-warning");
        registerButton.getStyleClass().add("button-large");

        // Keep both submit buttons visually consistent.
        root.lookupAll(".button-secondary").forEach(node -> node.getStyleClass().add("button-large"));

        emailField.textProperty().addListener((obs, old, value) -> {
            clearGeneralMessages();
            emailField.getStyleClass().remove("input-error");
            hide(emailHint);
        });

        aliasField.textProperty().addListener((obs, old, value) -> clearGeneralMessages());

        passwordField.textProperty().addListener((obs, old, value) -> {
            if (!passwordVisible) passwordVisibleField.setText(value);
            updatePasswordStrength(value);
            clearGeneralMessages();
            hide(confirmHint);
        });

        passwordVisibleField.textProperty().addListener((obs, old, value) -> {
            if (passwordVisible) passwordField.setText(value);
            updatePasswordStrength(value);
            clearGeneralMessages();
        });

        confirmPasswordField.textProperty().addListener((obs, old, value) -> {
            clearGeneralMessages();
            hide(confirmHint);
        });

        updatePasswordStrength("");
        registerButton.setTooltip(new Tooltip("Create your account and generate your encryption keys"));
        passwordToggleButton.setTooltip(new Tooltip("Show or hide the password"));
    }

    @FXML
    private void handlePasswordTyped() {
        updatePasswordStrength(passwordVisible ? passwordVisibleField.getText() : passwordField.getText());
        clearGeneralMessages();
    }

    private void updatePasswordStrength(String value) {
        Validation.PasswordStrength strength = Validation.assessPassword(value);
        strengthBar.setProgress(strength.score() / 4.0);
        strengthLabel.setText(strength.feedback());
        strengthLabel.getStyleClass().removeAll(
                "strength-weak", "strength-fair", "strength-good", "strength-strong");

        if (strength.score() <= 1) strengthLabel.getStyleClass().add("strength-weak");
        else if (strength.score() == 2) strengthLabel.getStyleClass().add("strength-fair");
        else if (strength.score() == 3) strengthLabel.getStyleClass().add("strength-good");
        else strengthLabel.getStyleClass().add("strength-strong");
    }

    @FXML
    private void handleRegister() {
        clearGeneralMessages();

        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String alias = aliasField.getText() == null || aliasField.getText().isBlank()
                ? null : aliasField.getText().trim();
        String password = passwordVisible ? passwordVisibleField.getText() : passwordField.getText();
        String confirm = confirmPasswordField.getText();

        if (!Validation.isValidEmail(email)) {
            showFieldError(emailField, emailHint, "Enter a valid email address.");
            return;
        }

        Validation.PasswordStrength strength = Validation.assessPassword(password);
        if (!strength.meetsMinimum()) {
            showError(strength.feedback());
            passwordField.requestFocus();
            return;
        }

        if (!password.equals(confirm)) {
            showFieldError(confirmPasswordField, confirmHint, "Passwords do not match.");
            return;
        }

        UiTaskRunner.run(
                () -> sceneManager.services().authService.register(
                        email,
                        password.toCharArray(),
                        alias
                ),
                () -> setLoading(true, "Creating your account…", "Generating your encryption keys and saving your account securely."),
                user -> {
                    passwordField.clear();
                    passwordVisibleField.clear();
                    confirmPasswordField.clear();

                    Label success = new Label("Account created successfully. Opening the sign-in screen…");
                    success.getStyleClass().addAll("inline-banner", "banner-success");
                    // Keep feedback visible briefly without blocking the JavaFX thread.
                    root.getChildren().add(success);
                    StackPane.setAlignment(success, javafx.geometry.Pos.BOTTOM_CENTER);
                    StackPane.setMargin(success, new javafx.geometry.Insets(0, 24, 24, 24));

                    PauseTransition pause = new PauseTransition(Duration.millis(850));
                    pause.setOnFinished(e -> {
                        root.getChildren().remove(success);
                        sceneManager.showLogin();
                    });
                    pause.play();
                },
                error -> {
                    if (error instanceof AuthService.DuplicateAccountException) {
                        showWarning(error.getMessage());
                    } else if (error instanceof AuthService.AuthException authException) {
                        showError(authException.getMessage());
                    } else {
                        showError("We couldn't create the account. Check your connection and try again.");
                    }
                },
                () -> setLoading(false, null, null)
        );
    }

    @FXML
    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            passwordVisibleField.setText(passwordField.getText());
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);
            passwordVisibleField.requestFocus();
            passwordVisibleField.positionCaret(passwordVisibleField.getText().length());
            passwordToggleButton.setText("Hide");
        } else {
            passwordField.setText(passwordVisibleField.getText());
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            passwordField.requestFocus();
            passwordField.positionCaret(passwordField.getText().length());
            passwordToggleButton.setText("Show");
        }
        updatePasswordStrength(passwordVisible ? passwordVisibleField.getText() : passwordField.getText());
    }

    @FXML
    private void handleGoToLogin() {
        sceneManager.showLogin();
    }

    private void setLoading(boolean loading, String title, String message) {
        registerButton.setDisable(loading);
        if (loading) loadingOverlay.show(title, message);
        else loadingOverlay.hide();
    }

    private void clearGeneralMessages() {
        hide(errorLabel);
        hide(warningLabel);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        hide(warningLabel);
        if (!errorLabel.getStyleClass().contains("banner-danger")) errorLabel.getStyleClass().add("banner-danger");
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void showWarning(String message) {
        warningLabel.setText(message);
        hide(errorLabel);
        if (!emailField.getStyleClass().contains("input-error")) {
            emailField.getStyleClass().add("input-error");
        }
        warningLabel.setManaged(true);
        warningLabel.setVisible(true);
    }

    private void showFieldError(TextInputControl field, Label hint, String message) {
        field.getStyleClass().remove("input-success");
        if (!field.getStyleClass().contains("input-error")) field.getStyleClass().add("input-error");
        hint.setText(message);
        hint.getStyleClass().remove("field-message-success");
        hint.getStyleClass().add("field-message-error");
        hint.setManaged(true);
        hint.setVisible(true);
        field.requestFocus();
    }

    private void hide(Label label) {
        label.setManaged(false);
        label.setVisible(false);
    }
}
