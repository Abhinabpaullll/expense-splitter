package com.expensesplitter.model;

public enum PaymentMethod {
    UPI("UPI"),
    BANK_TRANSFER("Bank Transfer"),
    CASH("Cash"),
    OTHER("Other");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PaymentMethod fromString(String text) {
        if (text == null) return CASH;
        for (PaymentMethod m : PaymentMethod.values()) {
            if (m.name().equalsIgnoreCase(text) || m.displayName.equalsIgnoreCase(text)) {
                return m;
            }
        }
        return CASH;
    }
}

