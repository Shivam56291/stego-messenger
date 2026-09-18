package com.stegomsg.ui.controllers;

import com.stegomsg.service.AuthService;
import com.stegomsg.ui.SceneManager;
import com.stegomsg.util.Validation;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;

public final class RegisterController {

    private final SceneManager sceneManager;

    @FXML private TextField emailField;
    @FXML private TextField aliasField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ProgressBar strengthBar;
    @FXML private Label strengthLabel;
    @FXML private Label errorLabel;

    public RegisterController(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    @FXML
    private void handlePasswordTyped() {
        Validation.PasswordStrength strength = Validation.assessPassword(passwordField.getText());
        strengthBar.setProgress(strength.score() / 4.0);
        strengthLabel.setText(strength.feedback());
    }

    @FXML
    private void handleRegister() {
        clearError();
        if (!passwordField.getText().equals(confirmPasswordField.getText())) {
            showError("Passwords do not match.");
            return;
        }
        try {
            sceneManager.services().authService.register(
                    emailField.getText(),
                    passwordField.getText().toCharArray(),
                    aliasField.getText() == null || aliasField.getText().isBlank() ? null : aliasField.getText().trim()
            );
            passwordField.clear();
            confirmPasswordField.clear();
            sceneManager.showLogin();
        } catch (AuthService.AuthException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleGoToLogin() {
        sceneManager.showLogin();
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
