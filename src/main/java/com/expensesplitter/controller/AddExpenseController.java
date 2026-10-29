package com.expensesplitter.controller;

import com.expensesplitter.model.*;
import com.expensesplitter.service.SplitCalculationService;
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

public class AddExpenseController implements Initializable, MainController.ModalController, MainController.ParamReceiver {

    @FXML private TextField titleField;
    @FXML private TextField amountField;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<Group> groupComboBox;
    @FXML private ComboBox<Member> payerComboBox;
    @FXML private ComboBox<String> categoryComboBox;
    @FXML private TextField notesField;

    @FXML private Button splitEqualBtn;
    @FXML private Button splitExactBtn;
    @FXML private Button splitPercentBtn;
    @FXML private Button splitSharesBtn;

    @FXML private VBox memberAllocationContainer;
    @FXML private Label verificationStatusLabel;
    @FXML private Label verificationSummaryLabel;
    @FXML private ProgressBar splitProgressBar;
    @FXML private Label discrepancyErrorLabel;
    @FXML private Button saveExpenseBtn;

    private MainController mainController;
    private AppContext appContext;
    private SplitType currentSplitType = SplitType.EQUAL;
    private Expense editingExpense = null;

    private final Map<String, CheckBox> memberCheckBoxes = new LinkedHashMap<>();
    private final Map<String, TextField> memberInputFields = new LinkedHashMap<>();
    private final Map<String, Label> memberShareLabels = new LinkedHashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.appContext = AppContext.getInstance();
        datePicker.setValue(LocalDate.now());

        categoryComboBox.getItems().addAll("Groceries", "Utilities", "Dining", "Household", "Maintenance", "Travel", "General");
        categoryComboBox.setValue("Groceries");

        List<Group> groups = appContext.getGroupService().getAllGroups();
        groupComboBox.setItems(FXCollections.observableArrayList(groups));
        if (!groups.isEmpty()) {
            groupComboBox.setValue(groups.get(0));
            onGroupChanged(groups.get(0));
        }

        groupComboBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                onGroupChanged(newV);
            }
        });

        amountField.textProperty().addListener((obs, oldV, newV) -> recalculateSplits());

        updateSplitButtons(splitEqualBtn);
    }

    @Override
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @Override
    public void setNavigationParameter(Object param) {
        if (param instanceof Group g) {
            groupComboBox.setValue(g);
            onGroupChanged(g);
        } else if (param instanceof Expense exp) {
            this.editingExpense = exp;
            titleField.setText(exp.getTitle());
            amountField.setText(exp.getAmountDecimal().toPlainString());
            datePicker.setValue(exp.getDate());
            if (exp.getCategory() != null) {
                categoryComboBox.setValue(exp.getCategory());
            }
            notesField.setText(exp.getNotes() != null ? exp.getNotes() : "");

            for (Group g : groupComboBox.getItems()) {
                if (g.getId().equals(exp.getGroupId())) {
                    groupComboBox.setValue(g);
                    onGroupChanged(g);
                    break;
                }
            }

            // Find payer
            List<ExpensePayer> payers = appContext.getExpenseService().getPayers(exp.getId());
            if (!payers.isEmpty()) {
                String payerMemberId = payers.get(0).getMemberId();
                for (Member m : payerComboBox.getItems()) {
                    if (m.getId().equals(payerMemberId)) {
                        payerComboBox.setValue(m);
                        break;
                    }
                }
            }

            // Set split type
            switch (exp.getSplitType()) {
                case EQUAL -> updateSplitButtons(splitEqualBtn);
                case EXACT -> updateSplitButtons(splitExactBtn);
                case PERCENT -> updateSplitButtons(splitPercentBtn);
                case SHARES -> updateSplitButtons(splitSharesBtn);
            }
            this.currentSplitType = exp.getSplitType();

            // Pre-fill member splits
            List<ExpenseSplit> splits = appContext.getExpenseService().getSplits(exp.getId());
            Set<String> splitMemberIds = new HashSet<>();
            for (ExpenseSplit s : splits) {
                splitMemberIds.add(s.getMemberId());
                TextField field = memberInputFields.get(s.getMemberId());
                if (field != null && s.getExactParam() != null) {
                    field.setText(s.getExactParam().toPlainString());
                }
            }

            for (Map.Entry<String, CheckBox> entry : memberCheckBoxes.entrySet()) {
                entry.getValue().setSelected(splitMemberIds.contains(entry.getKey()));
            }

            recalculateSplits();
        }
    }

    private void onGroupChanged(Group group) {
        List<Member> members = appContext.getGroupService().getGroupMembers(group.getId());
        payerComboBox.setItems(FXCollections.observableArrayList(members));
        if (!members.isEmpty()) {
            payerComboBox.setValue(members.get(0));
        }

        buildMemberRows(members);
        recalculateSplits();
    }

    private void buildMemberRows(List<Member> members) {
        memberAllocationContainer.getChildren().clear();
        memberCheckBoxes.clear();
        memberInputFields.clear();
        memberShareLabels.clear();

        for (Member m : members) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("card-subtle");
            row.setPrefHeight(38);

            CheckBox cb = new CheckBox(m.getName());
            cb.setSelected(true);
            cb.selectedProperty().addListener((obs, oldV, newV) -> recalculateSplits());
            memberCheckBoxes.put(m.getId(), cb);

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            TextField input = new TextField("1");
            input.setPrefWidth(80);
            input.getStyleClass().add("text-mono");
            input.textProperty().addListener((obs, oldV, newV) -> recalculateSplits());
            memberInputFields.put(m.getId(), input);

            Label shareLabel = new Label("₹0.00");
            shareLabel.getStyleClass().addAll("text-mono", "text-positive");
            shareLabel.setPrefWidth(90);
            shareLabel.setAlignment(Pos.CENTER_RIGHT);
            memberShareLabels.put(m.getId(), shareLabel);

            row.getChildren().addAll(cb, sp, input, shareLabel);
            memberAllocationContainer.getChildren().add(row);
        }

        updateInputsVisibility();
    }

    private void updateInputsVisibility() {
        boolean showInput = (currentSplitType != SplitType.EQUAL);
        for (TextField tf : memberInputFields.values()) {
            tf.setVisible(showInput);
            tf.setManaged(showInput);
        }
    }

    @FXML private void onSelectEqual() { setSplitType(SplitType.EQUAL, splitEqualBtn); }
    @FXML private void onSelectExact() { setSplitType(SplitType.EXACT, splitExactBtn); }
    @FXML private void onSelectPercent() { setSplitType(SplitType.PERCENT, splitPercentBtn); }
    @FXML private void onSelectShares() { setSplitType(SplitType.SHARES, splitSharesBtn); }

    private void setSplitType(SplitType type, Button activeBtn) {
        this.currentSplitType = type;
        updateSplitButtons(activeBtn);
        updateInputsVisibility();

        // Populate default values based on split type
        int activeCount = (int) memberCheckBoxes.values().stream().filter(CheckBox::isSelected).count();
        if (activeCount > 0) {
            for (String mId : memberCheckBoxes.keySet()) {
                if (type == SplitType.PERCENT) {
                    BigDecimal pct = BigDecimal.valueOf(100).divide(BigDecimal.valueOf(activeCount), 2, RoundingMode.HALF_UP);
                    memberInputFields.get(mId).setText(pct.toPlainString());
                } else if (type == SplitType.SHARES) {
                    memberInputFields.get(mId).setText("1");
                } else if (type == SplitType.EXACT) {
                    memberInputFields.get(mId).setText("0.00");
                }
            }
        }
        recalculateSplits();
    }

    private void updateSplitButtons(Button active) {
        splitEqualBtn.getStyleClass().remove("active");
        splitExactBtn.getStyleClass().remove("active");
        splitPercentBtn.getStyleClass().remove("active");
        splitSharesBtn.getStyleClass().remove("active");
        active.getStyleClass().add("active");
    }

    private void recalculateSplits() {
        long totalCents = 0;
        try {
            String text = amountField.getText() != null ? amountField.getText().trim() : "";
            if (!text.isBlank()) {
                BigDecimal amt = new BigDecimal(text);
                totalCents = amt.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
            }
        } catch (NumberFormatException ex) {
            totalCents = 0;
        }

        List<String> activeMemberIds = new ArrayList<>();
        Map<String, BigDecimal> params = new LinkedHashMap<>();

        for (Map.Entry<String, CheckBox> entry : memberCheckBoxes.entrySet()) {
            String mId = entry.getKey();
            if (entry.getValue().isSelected()) {
                activeMemberIds.add(mId);
                String text = memberInputFields.get(mId).getText().trim();
                try {
                    params.put(mId, text.isBlank() ? BigDecimal.ZERO : new BigDecimal(text));
                } catch (NumberFormatException ex) {
                    params.put(mId, BigDecimal.ZERO);
                }
            }
        }

        SplitCalculationService.SplitResult result = appContext.getSplitCalculationService().calculateSplits(
            totalCents, currentSplitType, activeMemberIds, params
        );

        String currencySymbol = (groupComboBox.getValue() != null) ? groupComboBox.getValue().getCurrencySymbol() : "₹";

        if (result.isValid()) {
            for (String mId : memberCheckBoxes.keySet()) {
                if (activeMemberIds.contains(mId)) {
                    long share = result.getShare(mId);
                    memberShareLabels.get(mId).setText(currencySymbol + formatCurrency(share));
                } else {
                    memberShareLabels.get(mId).setText(currencySymbol + "0.00");
                }
            }

            verificationStatusLabel.setText("Balanced distribution");
            verificationStatusLabel.getStyleClass().remove("text-negative");
            verificationSummaryLabel.setText(String.format("Total split: %s of %s", formatCurrency(totalCents), formatCurrency(totalCents)));
            splitProgressBar.setProgress(1.0);
            discrepancyErrorLabel.setVisible(false);
            discrepancyErrorLabel.setManaged(false);
            saveExpenseBtn.setDisable(totalCents <= 0);
        } else {
            verificationStatusLabel.setText("Discrepancy detected");
            if (!verificationStatusLabel.getStyleClass().contains("text-negative")) {
                verificationStatusLabel.getStyleClass().add("text-negative");
            }
            discrepancyErrorLabel.setText(result.getErrorMessage());
            discrepancyErrorLabel.setVisible(true);
            discrepancyErrorLabel.setManaged(true);
            splitProgressBar.setProgress(0.5);
            saveExpenseBtn.setDisable(true);
        }
    }

    private String formatCurrency(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
    }

    @FXML
    private void onSaveExpense() {
        try {
            BigDecimal amt = new BigDecimal(amountField.getText().trim());
            long totalCents = amt.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
            Group group = groupComboBox.getValue();
            Member payer = payerComboBox.getValue();

            if (group == null || payer == null || titleField.getText().isBlank()) {
                return;
            }

            List<ExpensePayer> payers = List.of(new ExpensePayer(UUID.randomUUID().toString(), "", payer.getId(), totalCents));

            List<String> activeMembers = new ArrayList<>();
            Map<String, BigDecimal> params = new LinkedHashMap<>();
            for (Map.Entry<String, CheckBox> e : memberCheckBoxes.entrySet()) {
                if (e.getValue().isSelected()) {
                    activeMembers.add(e.getKey());
                    try {
                        params.put(e.getKey(), new BigDecimal(memberInputFields.get(e.getKey()).getText().trim()));
                    } catch (Exception ex) {
                        params.put(e.getKey(), BigDecimal.ZERO);
                    }
                }
            }

            if (editingExpense != null) {
                SplitCalculationService.SplitResult splitResult = appContext.getSplitCalculationService().calculateSplits(
                    totalCents, currentSplitType, activeMembers, params
                );
                if (!splitResult.isValid()) {
                    throw new IllegalArgumentException(splitResult.getErrorMessage());
                }

                Expense updatedExpense = new Expense(
                    editingExpense.getId(),
                    group.getId(),
                    titleField.getText().trim(),
                    totalCents,
                    categoryComboBox.getValue(),
                    datePicker.getValue(),
                    currentSplitType,
                    notesField.getText().trim(),
                    editingExpense.getCreatedAt()
                );

                List<ExpensePayer> updatedPayers = List.of(
                    new ExpensePayer(UUID.randomUUID().toString(), editingExpense.getId(), payer.getId(), totalCents)
                );

                List<ExpenseSplit> updatedSplits = activeMembers.stream()
                    .map(mId -> new ExpenseSplit(
                        UUID.randomUUID().toString(),
                        editingExpense.getId(),
                        mId,
                        splitResult.getShare(mId),
                        params != null ? params.getOrDefault(mId, BigDecimal.ZERO) : BigDecimal.ZERO
                    ))
                    .toList();

                appContext.getExpenseService().updateExpense(updatedExpense, updatedPayers, updatedSplits);
            } else {
                appContext.getExpenseService().createExpense(
                    group.getId(),
                    titleField.getText().trim(),
                    totalCents,
                    categoryComboBox.getValue(),
                    datePicker.getValue(),
                    notesField.getText().trim(),
                    currentSplitType,
                    payers,
                    activeMembers,
                    params
                );
            }

            onClose();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    @FXML
    private void onClose() {
        if (mainController != null) {
            mainController.closeModal();
        } else {
            appContext.getNavigationService().closeModal();
        }
    }
}

