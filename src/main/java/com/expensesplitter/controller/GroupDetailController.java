package com.expensesplitter.controller;

import com.expensesplitter.model.*;
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
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.*;

public class GroupDetailController implements Initializable, MainController.ParamReceiver {

    @FXML private Label breadcrumbGroupLabel;
    @FXML private Label groupTitleLabel;
    @FXML private HBox memberTagsContainer;

    @FXML private Button tabExpensesBtn;
    @FXML private Button tabBalancesBtn;
    @FXML private Button tabMembersBtn;

    @FXML private VBox expensesTabBox;
    @FXML private VBox balancesTabBox;
    @FXML private VBox membersTabBox;

    @FXML private TextField expenseSearchField;
    @FXML private ComboBox<String> categoryFilterBox;
    @FXML private TableView<ExpenseDetailRow> expensesTable;
    @FXML private TableColumn<ExpenseDetailRow, String> colExpDate;
    @FXML private TableColumn<ExpenseDetailRow, String> colExpTitle;
    @FXML private TableColumn<ExpenseDetailRow, String> colExpPayer;
    @FXML private TableColumn<ExpenseDetailRow, String> colExpCategory;
    @FXML private TableColumn<ExpenseDetailRow, String> colExpAmount;
    @FXML private TableColumn<ExpenseDetailRow, String> colExpShare;

    @FXML private VBox individualBalancesContainer;
    @FXML private VBox simplifiedTransfersContainer;
    @FXML private VBox membersListContainer;

    private AppContext appContext;
    private Group currentGroup;
    private final ObservableList<ExpenseDetailRow> expenseRows = FXCollections.observableArrayList();

