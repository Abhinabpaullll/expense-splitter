package com.expensesplitter.controller;

import com.expensesplitter.model.Expense;
import com.expensesplitter.model.ExpensePayer;
import com.expensesplitter.model.ExpenseSplit;
import com.expensesplitter.model.Group;
import com.expensesplitter.model.Member;
import com.expensesplitter.service.BalanceService;
import com.expensesplitter.service.DebtSimplificationService;
import com.expensesplitter.service.ExpenseService;
import com.expensesplitter.service.GroupService;
import com.expensesplitter.service.ViewType;
import com.expensesplitter.util.AppContext;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.*;

public class DashboardController implements Initializable {

    @FXML private Label owedAmountLabel;
    @FXML private Label owedSubtextLabel;
    @FXML private Label oweAmountLabel;
    @FXML private Label oweSubtextLabel;
    @FXML private Label netAmountLabel;
    @FXML private Label netSubtextLabel;
    @FXML private Label netStatusBadge;

    @FXML private TextField tableFilterField;
    @FXML private TableView<ExpenseRowModel> recentActivityTable;
    @FXML private TableColumn<ExpenseRowModel, String> colDate;
    @FXML private TableColumn<ExpenseRowModel, String> colDescription;
    @FXML private TableColumn<ExpenseRowModel, String> colGroup;
    @FXML private TableColumn<ExpenseRowModel, String> colPaidBy;
    @FXML private TableColumn<ExpenseRowModel, String> colShare;

    @FXML private VBox categoryBreakdownContainer;
    @FXML private VBox quickSettlementContainer;

    private AppContext appContext;
    private final ObservableList<ExpenseRowModel> tableData = FXCollections.observableArrayList();

    public record ExpenseRowModel(
        String id,
        String date,
        String description,
        String groupName,
        String paidBy,
        String yourShare,
        boolean isCredit
    ) {}

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.appContext = AppContext.getInstance();

