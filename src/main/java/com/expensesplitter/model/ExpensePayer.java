package com.expensesplitter.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class ExpensePayer {
    private final String id;
    private final String expenseId;
    private final String memberId;
    private final long amountCents;

    public ExpensePayer(String id, String expenseId, String memberId, long amountCents) {
        this.id = id;
        this.expenseId = expenseId;
        this.memberId = memberId;
        this.amountCents = amountCents;
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

    public long getAmountCents() {
        return amountCents;
    }

    public BigDecimal getAmountDecimal() {
        return BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpensePayer that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