    public record ExpenseDetailRow(
        String id,
        String date,
        String title,
        String paidBy,
        String category,
        String amount,
        String yourShare,
        boolean isCredit
    ) {}

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.appContext = AppContext.getInstance();
        setupTable();
    }

    @Override
    public void setNavigationParameter(Object param) {
        if (param instanceof Group g) {
            this.currentGroup = g;
        } else if (param instanceof String groupId) {
            this.currentGroup = appContext.getGroupService().getGroupById(groupId).orElse(null);
        }

        if (this.currentGroup == null) {
            List<Group> all = appContext.getGroupService().getAllGroups();
            if (!all.isEmpty()) {
                this.currentGroup = all.get(0);
            }
        }

        renderGroupData();
    }

    private void setupTable() {
        colExpDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().date()));
        colExpTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().title()));
        colExpPayer.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().paidBy()));
        colExpCategory.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().category()));
        colExpAmount.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().amount()));
        colExpShare.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().yourShare()));

        colExpShare.setCellFactory(col -> new TableCell<>() {
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

        FilteredList<ExpenseDetailRow> filtered = new FilteredList<>(expenseRows, p -> true);

        expenseSearchField.textProperty().addListener((obs, oldV, newV) -> applyFilter(filtered));
        categoryFilterBox.valueProperty().addListener((obs, oldV, newV) -> applyFilter(filtered));

        expensesTable.setRowFactory(tv -> {
            TableRow<ExpenseDetailRow> row = new TableRow<>();
            ContextMenu rowMenu = new ContextMenu();
            MenuItem editItem = new MenuItem("Edit expense");
            editItem.setOnAction(e -> {
                ExpenseDetailRow item = row.getItem();
                if (item != null) {
                    onEditExpense(item.id());
                }
            });

            MenuItem deleteItem = new MenuItem("Delete expense");
            deleteItem.setOnAction(e -> {
                ExpenseDetailRow item = row.getItem();
                if (item != null) {
                    onDeleteExpense(item.id(), item.title());
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
                    onEditExpense(row.getItem().id());
                }
            });

            return row;
        });

        expensesTable.setItems(filtered);
    }

    private void onEditExpense(String expenseId) {
        appContext.getExpenseService().getExpenseById(expenseId).ifPresent(exp -> {
            appContext.getNavigationService().openAddExpense(exp);
        });
    }

    private void onDeleteExpense(String expenseId, String title) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Expense");
        confirm.setHeaderText("Delete '" + title + "'?");
        confirm.setContentText("This will permanently remove the expense, payer allocations, and split shares. Group balances will update immediately.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                appContext.getExpenseService().deleteExpense(expenseId);
                renderGroupData();
            }
        });
    }

    private void applyFilter(FilteredList<ExpenseDetailRow> filtered) {
        String query = expenseSearchField.getText();
        String selectedCat = categoryFilterBox.getValue();

        filtered.setPredicate(row -> {
            boolean matchesQuery = (query == null || query.isBlank()) ||
                row.title().toLowerCase().contains(query.toLowerCase()) ||
                row.paidBy().toLowerCase().contains(query.toLowerCase());

            boolean matchesCat = (selectedCat == null || selectedCat.equalsIgnoreCase("All categories")) ||
                row.category().equalsIgnoreCase(selectedCat);

            return matchesQuery && matchesCat;
        });
    }

    private void renderGroupData() {
        if (currentGroup == null) return;

        breadcrumbGroupLabel.setText(currentGroup.getName());
        groupTitleLabel.setText(currentGroup.getName());

        GroupService groupService = appContext.getGroupService();
        ExpenseService expenseService = appContext.getExpenseService();
        BalanceService balanceService = appContext.getBalanceService();
        Member currentUser = appContext.getCurrentUser();
        String currentUserId = (currentUser != null) ? currentUser.getId() : "";

        // 1. Members tag header
        memberTagsContainer.getChildren().clear();
        List<Member> members = groupService.getGroupMembers(currentGroup.getId());
        for (int i = 0; i < members.size(); i++) {
            Member m = members.get(i);
            Label tag = new Label(m.getName());
            tag.getStyleClass().addAll("badge", "badge-neutral");
            memberTagsContainer.getChildren().add(tag);
            if (i < members.size() - 1) {
                Label dot = new Label("•");
                dot.getStyleClass().add("text-secondary");
                memberTagsContainer.getChildren().add(dot);
            }
        }

        // 2. Load Expenses
        expenseRows.clear();
        List<Expense> expenses = expenseService.getExpensesByGroup(currentGroup.getId());
        Set<String> categories = new TreeSet<>();
        categories.add("All categories");

        Map<String, String> memberNames = new HashMap<>();
        for (Member m : members) {
            memberNames.put(m.getId(), m.getName());
        }

        for (Expense e : expenses) {
            categories.add(e.getCategory());
            List<ExpensePayer> payers = expenseService.getPayers(e.getId());
            String paidByName = payers.isEmpty() ? "Unknown" : memberNames.getOrDefault(payers.get(0).getMemberId(), "Member");

            long paidByUser = 0;
            for (ExpensePayer p : payers) {
                if (p.getMemberId().equals(currentUserId)) {
                    paidByUser += p.getAmountCents();
                }
            }

            long userShare = 0;
            for (ExpenseSplit s : expenseService.getSplits(e.getId())) {
                if (s.getMemberId().equals(currentUserId)) {
                    userShare = s.getShareCents();
                    break;
                }
            }

            long userNet = paidByUser - userShare;
            String shareStr = (userNet >= 0) ? ("+" + formatCurrency(userNet)) : ("-" + formatCurrency(Math.abs(userNet)));

            expenseRows.add(new ExpenseDetailRow(
                e.getId(),
                e.getDate().toString(),
                e.getTitle(),
                paidByName,
                e.getCategory(),
                currentGroup.getCurrencySymbol() + formatCurrency(e.getAmountCents()),
                shareStr,
                userNet >= 0
            ));
        }

        categoryFilterBox.getItems().setAll(categories);
        categoryFilterBox.setValue("All categories");
        tabExpensesBtn.setText("Expenses (" + expenses.size() + ")");
        tabBalancesBtn.setText("Balances (" + members.size() + ")");
        tabMembersBtn.setText("Members (" + members.size() + ")");

        // 3. Load Balances Tab
        renderBalancesTab(members, balanceService);

        // 4. Load Members Tab
        renderMembersTab(members);
    }

    private void renderBalancesTab(List<Member> members, BalanceService balanceService) {
        individualBalancesContainer.getChildren().clear();
        Map<String, Long> netBalances = balanceService.computeNetBalances(currentGroup.getId());

        for (Member m : members) {
            long net = netBalances.getOrDefault(m.getId(), 0L);
            HBox card = new HBox(12);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("card");

            HBox av = new HBox();
            av.getStyleClass().add("avatar-circle");
            Label in = new Label(m.getInitials());
            in.getStyleClass().add("avatar-initials");
            av.getChildren().add(in);

            VBox info = new VBox(2);
            Label name = new Label(m.getName());
            name.getStyleClass().add("text-label");
            String statusDesc = (net > 0) ? "gets back" : (net < 0 ? "owes group" : "fully settled");
            Label sub = new Label(statusDesc);
            sub.getStyleClass().add("text-caption");
            info.getChildren().addAll(name, sub);

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Label amt = new Label((net >= 0 ? "+" : "-") + currentGroup.getCurrencySymbol() + formatCurrency(Math.abs(net)));
            amt.getStyleClass().addAll("text-mono", net >= 0 ? "text-positive" : "text-negative");

            card.getChildren().addAll(av, info, sp, amt);
            individualBalancesContainer.getChildren().add(card);
        }

        // Simplified Transfers
        simplifiedTransfersContainer.getChildren().clear();
        List<DebtSimplificationService.Transfer> transfers = balanceService.computeSimplifiedTransfers(currentGroup.getId());
        if (transfers.isEmpty()) {
            Label settledLabel = new Label("All balances are fully settled.");
            settledLabel.getStyleClass().add("text-secondary");
            simplifiedTransfersContainer.getChildren().add(settledLabel);
        } else {
            Map<String, String> memberNames = new HashMap<>();
            for (Member m : members) {
                memberNames.put(m.getId(), m.getName());
            }

            for (DebtSimplificationService.Transfer t : transfers) {
                HBox tRow = new HBox(8);
                tRow.setAlignment(Pos.CENTER_LEFT);
                tRow.getStyleClass().add("card-subtle");

                String from = memberNames.getOrDefault(t.getFromMemberId(), "Member");
                String to = memberNames.getOrDefault(t.getToMemberId(), "Member");

                VBox tCol = new VBox(2);
                Label l = new Label(from + " → " + to);
                l.getStyleClass().add("text-body");
                Label a = new Label(currentGroup.getCurrencySymbol() + formatCurrency(t.getAmountCents()));
                a.getStyleClass().addAll("text-mono", "text-positive");
                tCol.getChildren().addAll(l, a);

                tRow.getChildren().add(tCol);
                simplifiedTransfersContainer.getChildren().add(tRow);
            }
        }
    }

    private void renderMembersTab(List<Member> members) {
        membersListContainer.getChildren().clear();
        for (Member m : members) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("card");

            HBox av = new HBox();
            av.getStyleClass().add("avatar-circle");
            Label in = new Label(m.getInitials());
            in.getStyleClass().add("avatar-initials");
            av.getChildren().add(in);

            VBox info = new VBox(2);
            Label name = new Label(m.getName());
            name.getStyleClass().add("text-label");
            Label contact = new Label((m.getEmail() != null ? m.getEmail() : "") + (m.getPhone() != null ? " • " + m.getPhone() : ""));
            contact.getStyleClass().add("text-caption");
            info.getChildren().addAll(name, contact);

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Button removeBtn = new Button("Remove");
            removeBtn.getStyleClass().add("btn-secondary");
            removeBtn.setOnAction(e -> {
                long net = appContext.getBalanceService().getUserNetBalance(m.getId(), currentGroup.getId());
                if (net != 0) {
                    Alert warn = new Alert(Alert.AlertType.WARNING);
                    warn.setTitle("Cannot Remove Member");
                    warn.setHeaderText("Outstanding balance pending");
                    warn.setContentText(m.getName() + " has an active net balance of " + (net > 0 ? "+" : "-") + currentGroup.getCurrencySymbol() + formatCurrency(Math.abs(net)) + ". Please settle all outstanding balances before removing this member.");
                    warn.showAndWait();
                    return;
                }

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Remove Member");
                confirm.setHeaderText("Remove " + m.getName() + " from " + currentGroup.getName() + "?");
                confirm.setContentText("The member will be removed from this shared group.");
                confirm.showAndWait().ifPresent(res -> {
                    if (res == ButtonType.OK) {
                        appContext.getGroupService().removeMemberFromGroup(currentGroup.getId(), m.getId());
                        renderGroupData();
                    }
                });
            });

            row.getChildren().addAll(av, info, sp, removeBtn);
            membersListContainer.getChildren().add(row);
        }
    }

    private String formatCurrency(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
    }

    @FXML
    private void onBackToGroups() {
        appContext.getNavigationService().navigateTo(ViewType.GROUPS);
    }

    @FXML
    private void onShowExpensesTab() {
        switchTab(expensesTabBox, tabExpensesBtn);
    }

    @FXML
    private void onShowBalancesTab() {
        switchTab(balancesTabBox, tabBalancesBtn);
    }

    @FXML
    private void onShowMembersTab() {
        switchTab(membersTabBox, tabMembersBtn);
    }

    private void switchTab(VBox activeBox, Button activeBtn) {
        expensesTabBox.setVisible(false);
        expensesTabBox.setManaged(false);
        balancesTabBox.setVisible(false);
        balancesTabBox.setManaged(false);
        membersTabBox.setVisible(false);
        membersTabBox.setManaged(false);

        tabExpensesBtn.getStyleClass().remove("active");
        tabBalancesBtn.getStyleClass().remove("active");
        tabMembersBtn.getStyleClass().remove("active");

        activeBox.setVisible(true);
        activeBox.setManaged(true);
        activeBtn.getStyleClass().add("active");
    }

    @FXML
    private void onAddExpense() {
        appContext.getNavigationService().openAddExpense(currentGroup);
    }

    @FXML
    private void onSettleUp() {
        appContext.getNavigationService().navigateTo(ViewType.SETTLE_UP, currentGroup);
    }

    @FXML
    private void onExportLedger() {
        appContext.getNavigationService().navigateTo(ViewType.REPORTS, currentGroup);
    }

    @FXML
    private void onAddMember() {
        Dialog<Member> dialog = new Dialog<>();
        dialog.setTitle("Add Member");
        dialog.setHeaderText("Add a member to " + currentGroup.getName());

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        VBox form = new VBox(12);
        form.setPadding(new javafx.geometry.Insets(16));

        Label nameLabel = new Label("Member Name");
        nameLabel.getStyleClass().add("form-label");
        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Maya Chen");

        Label emailLabel = new Label("Email Address (Optional)");
        emailLabel.getStyleClass().add("form-label");
        TextField emailField = new TextField();
        emailField.setPromptText("e.g. maya@example.com (links account if registered)");

        form.getChildren().addAll(nameLabel, nameField, emailLabel, emailField);
        dialog.getDialogPane().setContent(form);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType && !nameField.getText().isBlank()) {
                String name = nameField.getText().trim();
                String email = emailField.getText() != null && !emailField.getText().isBlank() ? emailField.getText().trim() : null;

                // Check if email matches an existing user
                String userId = null;
                if (email != null) {
                    Optional<com.expensesplitter.model.User> matchedUser = appContext.getUserDao().findByEmail(email);
                    if (matchedUser.isPresent()) {
                        userId = matchedUser.get().id();
                        Optional<Member> existingMember = appContext.getMemberDao().findByUserId(userId);
                        if (existingMember.isPresent()) {
                            return existingMember.get();
                        }
                    }
                } else {
                    Optional<com.expensesplitter.model.User> matchedUser = appContext.getUserDao().findByDisplayName(name);
                    if (matchedUser.isPresent()) {
                        userId = matchedUser.get().id();
                        Optional<Member> existingMember = appContext.getMemberDao().findByUserId(userId);
                        if (existingMember.isPresent()) {
                            return existingMember.get();
                        }
                    }
                }

                String id = UUID.randomUUID().toString();
                return new Member(id, name, email, null, userId, System.currentTimeMillis());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(member -> {
            if (appContext.getMemberDao().findById(member.getId()).isEmpty()) {
                appContext.getMemberDao().save(member);
            }
            appContext.getGroupService().addMemberToGroup(currentGroup.getId(), member.getId());
            renderGroupData();
        });
    }
}

