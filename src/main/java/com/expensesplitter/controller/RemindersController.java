package com.expensesplitter.controller;

import com.expensesplitter.model.Member;
import com.expensesplitter.model.Reminder;
import com.expensesplitter.service.BalanceService;
import com.expensesplitter.service.DebtSimplificationService;
import com.expensesplitter.util.AppContext;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.*;

public class RemindersController implements Initializable {

    @FXML private Label outstandingDueLabel;
    @FXML private Label dueSubtextLabel;
    @FXML private Label pendingNudgesCountLabel;
    @FXML private Label automationStatusLabel;
    @FXML private VBox notificationBannerBox;
    @FXML private Label notificationBannerText;
    @FXML private VBox remindersListContainer;

    private AppContext appContext;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.appContext = AppContext.getInstance();
        renderReminders();
    }

    private void renderReminders() {
        BalanceService balanceService = appContext.getBalanceService();
        Member currentUser = appContext.getCurrentUser();
        String currentUserId = (currentUser != null) ? currentUser.getId() : "";

        List<DebtSimplificationService.Transfer> transfers = balanceService.computeSimplifiedTransfers(null);
        Map<String, String> memberNames = new HashMap<>();
        for (Member m : appContext.getMemberDao().findAll()) {
            memberNames.put(m.getId(), m.getName());
        }

        long totalOwedToUser = 0;
        int eligibleNudges = 0;
        remindersListContainer.getChildren().clear();

        for (DebtSimplificationService.Transfer t : transfers) {
            if (t.getToMemberId().equals(currentUserId)) {
                String key = t.getFromMemberId() + "_" + t.getToMemberId() + "_" + t.getAmountCents();
                if (appContext.getReminderService().isSnoozedOrDismissed(key)) {
                    continue;
                }
                totalOwedToUser += t.getAmountCents();
                eligibleNudges++;

                HBox row = createReminderRow(t, memberNames.getOrDefault(t.getFromMemberId(), "Member"), key);
                remindersListContainer.getChildren().add(row);
            }
        }

        outstandingDueLabel.setText(getCurrencySymbol() + formatCurrency(totalOwedToUser));
        dueSubtextLabel.setText(eligibleNudges + " active pending claims");
        pendingNudgesCountLabel.setText(String.valueOf(eligibleNudges));

        if (eligibleNudges == 0) {
            VBox emptyBox = new VBox(12);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new javafx.geometry.Insets(32));
            emptyBox.getStyleClass().add("card");
            Label emptyTitle = new Label("No pending reminders");
            emptyTitle.getStyleClass().add("text-section");
            Label emptySub = new Label("You have no outstanding dues waiting to be settled.");
            emptySub.getStyleClass().add("text-secondary");
            emptyBox.getChildren().addAll(emptyTitle, emptySub);
            remindersListContainer.getChildren().add(emptyBox);
        }
    }

    private HBox createReminderRow(DebtSimplificationService.Transfer t, String debtorName, String transferKey) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("card");

        HBox av = new HBox();
        av.getStyleClass().add("avatar-circle");
        Label in = new Label(getInitials(debtorName));
        in.getStyleClass().add("avatar-initials");
        av.getChildren().add(in);

        VBox textCol = new VBox(2);
        Label name = new Label(debtorName);
        name.getStyleClass().add("text-label");
        Label note = new Label("Outstanding shared expense claim");
        note.getStyleClass().add("text-caption");
        textCol.getChildren().addAll(name, note);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label amt = new Label(getCurrencySymbol() + formatCurrency(t.getAmountCents()));
        amt.getStyleClass().addAll("text-mono-large", "text-positive");

        Button snoozeBtn = new Button("Snooze");
        snoozeBtn.getStyleClass().add("btn-secondary");
        snoozeBtn.setOnAction(e -> {
            appContext.getReminderService().snooze(transferKey, 24);
            showNotificationToast("Reminder for " + debtorName + " snoozed for 24 hours.");
            renderReminders();
        });

        Button dismissBtn = new Button("Dismiss");
        dismissBtn.getStyleClass().add("btn-secondary");
        dismissBtn.setOnAction(e -> {
            appContext.getReminderService().dismiss(transferKey);
            showNotificationToast("Reminder for " + debtorName + " dismissed.");
            renderReminders();
        });

        Button nudgeBtn = new Button("Send nudge");
        nudgeBtn.getStyleClass().add("btn-primary");
        nudgeBtn.setOnAction(e -> {
            showNotificationToast("Nudge dispatched to " + debtorName + " for " + getCurrencySymbol() + formatCurrency(t.getAmountCents()) + ".");
            nudgeBtn.setText("Sent");
            nudgeBtn.setDisable(true);
        });

        row.getChildren().addAll(av, textCol, sp, amt, snoozeBtn, dismissBtn, nudgeBtn);
        return row;
    }

    @FXML
    private void onSendAllReminders() {
        Member currentUser = appContext.getCurrentUser();
        String currentUserId = (currentUser != null) ? currentUser.getId() : "";
        List<DebtSimplificationService.Transfer> transfers = appContext.getBalanceService().computeSimplifiedTransfers(null);
        int sentCount = 0;
        long totalSent = 0;
        for (DebtSimplificationService.Transfer t : transfers) {
            if (t.getToMemberId().equals(currentUserId)) {
                String key = t.getFromMemberId() + "_" + t.getToMemberId() + "_" + t.getAmountCents();
                if (!appContext.getReminderService().isSnoozedOrDismissed(key)) {
                    sentCount++;
                    totalSent += t.getAmountCents();
                }
            }
        }
        if (sentCount > 0) {
            showNotificationToast("Dispatched reminders for " + sentCount + " pending claims (" + getCurrencySymbol() + formatCurrency(totalSent) + ") successfully.");
        } else {
            showNotificationToast("No pending reminders to dispatch.");
        }
    }

    private String getCurrencySymbol() {
        return appContext.getSettingsDao().get("default_currency")
            .map(c -> c.contains("USD") ? "$" : (c.contains("EUR") ? "€" : (c.contains("GBP") ? "£" : "₹")))
            .orElse("₹");
    }

    private void showNotificationToast(String message) {
        notificationBannerText.setText(message);
        notificationBannerBox.setVisible(true);
        notificationBannerBox.setManaged(true);

        PauseTransition hideTimer = new PauseTransition(Duration.seconds(3));
        hideTimer.setOnFinished(e -> {
            notificationBannerBox.setVisible(false);
            notificationBannerBox.setManaged(false);
        });
        hideTimer.play();
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    private String formatCurrency(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
    }
}

