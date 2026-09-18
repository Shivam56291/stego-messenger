package com.stegomsg.ui.controllers;

import com.stegomsg.model.User;
import com.stegomsg.service.AuthService;
import com.stegomsg.ui.SceneManager;
import com.stegomsg.util.Validation;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;

import java.util.Optional;

public final class LoginController {

    private final SceneManager sceneManager;

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    public LoginController(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    @FXML
    private void handleLogin() {
        clearError();
        try {
            AuthService auth = sceneManager.services().authService;
            User user = auth.login(emailField.getText(), passwordField.getText().toCharArray());
            passwordField.clear();
            sceneManager.showDashboard();
        } catch (AuthService.AuthException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleQuickPinLogin() {
        clearError();
        if (emailField.getText() == null || emailField.getText().isBlank()) {
            showError("Enter your email first, then use Quick PIN.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Quick PIN Login");
        dialog.setHeaderText("Enter your 6-digit PIN");
        dialog.setContentText("PIN:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(pin -> {
            if (!Validation.isValidPin(pin)) {
                showError("PIN must be exactly 6 digits.");
                return;
            }
            try {
                sceneManager.services().authService.quickLogin(emailField.getText(), pin.toCharArray());
                sceneManager.showDashboard();
            } catch (AuthService.AuthException e) {
                showError(e.getMessage());
            }
        });
    }

    @FXML
    private void handleGoToRegister() {
        sceneManager.showRegister();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void clearError() {
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }
}
