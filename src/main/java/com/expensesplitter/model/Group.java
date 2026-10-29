package com.expensesplitter.model;

import java.time.Instant;
import java.util.Objects;

public class Group {
    private final String id;
    private final String name;
    private final String currency;
    private final GroupStatus status;
    private final String createdByUserId;
    private final long createdAt;
    private final long updatedAt;

    public Group(String id, String name, String currency, GroupStatus status, String createdByUserId, long createdAt, long updatedAt) {
        this.id = id;
        this.name = name;
        this.currency = (currency == null || currency.isBlank()) ? "INR" : currency;
        this.status = status != null ? status : GroupStatus.ACTIVE;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt > 0 ? createdAt : Instant.now().toEpochMilli();
        this.updatedAt = updatedAt > 0 ? updatedAt : this.createdAt;
    }

    public Group(String id, String name, String currency, GroupStatus status, long createdAt, long updatedAt) {
        this(id, name, currency, status, null, createdAt, updatedAt);
    }

    public Group(String id, String name, String currency, String createdByUserId) {
        this(id, name, currency, GroupStatus.ACTIVE, createdByUserId, Instant.now().toEpochMilli(), Instant.now().toEpochMilli());
    }

    public Group(String id, String name, String currency) {
        this(id, name, currency, GroupStatus.ACTIVE, null, Instant.now().toEpochMilli(), Instant.now().toEpochMilli());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public GroupStatus getStatus() {
        return status;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public String getCurrencySymbol() {
        return switch (currency.toUpperCase()) {
            case "INR" -> "₹";
            case "USD" -> "$";
            case "EUR" -> "€";
            case "GBP" -> "£";
            default -> currency;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Group group)) return false;
        return Objects.equals(id, group.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name;
    }
}

