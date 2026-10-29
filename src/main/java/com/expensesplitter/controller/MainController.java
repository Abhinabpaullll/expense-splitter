package com.expensesplitter.controller;

import com.expensesplitter.model.Group;
import com.expensesplitter.model.Member;
import com.expensesplitter.service.NavigationService;
import com.expensesplitter.service.ViewType;
import com.expensesplitter.util.AppContext;
import com.expensesplitter.util.ThemeManager;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class MainController implements Initializable, NavigationService.ModalRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private StackPane rootStackPane;
    @FXML private BorderPane mainContainer;
    @FXML private VBox navContainer;

    @FXML private Button navDashboardBtn;
    @FXML private Button navGroupsBtn;
    @FXML private Button navRemindersBtn;
    @FXML private Button navReportsBtn;
    @FXML private Button navSettingsBtn;

    @FXML private javafx.scene.layout.HBox userProfileBox;
    @FXML private Label userAvatarLabel;
    @FXML private Label userNameLabel;
    @FXML private Label userEmailLabel;

    @FXML private TextField globalSearchField;
    @FXML private Button addExpenseQuickBtn;
    @FXML private Button themeToggleBtn;

    @FXML private StackPane contentArea;
    @FXML private StackPane modalOverlayLayer;
    @FXML private StackPane modalContentHolder;

    private AppContext appContext;
    private NavigationService navigationService;
    private final Map<ViewType, Button> navButtons = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.appContext = AppContext.getInstance();
        this.navigationService = appContext.getNavigationService();
        this.navigationService.setModalRequestHandler(this);

        // Setup sidebar icons
        navDashboardBtn.setGraphic(new FontIcon(MaterialDesignV.VIEW_DASHBOARD_OUTLINE));
        navGroupsBtn.setGraphic(new FontIcon(MaterialDesignA.ACCOUNT_GROUP_OUTLINE));
        navRemindersBtn.setGraphic(new FontIcon(MaterialDesignB.BELL_OUTLINE));
        navReportsBtn.setGraphic(new FontIcon(MaterialDesignF.FILE_DOCUMENT_OUTLINE));
        navSettingsBtn.setGraphic(new FontIcon(MaterialDesignC.COG_OUTLINE));
        addExpenseQuickBtn.setGraphic(new FontIcon(MaterialDesignP.PLUS));

        navButtons.put(ViewType.DASHBOARD, navDashboardBtn);
        navButtons.put(ViewType.GROUPS, navGroupsBtn);
        navButtons.put(ViewType.GROUP_DETAIL, navGroupsBtn);
        navButtons.put(ViewType.SETTLE_UP, navGroupsBtn);
        navButtons.put(ViewType.REMINDERS, navRemindersBtn);
        navButtons.put(ViewType.REPORTS, navReportsBtn);
        navButtons.put(ViewType.SETTINGS, navSettingsBtn);

        // Session listener for user profile header and sidebar updates
        appContext.getSessionManager().addSessionListener(user -> {
            Platform.runLater(() -> {
                if (user != null) {
                    userAvatarLabel.setText(user.getInitials());
                    userNameLabel.setText(user.displayName());
                    userEmailLabel.setText(user.email() != null ? user.email() : "Offline Profile");
                } else {
                    userAvatarLabel.setText("--");
                    userNameLabel.setText("Guest");
                    userEmailLabel.setText("Not signed in");
                }
            });
        });

        updateThemeButtonLabel();

        // Listen for navigation changes
        navigationService.addNavigationListener(this::loadView);

        // Listen for pending reminder counts to display in sidebar
        appContext.getReminderService().addPendingCountListener(count -> {
            if (count > 0) {
                navRemindersBtn.setText("Reminders (" + count + ")");
            } else {
                navRemindersBtn.setText("Reminders");
            }
        });

        // Load default view (Auth if not signed in, else Dashboard)
        if (!appContext.getSessionManager().isSignedIn()) {
            loadView(ViewType.AUTH, null);
        } else {
            loadView(ViewType.DASHBOARD, null);
        }

        // Configure global keyboard shortcuts
        Platform.runLater(this::setupKeyboardShortcuts);
    }

    private void setupKeyboardShortcuts() {
        if (rootStackPane.getScene() == null) return;

        KeyCombination newExpenseCombo = new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN);
        KeyCombination searchCombo = new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN);

        rootStackPane.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (newExpenseCombo.match(event)) {
                openAddExpenseDialog(null);
                event.consume();
            } else if (searchCombo.match(event)) {
                globalSearchField.requestFocus();
                globalSearchField.selectAll();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                if (modalOverlayLayer.isVisible()) {
                    closeModal();
                    event.consume();
                }
            }
        });
    }

    private void loadView(ViewType viewType, Object param) {
        // Update active nav styling
        for (Map.Entry<ViewType, Button> entry : navButtons.entrySet()) {
            entry.getValue().getStyleClass().remove("active");
        }
        Button activeBtn = navButtons.get(viewType);
        if (activeBtn != null && !activeBtn.getStyleClass().contains("active")) {
            activeBtn.getStyleClass().add("active");
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(viewType.getFxmlPath()));
            Parent view = loader.load();

            Object controller = loader.getController();
            if (controller instanceof ParamReceiver receiver) {
                receiver.setNavigationParameter(param);
            }

            contentArea.getChildren().setAll(view);
            logger.info("Loaded view: {}", viewType);
        } catch (IOException e) {
            logger.error("Failed to load view FXML: " + viewType.getFxmlPath(), e);
        }
    }

    @FXML
    private void onNavigateDashboard() {
        navigationService.navigateTo(ViewType.DASHBOARD);
    }

    @FXML
    private void onNavigateGroups() {
        navigationService.navigateTo(ViewType.GROUPS);
    }

    @FXML
    private void onNavigateReminders() {
        navigationService.navigateTo(ViewType.REMINDERS);
    }

    @FXML
    private void onNavigateReports() {
        navigationService.navigateTo(ViewType.REPORTS);
    }

    @FXML
    private void onNavigateSettings() {
        navigationService.navigateTo(ViewType.SETTINGS);
    }

    @FXML
    private void onQuickAddExpense() {
        openAddExpenseDialog(null);
    }

    @FXML
    private void onToggleTheme() {
        ThemeManager.toggleTheme(rootStackPane.getScene());
        updateThemeButtonLabel();
    }

    @FXML
    private void onUserMenuClick() {
        javafx.scene.control.ContextMenu userMenu = new javafx.scene.control.ContextMenu();

        javafx.scene.control.MenuItem switchItem = new javafx.scene.control.MenuItem("Switch account");
        switchItem.setOnAction(e -> {
            appContext.getSessionManager().signOut();
            navigationService.navigateTo(ViewType.AUTH);
        });

        javafx.scene.control.MenuItem addItem = new javafx.scene.control.MenuItem("Add account");
        addItem.setOnAction(e -> {
            navigationService.navigateTo(ViewType.AUTH, "register");
        });

        javafx.scene.control.MenuItem signOutItem = new javafx.scene.control.MenuItem("Sign out");
        signOutItem.setOnAction(e -> {
            appContext.getSessionManager().signOut();
            navigationService.navigateTo(ViewType.AUTH);
        });

        userMenu.getItems().addAll(switchItem, addItem, new javafx.scene.control.SeparatorMenuItem(), signOutItem);
        if (userProfileBox != null) {
            userMenu.show(userProfileBox, javafx.geometry.Side.TOP, 0, -10);
        }
    }

    private void updateThemeButtonLabel() {
        themeToggleBtn.setText(ThemeManager.isDarkMode() ? "Light theme" : "Dark theme");
    }

    @Override
    public void openAddExpenseDialog(Object target) {
        openModal("/fxml/AddExpenseDialog.fxml", target);
    }

    public void openRecordPaymentDialog(Group targetGroup) {
        openModal("/fxml/RecordPaymentDialog.fxml", targetGroup);
    }

    public void openModal(String fxmlPath, Object param) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent modalNode = loader.load();

            Object controller = loader.getController();
            if (controller instanceof ModalController modalCtrl) {
                modalCtrl.setMainController(this);
            }
            if (controller instanceof ParamReceiver receiver) {
                receiver.setNavigationParameter(param);
            }

            modalContentHolder.getChildren().setAll(modalNode);
            modalOverlayLayer.setVisible(true);
            modalOverlayLayer.setMouseTransparent(false);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(120), modalOverlayLayer);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        } catch (IOException e) {
            logger.error("Failed to open modal: " + fxmlPath, e);
        }
    }

    public void closeModal() {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(100), modalOverlayLayer);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> {
            modalOverlayLayer.setVisible(false);
            modalOverlayLayer.setMouseTransparent(true);
            modalContentHolder.getChildren().clear();
        });
        fadeOut.play();

        // Refresh current view in case data changed
        loadView(navigationService.getCurrentView(), navigationService.getCurrentParameter());
    }

    public interface ParamReceiver {
        void setNavigationParameter(Object param);
    }

    public interface ModalController {
        void setMainController(MainController mainController);
    }
}

