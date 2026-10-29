package com.expensesplitter.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public class Payment {
    private final String id;
    private final String groupId;
    private final String fromMemberId;
    private final String toMemberId;
    private final long amountCents;
    private final PaymentMethod paymentMethod;
    private final LocalDate date;
    private final String notes;
    private final long createdAt;

    public Payment(String id, String groupId, String fromMemberId, String toMemberId,
                   long amountCents, PaymentMethod paymentMethod, LocalDate date, String notes, long createdAt) {
        this.id = id;
        this.groupId = groupId;
        this.fromMemberId = fromMemberId;
        this.toMemberId = toMemberId;
        this.amountCents = amountCents;
        this.paymentMethod = paymentMethod != null ? paymentMethod : PaymentMethod.CASH;
        this.date = date != null ? date : LocalDate.now();
        this.notes = notes != null ? notes : "";
        this.createdAt = createdAt > 0 ? createdAt : Instant.now().toEpochMilli();
    }

    public Payment(String id, String groupId, String fromMemberId, String toMemberId,
                   long amountCents, PaymentMethod paymentMethod, LocalDate date, String notes) {
        this(id, groupId, fromMemberId, toMemberId, amountCents, paymentMethod, date, notes, Instant.now().toEpochMilli());
    }

    public String getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getFromMemberId() {
        return fromMemberId;
    }

    public String getToMemberId() {
        return toMemberId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public BigDecimal getAmountDecimal() {
        return BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public LocalDate getDate() {
        return date;
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
        if (!(o instanceof Payment payment)) return false;
        return Objects.equals(id, payment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

