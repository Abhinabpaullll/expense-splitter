package com.expensesplitter.util;

import com.expensesplitter.model.Expense;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

public final class CsvExporter {

    private CsvExporter() {}

    public static void exportExpensesToCsv(File file, List<Expense> expenses, Map<String, String> payerNames) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("Date,Description,Category,Payer,Amount");
            for (Expense e : expenses) {
                String payer = payerNames.getOrDefault(e.getId(), "Member");
                String amt = BigDecimal.valueOf(e.getAmountCents()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",%s%n",
                    e.getDate(),
                    escapeCsv(e.getTitle()),
                    escapeCsv(e.getCategory()),
                    escapeCsv(payer),
                    amt
                );
            }
        }
    }

    private static String escapeCsv(String str) {
        if (str == null) return "";
        return str.replace("\"", "\"\"");
    }
}

