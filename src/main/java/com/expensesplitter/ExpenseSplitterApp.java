package com.expensesplitter;

import com.expensesplitter.controller.MainController;
import com.expensesplitter.service.ViewType;
import com.expensesplitter.util.AppContext;
import com.expensesplitter.util.FontLoader;
import com.expensesplitter.util.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class ExpenseSplitterApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(ExpenseSplitterApp.class);

    @Override
    public void init() {
        FontLoader.loadFonts();
        ThemeManager.initTheme();
        AppContext.getInstance(); // Initialize database and seed demo data

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("Uncaught exception on thread: " + thread.getName(), throwable);
            Platform.runLater(() -> {
                try {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Expense Splitter Error");
                    alert.setHeaderText("An unexpected error occurred");
                    alert.setContentText(throwable.getMessage() != null ? throwable.getMessage() : throwable.toString());
                    alert.showAndWait();
                } catch (Exception ex) {
                    logger.error("Failed to show error alert dialog", ex);
                }
            });
        });
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Expense Splitter");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);

        try {
            // Check for legacy sample data
            if (AppContext.getInstance().getDatabaseManager().hasLegacyDummyData()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Legacy Data Detected");
                alert.setHeaderText("Old sample data was found");
                alert.setContentText("Old sample data was found. Back up and remove it?");
                java.util.Optional<ButtonType> res = alert.showAndWait();
                if (res.isPresent() && res.get() == ButtonType.OK) {
                    AppContext.getInstance().getDatabaseManager().cleanLegacyDataAndRecreateSchema(true);
                }
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainLayout.fxml"));
            Parent root = loader.load();
            MainController mainController = loader.getController();

            Scene scene = new Scene(root, 1180, 760);
            ThemeManager.applyStylesheets(scene);

            primaryStage.setScene(scene);
            primaryStage.show();

            // Ensure Auth screen is loaded if no active session
            if (!AppContext.getInstance().getSessionManager().isSignedIn()) {
                AppContext.getInstance().getNavigationService().navigateTo(ViewType.AUTH);
            }

            if (getParameters().getUnnamed().contains("--capture-all")) {
                new Thread(() -> {
                    try {
                        Thread.sleep(1500);
                        captureAllScreens(primaryStage, scene, mainController);
                        Thread.sleep(1000);
                        Platform.runLater(primaryStage::close);
                    } catch (Exception e) {
                        logger.error("Capture thread error", e);
                    }
                }).start();
            } else if (getParameters().getUnnamed().contains("--auto-exit")) {
                new Thread(() -> {
                    try {
                        Thread.sleep(2500);
                        Platform.runLater(primaryStage::close);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
        } catch (IOException e) {
            logger.error("Failed to launch Expense Splitter", e);
        }
    }

    private void captureAllScreens(Stage stage, Scene scene, MainController controller) {
        File dir = new File("screenshots");
        if (!dir.exists()) dir.mkdirs();

        ViewType[] views = {
            ViewType.DASHBOARD,
            ViewType.GROUPS,
            ViewType.GROUP_DETAIL,
            ViewType.SETTLE_UP,
            ViewType.REMINDERS,
            ViewType.REPORTS,
            ViewType.SETTINGS
        };

        for (ThemeManager.AppTheme theme : new ThemeManager.AppTheme[]{ThemeManager.AppTheme.LIGHT, ThemeManager.AppTheme.DARK}) {
            String themePrefix = (theme == ThemeManager.AppTheme.LIGHT) ? "light" : "dark";

            Platform.runLater(() -> ThemeManager.setTheme(theme, scene));
            sleep(400);

            for (ViewType v : views) {
                Platform.runLater(() -> AppContext.getInstance().getNavigationService().navigateTo(v));
                sleep(400);

                Platform.runLater(() -> {
                    String filename = String.format("screenshots/%s-%s.png", themePrefix, v.name().toLowerCase());
                    saveSceneSnapshot(scene, new File(filename));
                });
                sleep(200);
            }

            // Also capture Add Expense modal dialog
            Platform.runLater(() -> controller.openAddExpenseDialog(null));
            sleep(500);
            Platform.runLater(() -> {
                String modalFile = String.format("screenshots/%s-add_expense.png", themePrefix);
                saveSceneSnapshot(scene, new File(modalFile));
                controller.closeModal();
            });
            sleep(300);
        }

        logger.info("All screen captures completed successfully.");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void saveSceneSnapshot(Scene scene, File targetFile) {
        WritableImage image = scene.snapshot(null);
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", targetFile);
            logger.info("Saved screenshot: {}", targetFile.getName());
        } catch (IOException e) {
            logger.error("Error writing screenshot image: " + targetFile.getName(), e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
