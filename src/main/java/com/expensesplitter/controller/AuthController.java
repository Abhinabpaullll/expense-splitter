package com.expensesplitter.controller;

import com.expensesplitter.model.User;
import com.expensesplitter.service.ViewType;
import com.expensesplitter.util.AppContext;
import com.expensesplitter.util.PasswordUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.UUID;

public class AuthController implements Initializable, MainController.ParamReceiver {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @FXML private Label subtitleLabel;
    @FXML private Label errorMessageLabel;

    @FXML private VBox signInForm;
    @FXML private ComboBox<UserItem> accountPickerComboBox;
    @FXML private PasswordField signInPasswordField;
    @FXML private Button signInBtn;

    @FXML private VBox registerForm;
    @FXML private TextField registerNameField;
    @FXML private TextField registerEmailField;
    @FXML private PasswordField registerPasswordField;
    @FXML private PasswordField registerConfirmPasswordField;
    @FXML private Button registerBtn;
    @FXML private HBox backToSignInBox;

    private AppContext appContext;

    public record UserItem(String id, String displayName, String email) {
        @Override
        public String toString() {
            if (email != null && !email.isBlank()) {
                return displayName + " (" + email + ")";
            }
            return displayName;
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.appContext = AppContext.getInstance();
        refreshAccountList();
    }

    @Override
    public void setNavigationParameter(Object param) {
        if ("register".equals(param)) {
            onSwitchToRegister();
        } else {
            refreshAccountList();
        }
    }

    private void refreshAccountList() {
        List<User> users = appContext.getUserDao().findAll();
        if (users.isEmpty()) {
            onSwitchToRegister();
            backToSignInBox.setVisible(false);
            backToSignInBox.setManaged(false);
        } else {
            backToSignInBox.setVisible(true);
            backToSignInBox.setManaged(true);
            List<UserItem> items = users.stream()
                .map(u -> new UserItem(u.id(), u.displayName(), u.email()))
                .toList();
            accountPickerComboBox.setItems(FXCollections.observableArrayList(items));
            accountPickerComboBox.getSelectionModel().selectFirst();
            onSwitchToSignIn();
        }
    }

    @FXML
    private void onSwitchToRegister() {
        clearError();
        signInForm.setVisible(false);
        signInForm.setManaged(false);
        registerForm.setVisible(true);
        registerForm.setManaged(true);
        subtitleLabel.setText("Create an offline account to manage your shared ledgers");
        registerNameField.requestFocus();
    }

    @FXML
    private void onSwitchToSignIn() {
        clearError();
        registerForm.setVisible(false);
        registerForm.setManaged(false);
        signInForm.setVisible(true);
        signInForm.setManaged(true);
        subtitleLabel.setText("Sign in to your shared expense workspace");
        signInPasswordField.requestFocus();
    }

    @FXML
    private void onSignIn() {
        clearError();
        UserItem selected = accountPickerComboBox.getValue();
        if (selected == null) {
            showError("Please select an account.");
            return;
        }

        String password = signInPasswordField.getText();
        if (password == null || password.isEmpty()) {
            showError("Password cannot be blank.");
            return;
        }

        User user = appContext.getUserDao().findById(selected.id()).orElse(null);
        if (user == null) {
            showError("Selected account not found.");
            return;
        }

        if (!PasswordUtil.verifyPassword(password, user.salt(), user.passwordHash())) {
            showError("Invalid password. Please try again.");
            return;
        }

        // Establish session and route to Dashboard
        appContext.getSessionManager().setCurrentUser(user);
        appContext.getNavigationService().navigateTo(ViewType.DASHBOARD);
    }

    @FXML
    private void onRegister() {
        clearError();
        String name = registerNameField.getText() != null ? registerNameField.getText().trim() : "";
        String email = registerEmailField.getText() != null ? registerEmailField.getText().trim() : "";
        String password = registerPasswordField.getText();
        String confirm = registerConfirmPasswordField.getText();

        if (name.isBlank()) {
            showError("Full name is required.");
            registerNameField.requestFocus();
            return;
        }

        if (password == null || password.length() < 6) {
            showError("Password must be at least 6 characters.");
            registerPasswordField.requestFocus();
            return;
        }

        if (!password.equals(confirm)) {
            showError("Passwords do not match.");
            registerConfirmPasswordField.requestFocus();
            return;
        }

        if (!email.isBlank()) {
            if (appContext.getUserDao().findByEmail(email).isPresent()) {
                showError("An account with this email address already exists.");
                return;
            }
        }

        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(password, salt);
        String userId = UUID.randomUUID().toString();

        User newUser = new User(userId, name, email.isBlank() ? null : email, hash, salt, System.currentTimeMillis(), System.currentTimeMillis());
        appContext.getUserDao().save(newUser);
        logger.info("New user account registered: {} ({})", name, userId);

        // Sign in immediately
        appContext.getSessionManager().setCurrentUser(newUser);
        appContext.getNavigationService().navigateTo(ViewType.DASHBOARD);
    }

    private void showError(String message) {
        errorMessageLabel.setText(message);
        errorMessageLabel.setVisible(true);
        errorMessageLabel.setManaged(true);
    }

    private void clearError() {
        errorMessageLabel.setText("");
        errorMessageLabel.setVisible(false);
        errorMessageLabel.setManaged(false);
    }
}