        setupTableColumns();
        refreshDashboard();
    }

    private void setupTableColumns() {
        colDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().date()));
        colDescription.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().description()));
        colGroup.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().groupName()));
        colPaidBy.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().paidBy()));
        colShare.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().yourShare()));

        colShare.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("text-positive", "text-negative");
                } else {
                    setText(item);
                    getStyleClass().removeAll("text-positive", "text-negative");
                    if (item.startsWith("+")) {
                        getStyleClass().add("text-positive");
                    } else if (item.startsWith("-")) {
                        getStyleClass().add("text-negative");
                    }
                }
            }
        });

        FilteredList<ExpenseRowModel> filteredData = new FilteredList<>(tableData, p -> true);
        tableFilterField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(row -> {
                if (newValue == null || newValue.isBlank()) return true;
                String lower = newValue.toLowerCase();
                return row.description().toLowerCase().contains(lower) ||
                       row.groupName().toLowerCase().contains(lower) ||
                       row.paidBy().toLowerCase().contains(lower);
            });
        });

        recentActivityTable.setRowFactory(tv -> {
            TableRow<ExpenseRowModel> row = new TableRow<>();
            ContextMenu rowMenu = new ContextMenu();
            MenuItem editItem = new MenuItem("Edit expense");
            editItem.setOnAction(e -> {
                ExpenseRowModel item = row.getItem();
                if (item != null) {
                    appContext.getExpenseService().getExpenseById(item.id()).ifPresent(exp -> {
                        appContext.getNavigationService().openAddExpense(exp);
                    });
                }
            });

            MenuItem deleteItem = new MenuItem("Delete expense");
            deleteItem.setOnAction(e -> {
                ExpenseRowModel item = row.getItem();
                if (item != null) {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Delete Expense");
                    confirm.setHeaderText("Delete '" + item.description() + "'?");
                    confirm.setContentText("This will permanently remove the expense and recalculate all balances.");
                    confirm.showAndWait().ifPresent(res -> {
                        if (res == ButtonType.OK) {
                            appContext.getExpenseService().deleteExpense(item.id());
                            refreshDashboard();
                        }
                    });
                }
            });

            rowMenu.getItems().addAll(editItem, deleteItem);
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(rowMenu)
            );

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    appContext.getExpenseService().getExpenseById(row.getItem().id()).ifPresent(exp -> {
                        appContext.getNavigationService().openAddExpense(exp);
                    });
                }
            });

            return row;
        });

        recentActivityTable.setItems(filteredData);
    }

    public void refreshDashboard() {
        Member currentUser = appContext.getCurrentUser();
        String currentUserId = (currentUser != null) ? currentUser.getId() : "";
        BalanceService balanceService = appContext.getBalanceService();
        GroupService groupService = appContext.getGroupService();
        ExpenseService expenseService = appContext.getExpenseService();

        // 1. Metric strip calculations
        long totalOwed = balanceService.getUserOwed(currentUserId, null);
        long totalOwes = balanceService.getUserOwes(currentUserId, null);
        long netBalance = balanceService.getUserNetBalance(currentUserId, null);

        owedAmountLabel.setText("+" + formatCurrency(totalOwed));
        oweAmountLabel.setText("-" + formatCurrency(totalOwes));

        if (netBalance >= 0) {
            netAmountLabel.setText("+" + formatCurrency(netBalance));
            netAmountLabel.getStyleClass().removeAll("text-positive", "text-negative");
            netAmountLabel.getStyleClass().add("text-positive");
            netStatusBadge.setText("Surplus");
            netStatusBadge.getStyleClass().removeAll("badge-positive", "badge-negative");
            netStatusBadge.getStyleClass().add("badge-positive");
            netSubtextLabel.setText("Settlement ready");
        } else {
            netAmountLabel.setText("-" + formatCurrency(Math.abs(netBalance)));
            netAmountLabel.getStyleClass().removeAll("text-positive", "text-negative");
            netAmountLabel.getStyleClass().add("text-negative");
            netStatusBadge.setText("Deficit");
            netStatusBadge.getStyleClass().removeAll("badge-positive", "badge-negative");
            netStatusBadge.getStyleClass().add("badge-negative");
            netSubtextLabel.setText("Payment pending");
        }

        // 2. Load recent activity
        tableData.clear();
        List<Expense> expenses = expenseService.getAllExpenses();
        Map<String, String> groupNames = new HashMap<>();
        Map<String, String> memberNames = new HashMap<>();

        for (Group g : groupService.getAllGroups()) {
            groupNames.put(g.getId(), g.getName());
        }
        for (Member m : appContext.getMemberDao().findAll()) {
            memberNames.put(m.getId(), m.getName());
        }

        int count = 0;
        for (Expense exp : expenses) {
            if (count++ >= 15) break;

            String groupName = groupNames.getOrDefault(exp.getGroupId(), "General");
            List<ExpensePayer> payers = expenseService.getPayers(exp.getId());
            String paidBy = payers.isEmpty() ? "Unknown" : memberNames.getOrDefault(payers.get(0).getMemberId(), "Member");

            List<ExpenseSplit> splits = expenseService.getSplits(exp.getId());
            long userShare = 0;
            for (ExpenseSplit s : splits) {
                if (s.getMemberId().equals(currentUserId)) {
                    userShare = s.getShareCents();
                    break;
                }
            }

            long paidByUser = 0;
            for (ExpensePayer p : payers) {
                if (p.getMemberId().equals(currentUserId)) {
                    paidByUser += p.getAmountCents();
                }
            }

            long userNet = paidByUser - userShare;
            String shareStr = (userNet >= 0) ? ("+" + formatCurrency(userNet)) : ("-" + formatCurrency(Math.abs(userNet)));

            tableData.add(new ExpenseRowModel(
                exp.getId(),
                exp.getDate().toString(),
                exp.getTitle(),
                groupName,
                paidBy,
                shareStr,
                userNet >= 0
            ));
        }

        String curSymbol = appContext.getSettingsDao().get("default_currency")
            .map(c -> c.contains("USD") ? "$" : (c.contains("EUR") ? "€" : (c.contains("GBP") ? "£" : "₹")))
            .orElse("₹");

        // 3. Category Breakdown
        categoryBreakdownContainer.getChildren().clear();
        Map<String, Long> categorySpending = balanceService.computeSpendingByCategory(null);
        long maxCat = categorySpending.values().stream().mapToLong(Long::longValue).max().orElse(1L);

        for (Map.Entry<String, Long> entry : categorySpending.entrySet()) {
            VBox catRow = new VBox(4);
            HBox labels = new HBox();
            Label catName = new Label(entry.getKey());
            catName.getStyleClass().add("text-body");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label catVal = new Label(curSymbol + formatCurrency(entry.getValue()));
            catVal.getStyleClass().addAll("text-mono", "text-secondary");
            labels.getChildren().addAll(catName, spacer, catVal);

            ProgressBar bar = new ProgressBar((double) entry.getValue() / maxCat);
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.setPrefHeight(6.0);
            catRow.getChildren().addAll(labels, bar);
            categoryBreakdownContainer.getChildren().add(catRow);
        }

        // 4. Quick settlements
        quickSettlementContainer.getChildren().clear();
        List<DebtSimplificationService.Transfer> transfers = balanceService.computeSimplifiedTransfers(null);
        int tCount = 0;
        for (DebtSimplificationService.Transfer t : transfers) {
            if (tCount++ >= 3) break;
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("card-subtle");

            String fromName = memberNames.getOrDefault(t.getFromMemberId(), "Member");
            String toName = memberNames.getOrDefault(t.getToMemberId(), "Member");

            VBox textCol = new VBox(2);
            Label transferLabel = new Label(fromName + " pays " + toName);
            transferLabel.getStyleClass().add("text-body");
            Label amountLabel = new Label(curSymbol + formatCurrency(t.getAmountCents()));
            amountLabel.getStyleClass().addAll("text-mono", "text-positive");
            textCol.getChildren().addAll(transferLabel, amountLabel);

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Button settleBtn = new Button("Settle");
            settleBtn.getStyleClass().add("btn-secondary");
            settleBtn.setOnAction(e -> onOpenSettleUp());

            row.getChildren().addAll(textCol, sp, settleBtn);
            quickSettlementContainer.getChildren().add(row);
        }
    }

    private String formatCurrency(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
    }

    @FXML
    private void onAddExpense() {
        appContext.getNavigationService().openAddExpense(null);
    }

    @FXML
    private void onOpenSettleUp() {
        appContext.getNavigationService().navigateTo(ViewType.SETTLE_UP);
    }
}

