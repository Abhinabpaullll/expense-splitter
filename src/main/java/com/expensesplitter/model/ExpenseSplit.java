package com.expensesplitter.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class ExpenseSplit {
    private final String id;
    private final String expenseId;
    private final String memberId;
    private final long shareCents;
    private final BigDecimal exactParam; // For percentages (e.g. 25.0) or shares (e.g. 2.0) or exact amounts

    public ExpenseSplit(String id, String expenseId, String memberId, long shareCents, BigDecimal exactParam) {
        this.id = id;
        this.expenseId = expenseId;
        this.memberId = memberId;
        this.shareCents = shareCents;
        this.exactParam = exactParam != null ? exactParam : BigDecimal.ZERO;
    }

    public ExpenseSplit(String id, String expenseId, String memberId, long shareCents) {
        this(id, expenseId, memberId, shareCents, BigDecimal.ZERO);
    }

    public String getId() {
        return id;
    }

    public String getExpenseId() {
        return expenseId;
    }

    public String getMemberId() {
        return memberId;
    }

    public long getShareCents() {
        return shareCents;
    }

    public BigDecimal getShareDecimal() {
        return BigDecimal.valueOf(shareCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getExactParam() {
        return exactParam;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpenseSplit that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

