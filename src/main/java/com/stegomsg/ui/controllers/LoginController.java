package com.stegomsg.ui.controllers;

import com.stegomsg.model.User;
import com.stegomsg.service.AuthService;
import com.stegomsg.ui.LoadingOverlay;
import com.stegomsg.ui.SceneManager;
import com.stegomsg.ui.UiIcon;
import com.stegomsg.ui.UiTaskRunner;
import com.stegomsg.util.Validation;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.prefs.Preferences;

public final class LoginController {

    private static final String PREF_REMEMBERED_EMAIL = "rememberedEmail";

    private final SceneManager sceneManager;
    private final Preferences preferences = Preferences.userNodeForPackage(LoginController.class);

    @FXML private StackPane root;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private Button passwordToggleButton;
    @FXML private CheckBox rememberEmailCheck;
    @FXML private Button loginButton;
    @FXML private Button quickPinButton;
    @FXML private Label errorLabel;
    @FXML private Label emailHint;

    private LoadingOverlay loadingOverlay;
    private boolean passwordVisible;

    public LoginController(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    @FXML
    private void initialize() {
        loadingOverlay = new LoadingOverlay();
        root.getChildren().add(loadingOverlay);
        errorLabel.getStyleClass().add("banner-danger");
        loginButton.getStyleClass().add("button-large");
        quickPinButton.getStyleClass().add("button-large");

        passwordField.textProperty().addListener((obs, old, value) -> {
            if (!passwordVisible && passwordVisibleField != null) {
                passwordVisibleField.setText(value);
            }
            clearError();
        });

        passwordVisibleField.textProperty().addListener((obs, old, value) -> {
            if (passwordVisible) {
                passwordField.setText(value);
            }
            clearError();
        });

        emailField.textProperty().addListener((obs, old, value) -> {
            clearError();
            clearFieldMessage(emailHint);
            emailField.getStyleClass().remove("input-error");
        });

        rememberEmailCheck.setSelected(!preferences.get(PREF_REMEMBERED_EMAIL, "").isBlank());
        String remembered = preferences.get(PREF_REMEMBERED_EMAIL, "");
        if (!remembered.isBlank()) {
            emailField.setText(remembered);
            passwordField.requestFocus();
        }

        loginButton.setTooltip(new Tooltip("Sign in with your account password"));
        quickPinButton.setTooltip(new Tooltip("Use the 6-digit PIN configured for this device"));
        passwordToggleButton.setTooltip(new Tooltip("Show or hide the password"));
    }

    @FXML
    private void handleLogin() {
        clearError();

        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (!Validation.isValidEmail(email)) {
            showFieldError(emailField, emailHint, "Enter a valid email address.");
            return;
        }
        if (password.isBlank()) {
            showError("Enter your password to continue.");
            passwordField.requestFocus();
            return;
        }

        UiTaskRunner.run(
                () -> sceneManager.services().authService.login(email, password.toCharArray()),
                () -> setLoading(true, "Signing you in…", "Checking your account securely. This may take a moment."),
                user -> {
                    passwordField.clear();
                    passwordVisibleField.clear();
                    persistEmail(email);
                    sceneManager.showDashboard();
                },
                error -> showError(userFriendlyError(error)),
                () -> setLoading(false, null, null)
        );
    }

    @FXML
    private void handleQuickPinLogin() {
        clearError();

        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        if (!Validation.isValidEmail(email)) {
            showFieldError(emailField, emailHint, "Enter your account email first.");
            return;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Quick PIN");
        dialog.setHeaderText("Unlock this device");
        dialog.setContentText(null);

        TextField pinField = new TextField();
        pinField.setPromptText("6-digit PIN");
        pinField.setMaxWidth(Double.MAX_VALUE);
        pinField.textProperty().addListener((obs, old, value) -> {
            if (!value.matches("\\d{0,6}")) {
                pinField.setText(value.replaceAll("\\D", "").substring(0, Math.min(6, value.replaceAll("\\D", "").length())));
            }
        });

        Label helper = new Label("Your Quick PIN is local to this device.");
        helper.getStyleClass().add("dialog-helper");

        VBox content = new VBox(10, pinField, helper);
        content.getStyleClass().add("dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/com/stegomsg/css/theme-dark.css").toExternalForm());

        ButtonType unlockType = new ButtonType("Unlock", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(unlockType, ButtonType.CANCEL);

        dialog.setResultConverter(button -> button == unlockType ? pinField.getText() : null);
        // Validation is performed when the user presses Unlock so the dialog stays simple and fast.
        dialog.getDialogPane().lookupButton(unlockType).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!Validation.isValidPin(pinField.getText())) {
                helper.setText("PIN must be exactly 6 digits.");
                helper.getStyleClass().removeAll("status-safe", "status-danger");
                helper.getStyleClass().add("status-danger");
                event.consume();
            }
        });

        pinField.requestFocus();
        dialog.showAndWait().ifPresent(pin -> {
            UiTaskRunner.run(
                    () -> sceneManager.services().authService.quickLogin(email, pin.toCharArray()),
                    () -> setLoading(true, "Unlocking your session…", "Verifying your device PIN."),
                    user -> {
                        persistEmail(email);
                        sceneManager.showDashboard();
                    },
                    error -> showError(error instanceof AuthService.AuthException
                            ? error.getMessage()
                            : "We couldn't unlock this session. Please try again."),
                    () -> setLoading(false, null, null)
            );
        });
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
    }

    @FXML
    private void handleGoToRegister() {
        sceneManager.showRegister();
    }

    private void persistEmail(String email) {
        if (rememberEmailCheck.isSelected()) {
            preferences.put(PREF_REMEMBERED_EMAIL, email);
        } else {
            preferences.remove(PREF_REMEMBERED_EMAIL);
        }
    }

    private void setLoading(boolean loading, String title, String message) {
        if (loading) {
            loginButton.setDisable(true);
            quickPinButton.setDisable(true);
            loadingOverlay.show(title, message);
        } else {
            loginButton.setDisable(false);
            quickPinButton.setDisable(false);
            loadingOverlay.hide();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.getStyleClass().removeAll("banner-success", "banner-warning");
        if (!errorLabel.getStyleClass().contains("banner-danger")) {
            errorLabel.getStyleClass().add("banner-danger");
        }
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
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

    private void clearError() {
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    private void clearFieldMessage(Label label) {
        label.setManaged(false);
        label.setVisible(false);
    }

    private String userFriendlyError(Throwable error) {
        if (error instanceof AuthService.AuthException authException) {
            return authException.getMessage();
        }
        return "We couldn't complete the sign-in request. Check your connection and try again.";
    }
}
