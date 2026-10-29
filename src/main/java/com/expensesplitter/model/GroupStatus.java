package com.expensesplitter.model;

public enum GroupStatus {
    ACTIVE("Active"),
    SETTLED("Settled"),
    ARCHIVED("Archived");

    private final String displayName;

    GroupStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static GroupStatus fromString(String text) {
        if (text == null) return ACTIVE;
        for (GroupStatus s : GroupStatus.values()) {
            if (s.name().equalsIgnoreCase(text) || s.displayName.equalsIgnoreCase(text)) {
                return s;
            }
        }
        return ACTIVE;
    }
}

