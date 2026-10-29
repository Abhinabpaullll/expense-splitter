package com.expensesplitter.service;

public enum ViewType {
    DASHBOARD("Dashboard", "/fxml/DashboardView.fxml"),
    GROUPS("Groups", "/fxml/GroupsView.fxml"),
    GROUP_DETAIL("Group Detail", "/fxml/GroupDetailView.fxml"),
    SETTLE_UP("Settle Up", "/fxml/SettleUpView.fxml"),
    REMINDERS("Reminders", "/fxml/RemindersView.fxml"),
    REPORTS("Reports", "/fxml/ReportsView.fxml"),
    SETTINGS("Settings", "/fxml/SettingsView.fxml"),
    AUTH("Account", "/fxml/AuthView.fxml");

    private final String title;
    private final String fxmlPath;

    ViewType(String title, String fxmlPath) {
        this.title = title;
        this.fxmlPath = fxmlPath;
    }

    public String getTitle() {
        return title;
    }

    public String getFxmlPath() {
        return fxmlPath;
    }
}

