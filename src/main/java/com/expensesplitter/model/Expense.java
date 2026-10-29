package com.expensesplitter.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public class Expense {
    private final String id;
    private final String groupId;
    private final String title;
    private final long amountCents; // in minor units
    private final String category;
    private final LocalDate date;
    private final SplitType splitType;
    private final String notes;
    private final long createdAt;

    public Expense(String id, String groupId, String title, long amountCents, String category,
                   LocalDate date, SplitType splitType, String notes, long createdAt) {
        this.id = id;
        this.groupId = groupId;
        this.title = title;
        this.amountCents = amountCents;
        this.category = (category == null || category.isBlank()) ? "General" : category;
        this.date = date != null ? date : LocalDate.now();
        this.splitType = splitType != null ? splitType : SplitType.EQUAL;
        this.notes = notes != null ? notes : "";
        this.createdAt = createdAt > 0 ? createdAt : Instant.now().toEpochMilli();
    }

    public Expense(String id, String groupId, String title, long amountCents, String category,
                   LocalDate date, SplitType splitType, String notes) {
        this(id, groupId, title, amountCents, category, date, splitType, notes, Instant.now().toEpochMilli());
    }

    public String getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getTitle() {
        return title;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public BigDecimal getAmountDecimal() {
        return BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public String getCategory() {
        return category;
    }

    public LocalDate getDate() {
        return date;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public String getNotes() {
        return notes;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Expense expense)) return false;
        return Objects.equals(id, expense.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

