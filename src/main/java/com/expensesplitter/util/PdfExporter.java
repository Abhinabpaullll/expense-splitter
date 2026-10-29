package com.expensesplitter.util;

import com.expensesplitter.model.Expense;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

public final class PdfExporter {

    private PdfExporter() {}

    public static void exportExpensesToPdf(File file, String groupName, List<Expense> expenses, Map<String, String> payerNames) throws Exception {
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, new FileOutputStream(file));
        document.open();

        // Fonts
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(0x1A, 0x1D, 0x1F));
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(0x5B, 0x61, 0x66));
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(0xFF, 0xFF, 0xFF));
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(0x1A, 0x1D, 0x1F));
        Font monoFont = FontFactory.getFont(FontFactory.COURIER, 9, new Color(0x1A, 0x1D, 0x1F));

        // Header
        Paragraph title = new Paragraph("Expense Splitter — Ledger Statement", titleFont);
        title.setSpacingAfter(4);
        document.add(title);

        Paragraph scope = new Paragraph("Group: " + groupName + " | Generated on: " + java.time.LocalDate.now(), subFont);
        scope.setSpacingAfter(16);
        document.add(scope);

        // Table
        PdfPTable table = new PdfPTable(new float[]{2f, 4f, 2.5f, 2.5f, 2.5f});
        table.setWidthPercentage(100);

        String[] headers = {"Date", "Description", "Category", "Paid by", "Amount (INR)"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new Color(0x1B, 0x6B, 0x5A)); // Deep Botanical Teal
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(cell);
        }

        long totalCents = 0;
        for (Expense e : expenses) {
            totalCents += e.getAmountCents();
            String payer = payerNames.getOrDefault(e.getId(), "Member");
            String amtStr = BigDecimal.valueOf(e.getAmountCents()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();

            table.addCell(createCell(e.getDate().toString(), monoFont, Element.ALIGN_LEFT));
            table.addCell(createCell(e.getTitle(), bodyFont, Element.ALIGN_LEFT));
            table.addCell(createCell(e.getCategory(), bodyFont, Element.ALIGN_LEFT));
            table.addCell(createCell(payer, bodyFont, Element.ALIGN_LEFT));
            table.addCell(createCell(amtStr, monoFont, Element.ALIGN_RIGHT));
        }

        // Total Row
        String totalFormatted = BigDecimal.valueOf(totalCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
        PdfPCell totalLabelCell = new PdfPCell(new Phrase("Total Expenditure", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
        totalLabelCell.setColspan(4);
        totalLabelCell.setPadding(6);
        totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(totalLabelCell);

        PdfPCell totalValCell = new PdfPCell(new Phrase(BigDecimal.valueOf(totalCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString(), FontFactory.getFont(FontFactory.COURIER_BOLD, 9)));
        totalValCell.setPadding(6);
        totalValCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(totalValCell);

        document.add(table);
        document.close();
    }

    private static PdfPCell createCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }
}
