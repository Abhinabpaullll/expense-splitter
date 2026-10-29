package com.expensesplitter.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

public class Reminder {
    private final String id;
    private final String groupId;
    private final String memberId;
    private final long amountCents;
    private final String status; // PENDING, SENT, DISMISSED, SNOOZED
    private final long scheduledAt;
    private final long sentAt;

    public Reminder(String id, String groupId, String memberId, long amountCents, String status, long scheduledAt, long sentAt) {
        this.id = id;
        this.groupId = groupId;
        this.memberId = memberId;
        this.amountCents = amountCents;
        this.status = status != null ? status : "PENDING";
        this.scheduledAt = scheduledAt > 0 ? scheduledAt : Instant.now().toEpochMilli();
        this.sentAt = sentAt;
    }

    public Reminder(String id, String groupId, String memberId, long amountCents) {
        this(id, groupId, memberId, amountCents, "PENDING", Instant.now().toEpochMilli(), 0);
    }

    public String getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getMemberId() {
        return memberId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public BigDecimal getAmountDecimal() {
        return BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public String getStatus() {
        return status;
    }

    public long getScheduledAt() {
        return scheduledAt;
    }

    public long getSentAt() {
        return sentAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reminder reminder)) return false;
        return Objects.equals(id, reminder.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

