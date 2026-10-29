package com.expensesplitter.model;

public enum SplitType {
    EQUAL("Equal"),
    EXACT("Exact"),
    PERCENT("Percent"),
    SHARES("Shares");

    private final String displayName;

    SplitType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SplitType fromString(String text) {
        if (text == null) return EQUAL;
        for (SplitType b : SplitType.values()) {
            if (b.name().equalsIgnoreCase(text) || b.displayName.equalsIgnoreCase(text)) {
                return b;
            }
        }
        return EQUAL;
    }
}

