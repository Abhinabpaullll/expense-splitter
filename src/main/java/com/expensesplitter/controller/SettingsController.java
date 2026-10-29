package com.expensesplitter.controller;

import com.expensesplitter.model.User;
import com.expensesplitter.service.ViewType;
import com.expensesplitter.util.AppContext;
import com.expensesplitter.util.PasswordUtil;
import com.expensesplitter.util.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class SettingsController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    @FXML private RadioButton lightThemeRadio;
    @FXML private RadioButton darkThemeRadio;
    @FXML private ComboBox<String> defaultCurrencyBox;
    @FXML private ComboBox<String> reminderFrequencyBox;
    @FXML private VBox settingsFeedbackBox;
    @FXML private Label settingsFeedbackText;

    @FXML private TextField accountDisplayNameField;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;

    private AppContext appContext;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.appContext = AppContext.getInstance();

        // Appearance
        if (ThemeManager.isDarkMode()) {
            darkThemeRadio.setSelected(true);
        } else {
            lightThemeRadio.setSelected(true);
        }

        // Currency
        defaultCurrencyBox.getItems().addAll("INR (₹)", "USD ($)", "EUR (€)", "GBP (£)");
        String savedCurrency = appContext.getSettingsDao().get("default_currency").orElse("INR (₹)");
        defaultCurrencyBox.setValue(savedCurrency);
        defaultCurrencyBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                appContext.getSettingsDao().set("default_currency", newV);
                showFeedback("Default currency updated to " + newV);
            }
        });

        // Reminders Frequency
        reminderFrequencyBox.getItems().addAll("Every 2 hours", "Every 4 hours", "Daily", "On application start only");
        String savedFreq = appContext.getSettingsDao().get("reminder_frequency_hours").orElse("Every 4 hours");
        reminderFrequencyBox.setValue(savedFreq);
        reminderFrequencyBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                appContext.getSettingsDao().set("reminder_frequency_hours", newV);
                long hours = parseFrequencyHours(newV);
                if (hours > 0) {
                    appContext.getReminderService().startScheduler(hours);
                }
                showFeedback("Reminder check frequency set to " + newV);
            }
        });

        // Account management
        User user = appContext.getCurrentAccountUser();
        if (user != null) {
            accountDisplayNameField.setText(user.displayName());
        }
    }

    private long parseFrequencyHours(String freqText) {
        if (freqText.contains("2 hours")) return 2;
        if (freqText.contains("4 hours")) return 4;
        if (freqText.contains("Daily")) return 24;
        return 0; // on start only
    }

    @FXML
    private void onSelectLightTheme() {
        ThemeManager.setTheme(ThemeManager.AppTheme.LIGHT, lightThemeRadio.getScene());
        appContext.getSettingsDao().set("theme", "LIGHT");
        showFeedback("Switched to Light Canvas theme.");
    }

    @FXML
    private void onSelectDarkTheme() {
        ThemeManager.setTheme(ThemeManager.AppTheme.DARK, darkThemeRadio.getScene());
        appContext.getSettingsDao().set("theme", "DARK");
        showFeedback("Switched to Dark Slate theme.");
    }

    @FXML
    private void onSaveDisplayName() {
        String newName = accountDisplayNameField.getText() != null ? accountDisplayNameField.getText().trim() : "";
        if (newName.isBlank()) {
            showFeedback("Display name cannot be empty.");
            return;
        }

        User user = appContext.getCurrentAccountUser();
        if (user == null) {
            showFeedback("No authenticated user session.");
            return;
        }

        User updatedUser = new User(user.id(), newName, user.email(), user.passwordHash(), user.salt(), user.createdAt(), user.lastLoginAt());
        appContext.getUserDao().update(updatedUser);
        appContext.getSessionManager().setCurrentUser(updatedUser);
        showFeedback("Profile name updated successfully.");
    }

    @FXML
    private void onUpdatePassword() {
        User user = appContext.getCurrentAccountUser();
        if (user == null) {
            showFeedback("No authenticated user session.");
            return;
        }

        String currentPass = currentPasswordField.getText();
        String newPass = newPasswordField.getText();
        String confirmPass = confirmPasswordField.getText();

        if (currentPass == null || currentPass.isBlank()) {
            showFeedback("Current password is required.");
            return;
        }

        if (!PasswordUtil.verifyPassword(currentPass, user.salt(), user.passwordHash())) {
            showFeedback("Current password is incorrect.");
            return;
        }

        if (newPass == null || newPass.length() < 6) {
            showFeedback("New password must be at least 6 characters.");
            return;
        }

        if (!newPass.equals(confirmPass)) {
            showFeedback("New passwords do not match.");
            return;
        }

        String newSalt = PasswordUtil.generateSalt();
        String newHash = PasswordUtil.hashPassword(newPass, newSalt);

        User updatedUser = new User(user.id(), user.displayName(), user.email(), newHash, newSalt, user.createdAt(), user.lastLoginAt());
        appContext.getUserDao().update(updatedUser);
        appContext.getSessionManager().setCurrentUser(updatedUser);

        currentPasswordField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();

        showFeedback("Password changed successfully.");
    }

    @FXML
    private void onDeleteAccount() {
        User user = appContext.getCurrentAccountUser();
        if (user == null) {
            showFeedback("No authenticated user session.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Account");
        confirm.setHeaderText("Delete account for " + user.displayName() + "?");
        confirm.setContentText("Your personal sign-in credentials will be removed. All shared group ledgers and expense rows will remain intact.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            appContext.getUserDao().delete(user.id());
            appContext.getSessionManager().signOut();
            appContext.getNavigationService().navigateTo(ViewType.AUTH);
        }
    }

    @FXML
    private void onBackupDatabase() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Backup SQLite Database");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Database (*.db)", "*.db"));
        chooser.setInitialFileName("expense_splitter_backup_" + java.time.LocalDate.now() + ".db");

        File dest = chooser.showSaveDialog(lightThemeRadio.getScene().getWindow());
        if (dest != null) {
            try {
                String dbUrl = appContext.getDatabaseManager().getJdbcUrl();
                String path = dbUrl.replace("jdbc:sqlite:", "");
                File src = new File(path);
                if (src.exists()) {
                    copyFile(src, dest);
                    showFeedback("Backup saved to " + dest.getAbsolutePath());
                } else {
                    showFeedback("Database file not found at " + path);
                }
            } catch (Exception e) {
                logger.error("Database backup failed", e);
                showFeedback("Backup failed: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onRestoreDatabase() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Restore SQLite Database");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Database (*.db)", "*.db"));

        File src = chooser.showOpenDialog(lightThemeRadio.getScene().getWindow());
        if (src != null) {
            try {
                String dbUrl = appContext.getDatabaseManager().getJdbcUrl();
                String path = dbUrl.replace("jdbc:sqlite:", "");
                File dest = new File(path);
                copyFile(src, dest);
                showFeedback("Database restored from backup. Please restart the application.");
            } catch (Exception e) {
                logger.error("Database restore failed", e);
                showFeedback("Restore failed: " + e.getMessage());
            }
        }
    }

    private void copyFile(File src, File dest) throws Exception {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int length;
            while ((length = in.read(buf)) > 0) {
                out.write(buf, 0, length);
            }
        }
    }

    private void showFeedback(String message) {
        settingsFeedbackText.setText(message);
        settingsFeedbackBox.setVisible(true);
        settingsFeedbackBox.setManaged(true);

        PauseTransition pause = new PauseTransition(Duration.seconds(3.5));
        pause.setOnFinished(e -> {
            settingsFeedbackBox.setVisible(false);
            settingsFeedbackBox.setManaged(false);
        });
        pause.play();
    }
}
