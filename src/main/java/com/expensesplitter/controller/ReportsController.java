package com.expensesplitter.controller;

import com.expensesplitter.model.Expense;
import com.expensesplitter.model.ExpensePayer;
import com.expensesplitter.model.Group;
import com.expensesplitter.model.Member;
import com.expensesplitter.service.ExpenseService;
import com.expensesplitter.service.GroupService;
import com.expensesplitter.util.AppContext;
import com.expensesplitter.util.CsvExporter;
import com.expensesplitter.util.PdfExporter;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;

public class ReportsController implements Initializable, MainController.ParamReceiver {

    @FXML private ComboBox<GroupItem> reportGroupComboBox;
    @FXML private DatePicker reportStartDatePicker;
    @FXML private DatePicker reportEndDatePicker;

    @FXML private Label reportTotalAmountLabel;
    @FXML private Label reportTransactionCountLabel;
    @FXML private Label reportAverageAmountLabel;

    @FXML private VBox exportFeedbackBox;
    @FXML private Label exportFeedbackText;

    @FXML private TableView<ReportTableRow> reportTable;
    @FXML private TableColumn<ReportTableRow, String> colRepDate;
    @FXML private TableColumn<ReportTableRow, String> colRepTitle;
    @FXML private TableColumn<ReportTableRow, String> colRepCategory;
    @FXML private TableColumn<ReportTableRow, String> colRepPayer;
    @FXML private TableColumn<ReportTableRow, String> colRepAmount;

    private AppContext appContext;
    private final ObservableList<ReportTableRow> reportRows = FXCollections.observableArrayList();
    private List<Expense> currentFilteredExpenses = new ArrayList<>();
    private final Map<String, String> currentPayerNames = new HashMap<>();

    public record GroupItem(String id, String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    public record ReportTableRow(
        String date,
        String title,
        String category,
        String payer,
        String amount
    ) {}

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.appContext = AppContext.getInstance();
        setupTable();
        setupFilterControls();
        onApplyFilter();
    }

    @Override
    public void setNavigationParameter(Object param) {
        if (param instanceof Group g) {
            for (GroupItem item : reportGroupComboBox.getItems()) {
                if (item.id().equals(g.getId())) {
                    reportGroupComboBox.setValue(item);
                    break;
                }
            }
            onApplyFilter();
        }
    }

    private void setupTable() {
        colRepDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().date()));
        colRepTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().title()));
        colRepCategory.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().category()));
        colRepPayer.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().payer()));
        colRepAmount.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().amount()));
        reportTable.setItems(reportRows);
    }

    private void setupFilterControls() {
        reportGroupComboBox.getItems().clear();
        reportGroupComboBox.getItems().add(new GroupItem("", "All Groups"));

        for (Group g : appContext.getGroupService().getAllGroups()) {
            reportGroupComboBox.getItems().add(new GroupItem(g.getId(), g.getName()));
        }
        reportGroupComboBox.getSelectionModel().selectFirst();

        reportStartDatePicker.setValue(LocalDate.now().minusMonths(1));
        reportEndDatePicker.setValue(LocalDate.now().plusDays(1));
    }

    @FXML
    private void onApplyFilter() {
        GroupItem selected = reportGroupComboBox.getValue();
        String groupId = (selected != null && !selected.id().isEmpty()) ? selected.id() : null;
        LocalDate start = reportStartDatePicker.getValue();
        LocalDate end = reportEndDatePicker.getValue();

        ExpenseService expenseService = appContext.getExpenseService();
        currentFilteredExpenses = expenseService.search(groupId, null, null, null, start, end);

        Map<String, String> memberNames = new HashMap<>();
        for (Member m : appContext.getMemberDao().findAll()) {
            memberNames.put(m.getId(), m.getName());
        }

        currentPayerNames.clear();
        reportRows.clear();
        long totalCents = 0;

        for (Expense e : currentFilteredExpenses) {
            totalCents += e.getAmountCents();
            List<ExpensePayer> payers = expenseService.getPayers(e.getId());
            String payer = payers.isEmpty() ? "Unknown" : memberNames.getOrDefault(payers.get(0).getMemberId(), "Member");
            currentPayerNames.put(e.getId(), payer);

            String curSymbol = (groupId != null)
                ? appContext.getGroupService().getGroupById(groupId).map(Group::getCurrencySymbol).orElse(getCurrencySymbol())
                : getCurrencySymbol();
            reportRows.add(new ReportTableRow(
                e.getDate().toString(),
                e.getTitle(),
                e.getCategory(),
                payer,
                curSymbol + formatCurrency(e.getAmountCents())
            ));
        }

        String curSymbol = (groupId != null)
            ? appContext.getGroupService().getGroupById(groupId).map(Group::getCurrencySymbol).orElse(getCurrencySymbol())
            : getCurrencySymbol();
        int count = currentFilteredExpenses.size();
        long avgCents = (count > 0) ? (totalCents / count) : 0;

        reportTotalAmountLabel.setText(curSymbol + formatCurrency(totalCents));
        reportTransactionCountLabel.setText(String.valueOf(count));
        reportAverageAmountLabel.setText(curSymbol + formatCurrency(avgCents));
    }

    private String getCurrencySymbol() {
        return appContext.getSettingsDao().get("default_currency")
            .map(c -> c.contains("USD") ? "$" : (c.contains("EUR") ? "€" : (c.contains("GBP") ? "£" : "₹")))
            .orElse("₹");
    }

    @FXML
    private void onExportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save CSV Report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        chooser.setInitialFileName("Expense_Report_" + LocalDate.now() + ".csv");

        File file = chooser.showSaveDialog(reportTable.getScene().getWindow());
        if (file != null) {
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    CsvExporter.exportExpensesToCsv(file, currentFilteredExpenses, currentPayerNames);
                    return null;
                }
            };
            task.setOnSucceeded(e -> showFeedbackToast("CSV report saved to: " + file.getAbsolutePath()));
            task.setOnFailed(e -> showFeedbackToast("Error exporting CSV: " + task.getException().getMessage()));
            new Thread(task).start();
        }
    }

    @FXML
    private void onExportPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save PDF Report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));
        chooser.setInitialFileName("Expense_Report_" + LocalDate.now() + ".pdf");

        File file = chooser.showSaveDialog(reportTable.getScene().getWindow());
        if (file != null) {
            GroupItem selected = reportGroupComboBox.getValue();
            String groupName = (selected != null) ? selected.displayName() : "Consolidated";

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    PdfExporter.exportExpensesToPdf(file, groupName, currentFilteredExpenses, currentPayerNames);
                    return null;
                }
            };
            task.setOnSucceeded(e -> showFeedbackToast("PDF report saved to: " + file.getAbsolutePath()));
            task.setOnFailed(e -> showFeedbackToast("Error generating PDF: " + task.getException().getMessage()));
            new Thread(task).start();
        }
    }

    private void showFeedbackToast(String message) {
        exportFeedbackText.setText(message);
        exportFeedbackBox.setVisible(true);
        exportFeedbackBox.setManaged(true);

        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> {
            exportFeedbackBox.setVisible(false);
            exportFeedbackBox.setManaged(false);
        });
        pause.play();
    }

    private String formatCurrency(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
    }
}

