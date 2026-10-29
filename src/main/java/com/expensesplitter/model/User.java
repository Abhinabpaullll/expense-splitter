package com.expensesplitter.model;

/**
 * Registered application user account.
 */
public record User(
    String id,
    String displayName,
    String email,
    String passwordHash,
    String salt,
    long createdAt,
    Long lastLoginAt
) {
    public String getInitials() {
        if (displayName == null || displayName.isBlank()) return "?";
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}

