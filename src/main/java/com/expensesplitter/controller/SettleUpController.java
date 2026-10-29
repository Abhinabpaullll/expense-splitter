package com.expensesplitter.controller;

import com.expensesplitter.model.Group;
import com.expensesplitter.model.Member;
import com.expensesplitter.model.Payment;
import com.expensesplitter.model.PaymentMethod;
import com.expensesplitter.service.BalanceService;
import com.expensesplitter.service.DebtSimplificationService;
import com.expensesplitter.service.GroupService;
import com.expensesplitter.util.AppContext;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;

public class SettleUpController implements Initializable, MainController.ParamReceiver {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SettleUpController.class);

    @FXML private ComboBox<GroupItem> scopeGroupComboBox;
    @FXML private Label graphCompressionLabel;
    @FXML private Label totalResidualLabel;
    @FXML private Label pendingTransfersCountLabel;
    @FXML private Label youReceiveLabel;
    @FXML private Label youOweLabel;
    @FXML private Label settlementsCountBadge;
    @FXML private VBox settlementListContainer;
    @FXML private VBox allSettledBox;

    private AppContext appContext;
    private Group selectedGroup;

    public record GroupItem(String id, String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.appContext = AppContext.getInstance();
        setupScopeDropdown();
    }

    @Override
    public void setNavigationParameter(Object param) {
        if (param instanceof Group g) {
            this.selectedGroup = g;
            for (GroupItem item : scopeGroupComboBox.getItems()) {
                if (item.id().equals(g.getId())) {
                    scopeGroupComboBox.setValue(item);
                    break;
                }
            }
        }
        renderSettlementData();
    }

    private void setupScopeDropdown() {
        scopeGroupComboBox.getItems().clear();
        scopeGroupComboBox.getItems().add(new GroupItem("", "All Groups (Consolidated)"));

        for (Group g : appContext.getGroupService().getAllGroups()) {
            scopeGroupComboBox.getItems().add(new GroupItem(g.getId(), g.getName()));
        }

        scopeGroupComboBox.getSelectionModel().selectFirst();
        scopeGroupComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                if (newVal.id().isEmpty()) {
                    this.selectedGroup = null;
                } else {
                    this.selectedGroup = appContext.getGroupService().getGroupById(newVal.id()).orElse(null);
                }
                renderSettlementData();
            }
        });
    }

    private void renderSettlementData() {
        BalanceService balanceService = appContext.getBalanceService();
        Member currentUser = appContext.getCurrentUser();
        String currentUserId = (currentUser != null) ? currentUser.getId() : "";
        String groupId = (selectedGroup != null) ? selectedGroup.getId() : null;
        String currencySymbol = (selectedGroup != null) ? selectedGroup.getCurrencySymbol() : "₹";

        // Calculate simplified transfers
        List<DebtSimplificationService.Transfer> transfers = balanceService.computeSimplifiedTransfers(groupId);

        long totalResidual = 0;
        long youReceive = 0;
        long youOwe = 0;

        for (DebtSimplificationService.Transfer t : transfers) {
            totalResidual += t.getAmountCents();
            if (t.getToMemberId().equals(currentUserId)) {
                youReceive += t.getAmountCents();
            }
            if (t.getFromMemberId().equals(currentUserId)) {
                youOwe += t.getAmountCents();
            }
        }

        totalResidualLabel.setText(currencySymbol + formatCurrency(totalResidual));
        pendingTransfersCountLabel.setText(transfers.size() + " pending transfers");
        youReceiveLabel.setText("+" + currencySymbol + formatCurrency(youReceive));
        youOweLabel.setText("-" + currencySymbol + formatCurrency(youOwe));
        settlementsCountBadge.setText(transfers.size() + " transactions");

        graphCompressionLabel.setText(
            String.format("All bilateral balances compressed into %d multi-party transfers.", transfers.size())
        );

        settlementListContainer.getChildren().clear();

        if (transfers.isEmpty()) {
            allSettledBox.setVisible(true);
            allSettledBox.setManaged(true);
            return;
        } else {
            allSettledBox.setVisible(false);
            allSettledBox.setManaged(false);
        }

        Map<String, String> memberNames = new HashMap<>();
        for (Member m : appContext.getMemberDao().findAll()) {
            memberNames.put(m.getId(), m.getName());
        }

        for (DebtSimplificationService.Transfer t : transfers) {
            HBox row = createSettlementRow(t, memberNames, currencySymbol);
            settlementListContainer.getChildren().add(row);
        }
    }

    private HBox createSettlementRow(DebtSimplificationService.Transfer t, Map<String, String> memberNames, String currencySymbol) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("card");

        String fromName = memberNames.getOrDefault(t.getFromMemberId(), "Member");
        String toName = memberNames.getOrDefault(t.getToMemberId(), "Member");

        // From avatar
        HBox avFrom = new HBox();
        avFrom.getStyleClass().add("avatar-circle");
        Label inFrom = new Label(getInitials(fromName));
        inFrom.getStyleClass().add("avatar-initials");
        avFrom.getChildren().add(inFrom);

        Label arrow = new Label("→");
        arrow.getStyleClass().addAll("text-label", "text-secondary");

        // To avatar
        HBox avTo = new HBox();
        avTo.getStyleClass().add("avatar-circle");
        Label inTo = new Label(getInitials(toName));
        inTo.getStyleClass().add("avatar-initials");
        avTo.getChildren().add(inTo);

        VBox textCol = new VBox(2);
        Label l = new Label(fromName + " pays " + toName);
        l.getStyleClass().add("text-label");
        Label note = new Label("Optimal bilateral settlement transfer");
        note.getStyleClass().add("text-caption");
        textCol.getChildren().addAll(l, note);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label amt = new Label(currencySymbol + formatCurrency(t.getAmountCents()));
        amt.getStyleClass().addAll("text-mono-large", "text-positive");

        Button settleBtn = new Button("Record settle");
        settleBtn.getStyleClass().add("btn-primary");
        settleBtn.setOnAction(e -> executeDirectSettlement(t));

        row.getChildren().addAll(avFrom, arrow, avTo, textCol, sp, amt, settleBtn);
        return row;
    }

    private void executeDirectSettlement(DebtSimplificationService.Transfer t) {
        String targetGroupId = (selectedGroup != null) ? selectedGroup.getId() : "";
        if (targetGroupId.isBlank()) {
            List<Group> allGroups = appContext.getGroupService().getAllGroups();
            for (Group g : allGroups) {
                List<Member> members = appContext.getGroupService().getGroupMembers(g.getId());
                boolean hasFrom = members.stream().anyMatch(m -> m.getId().equals(t.getFromMemberId()));
                boolean hasTo = members.stream().anyMatch(m -> m.getId().equals(t.getToMemberId()));
                if (hasFrom && hasTo) {
                    targetGroupId = g.getId();
                    break;
                }
            }
            if (targetGroupId.isBlank() && !allGroups.isEmpty()) {
                targetGroupId = allGroups.get(0).getId();
            }
        }

        Payment payment = new Payment(
            UUID.randomUUID().toString(),
            targetGroupId,
            t.getFromMemberId(),
            t.getToMemberId(),
            t.getAmountCents(),
            PaymentMethod.UPI,
            LocalDate.now(),
            "Settlement via greedy flow"
        );
        appContext.getPaymentDao().save(payment);
        renderSettlementData();
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

    @FXML
    private void onRecordPayment() {
        Dialog<Payment> dialog = new Dialog<>();
        dialog.setTitle("Record Payment");
        dialog.setHeaderText("Record a direct settlement payment");

        ButtonType saveBtnType = new ButtonType("Save payment", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        VBox form = new VBox(12);
        form.setPadding(new javafx.geometry.Insets(16));

        List<Member> allMembers = appContext.getMemberDao().findAll();

        Label fromLabel = new Label("Payer (From)");
        fromLabel.getStyleClass().add("form-label");
        ComboBox<Member> fromBox = new ComboBox<>(FXCollections.observableArrayList(allMembers));
        fromBox.getSelectionModel().selectFirst();

        Label toLabel = new Label("Payee (To)");
        toLabel.getStyleClass().add("form-label");
        ComboBox<Member> toBox = new ComboBox<>(FXCollections.observableArrayList(allMembers));
        if (allMembers.size() > 1) toBox.getSelectionModel().select(1);

        Label amtLabel = new Label("Amount");
        amtLabel.getStyleClass().add("form-label");
        TextField amtField = new TextField("500.00");

        Label methodLabel = new Label("Payment Method");
        methodLabel.getStyleClass().add("form-label");
        ComboBox<PaymentMethod> methodBox = new ComboBox<>(FXCollections.observableArrayList(PaymentMethod.values()));
        methodBox.setValue(PaymentMethod.UPI);

        form.getChildren().addAll(fromLabel, fromBox, toLabel, toBox, amtLabel, amtField, methodLabel, methodBox);
        dialog.getDialogPane().setContent(form);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtnType && fromBox.getValue() != null && toBox.getValue() != null) {
                try {
                    BigDecimal d = new BigDecimal(amtField.getText().trim());
                    long cents = d.multiply(BigDecimal.valueOf(100)).longValue();
                    String gId = (selectedGroup != null) ? selectedGroup.getId() : "";
                    if (gId.isBlank()) {
                        List<Group> allGroups = appContext.getGroupService().getAllGroups();
                        if (!allGroups.isEmpty()) {
                            gId = allGroups.get(0).getId();
                        }
                    }
                    return new Payment(
                        UUID.randomUUID().toString(),
                        gId,
                        fromBox.getValue().getId(),
                        toBox.getValue().getId(),
                        cents,
                        methodBox.getValue(),
                        LocalDate.now(),
                        "Manual settlement"
                    );
                } catch (Exception ex) {
                    logger.warn("Failed to parse settlement amount: {}", ex.getMessage());
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(p -> {
            appContext.getPaymentDao().save(p);
            renderSettlementData();
        });
    }
}
