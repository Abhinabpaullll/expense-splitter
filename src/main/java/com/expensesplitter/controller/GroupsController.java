package com.expensesplitter.controller;

import com.expensesplitter.model.Group;
import com.expensesplitter.model.GroupStatus;
import com.expensesplitter.model.Member;
import com.expensesplitter.service.BalanceService;
import com.expensesplitter.service.GroupService;
import com.expensesplitter.service.ViewType;
import com.expensesplitter.util.AppContext;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.*;

public class GroupsController implements Initializable {

    @FXML private Button filterAllBtn;
    @FXML private Button filterActiveBtn;
    @FXML private Button filterSettledBtn;
    @FXML private Button filterArchivedBtn;
    @FXML private TextField searchGroupField;
    @FXML private FlowPane groupsFlowPane;
    @FXML private VBox emptyStateBox;

    private AppContext appContext;
    private GroupStatus selectedFilter = null; // null means All

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.appContext = AppContext.getInstance();

        searchGroupField.textProperty().addListener((obs, oldVal, newVal) -> renderGroups());
        renderGroups();
    }

    @FXML
    private void onFilterAll() {
        setFilter(null, filterAllBtn);
    }

    @FXML
    private void onFilterActive() {
        setFilter(GroupStatus.ACTIVE, filterActiveBtn);
    }

    @FXML
    private void onFilterSettled() {
        setFilter(GroupStatus.SETTLED, filterSettledBtn);
    }

    @FXML
    private void onFilterArchived() {
        setFilter(GroupStatus.ARCHIVED, filterArchivedBtn);
    }

    private void setFilter(GroupStatus status, Button activeBtn) {
        selectedFilter = status;
        filterAllBtn.getStyleClass().remove("active");
        filterActiveBtn.getStyleClass().remove("active");
        filterSettledBtn.getStyleClass().remove("active");
        filterArchivedBtn.getStyleClass().remove("active");
        activeBtn.getStyleClass().add("active");
        renderGroups();
    }

    private void renderGroups() {
        groupsFlowPane.getChildren().clear();

        GroupService groupService = appContext.getGroupService();
        BalanceService balanceService = appContext.getBalanceService();
        Member currentUser = appContext.getCurrentUser();
        String currentUserId = (currentUser != null) ? currentUser.getId() : "";
        com.expensesplitter.model.User accountUser = appContext.getCurrentAccountUser();
        String accountUserId = (accountUser != null) ? accountUser.id() : currentUserId;

        List<Group> groups = (selectedFilter == null)
            ? groupService.getGroupsForUser(accountUserId)
            : groupService.getGroupsForUserAndStatus(accountUserId, selectedFilter);

        String query = searchGroupField.getText();
        if (query != null && !query.isBlank()) {
            String lower = query.toLowerCase();
            groups = groups.stream().filter(g -> g.getName().toLowerCase().contains(lower)).toList();
        }

        if (groups.isEmpty()) {
            emptyStateBox.setVisible(true);
            emptyStateBox.setManaged(true);
            return;
        } else {
            emptyStateBox.setVisible(false);
            emptyStateBox.setManaged(false);
        }

        for (Group group : groups) {
            VBox card = createGroupCard(group, currentUserId, balanceService, groupService);
            groupsFlowPane.getChildren().add(card);
        }
    }

    private VBox createGroupCard(Group group, String currentUserId, BalanceService balanceService, GroupService groupService) {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setPrefWidth(340);
        card.setMinWidth(320);

        // Header: Title & Status Badge
        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox titleCol = new VBox(2);
        Label name = new Label(group.getName());
        name.getStyleClass().add("text-label");
        Label currency = new Label("Currency: " + group.getCurrency() + " (" + group.getCurrencySymbol() + ")");
        currency.getStyleClass().add("text-caption");
        titleCol.getChildren().addAll(name, currency);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label statusBadge = new Label(group.getStatus().getDisplayName());
        statusBadge.getStyleClass().addAll("badge", group.getStatus() == GroupStatus.ACTIVE ? "badge-positive" : "badge-neutral");

        top.getChildren().addAll(titleCol, sp, statusBadge);

        // Members row
        List<Member> members = groupService.getGroupMembers(group.getId());
        HBox membersRow = new HBox(6);
        membersRow.setAlignment(Pos.CENTER_LEFT);
        for (Member m : members) {
            HBox avatar = new HBox();
            avatar.getStyleClass().add("avatar-circle");
            Label in = new Label(m.getInitials());
            in.getStyleClass().add("avatar-initials");
            avatar.getChildren().add(in);
            membersRow.getChildren().add(avatar);
        }
        Label memberCount = new Label(members.size() + " members");
        memberCount.getStyleClass().add("text-caption");
        membersRow.getChildren().add(memberCount);

        // Net Balance for current user in this group
        long net = balanceService.getUserNetBalance(currentUserId, group.getId());
        HBox balanceRow = new HBox(8);
        balanceRow.setAlignment(Pos.CENTER_LEFT);
        balanceRow.getStyleClass().add("card-subtle");

        VBox bCol = new VBox(2);
        Label bCaption = new Label("Your net balance");
        bCaption.getStyleClass().add("text-caption");

        Label bAmount = new Label((net >= 0 ? "+" : "-") + group.getCurrencySymbol() + formatCurrency(Math.abs(net)));
        bAmount.getStyleClass().addAll("text-mono", net >= 0 ? "text-positive" : "text-negative");
        bCol.getChildren().addAll(bCaption, bAmount);

        balanceRow.getChildren().add(bCol);

        // Action Buttons Row
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);

        ContextMenu cardMenu = new ContextMenu();
        MenuItem editItem = new MenuItem("Edit group");
        editItem.setOnAction(e -> onEditGroup(group));

        MenuItem archiveItem = new MenuItem(group.getStatus() == GroupStatus.ARCHIVED ? "Activate group" : "Archive group");
        archiveItem.setOnAction(e -> {
            if (group.getStatus() == GroupStatus.ARCHIVED) {
                Group updated = new Group(group.getId(), group.getName(), group.getCurrency(), GroupStatus.ACTIVE, group.getCreatedByUserId(), group.getCreatedAt(), System.currentTimeMillis());
                groupService.updateGroup(updated);
            } else {
                groupService.archiveGroup(group.getId());
            }
            renderGroups();
        });

        MenuItem deleteItem = new MenuItem("Delete group");
        deleteItem.setOnAction(e -> onDeleteGroup(group));

        cardMenu.getItems().addAll(editItem, archiveItem, new SeparatorMenuItem(), deleteItem);

        Button optionsBtn = new Button("•••");
        optionsBtn.getStyleClass().add("btn-secondary");
        optionsBtn.setOnAction(e -> cardMenu.show(optionsBtn, javafx.geometry.Side.BOTTOM, 0, 0));

        Button openBtn = new Button("Open register");
        openBtn.getStyleClass().add("btn-primary");
        openBtn.setOnAction(e -> appContext.getNavigationService().navigateTo(ViewType.GROUP_DETAIL, group));

        Button settleBtn = new Button("Settle up");
        settleBtn.getStyleClass().add("btn-secondary");
        settleBtn.setOnAction(e -> appContext.getNavigationService().navigateTo(ViewType.SETTLE_UP, group));

        actions.getChildren().addAll(optionsBtn, settleBtn, openBtn);

        card.setOnContextMenuRequested(e -> cardMenu.show(card, e.getScreenX(), e.getScreenY()));

        card.getChildren().addAll(top, membersRow, balanceRow, actions);
        return card;
    }

    private void onEditGroup(Group group) {
        Dialog<Group> dialog = new Dialog<>();
        dialog.setTitle("Edit Group");
        dialog.setHeaderText("Update shared group details");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        VBox form = new VBox(12);
        form.setPadding(new javafx.geometry.Insets(16));

        Label nameLabel = new Label("Group Name");
        nameLabel.getStyleClass().add("form-label");
        TextField nameField = new TextField(group.getName());

        Label curLabel = new Label("Currency");
        curLabel.getStyleClass().add("form-label");
        ComboBox<String> currencyBox = new ComboBox<>();
        currencyBox.getItems().addAll("INR", "USD", "EUR", "GBP");
        currencyBox.setValue(group.getCurrency());

        form.getChildren().addAll(nameLabel, nameField, curLabel, currencyBox);
        dialog.getDialogPane().setContent(form);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType && !nameField.getText().isBlank()) {
                return new Group(group.getId(), nameField.getText().trim(), currencyBox.getValue(), group.getStatus(), group.getCreatedByUserId(), group.getCreatedAt(), System.currentTimeMillis());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(updated -> {
            appContext.getGroupService().updateGroup(updated);
            renderGroups();
        });
    }

    private void onDeleteGroup(Group group) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Group");
        confirm.setHeaderText("Delete '" + group.getName() + "'?");
        confirm.setContentText("All expenses, splits, and payments associated with this group will be permanently removed. This action cannot be undone.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                appContext.getGroupService().deleteGroup(group.getId());
                renderGroups();
            }
        });
    }

    private String formatCurrency(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
    }

    @FXML
    private void onNewGroup() {
        Dialog<Group> dialog = new Dialog<>();
        dialog.setTitle("Create New Group");
        dialog.setHeaderText("Create a shared expense ledger");

        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        VBox form = new VBox(12);
        form.setPadding(new javafx.geometry.Insets(16));

        Label nameLabel = new Label("Group Name");
        nameLabel.getStyleClass().add("form-label");
        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Vacation Trip, Office Lunch");

        Label curLabel = new Label("Currency");
        curLabel.getStyleClass().add("form-label");
        ComboBox<String> currencyBox = new ComboBox<>();
        currencyBox.getItems().addAll("INR", "USD", "EUR", "GBP");
        currencyBox.setValue("INR");

        form.getChildren().addAll(nameLabel, nameField, curLabel, currencyBox);
        dialog.getDialogPane().setContent(form);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType && !nameField.getText().isBlank()) {
                String id = UUID.randomUUID().toString();
                com.expensesplitter.model.User accUser = appContext.getCurrentAccountUser();
                String createdBy = (accUser != null) ? accUser.id() : null;
                return new Group(id, nameField.getText().trim(), currencyBox.getValue(), GroupStatus.ACTIVE, createdBy, System.currentTimeMillis(), System.currentTimeMillis());
            }
            return null;
        });

        Optional<Group> result = dialog.showAndWait();
        result.ifPresent(newGroup -> {
            Member currentUser = appContext.getCurrentUser();
            List<String> memberIds = (currentUser != null) ? List.of(currentUser.getId()) : Collections.emptyList();
            appContext.getGroupService().createGroup(newGroup, memberIds);
            renderGroups();
        });
    }
}

