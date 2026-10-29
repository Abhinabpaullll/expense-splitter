package com.expensesplitter.model;

import java.time.Instant;
import java.util.Objects;

public class Member {
    private final String id;
    private final String name;
    private final String email;
    private final String phone;
    private final String userId;
    private final long createdAt;

    public Member(String id, String name, String email, String phone, String userId, long createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.userId = userId;
        this.createdAt = createdAt > 0 ? createdAt : Instant.now().toEpochMilli();
    }

    public Member(String id, String name, String email, String phone, long createdAt) {
        this(id, name, email, phone, null, createdAt);
    }

    public Member(String id, String name, String email, String phone) {
        this(id, name, email, phone, null, Instant.now().toEpochMilli());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getUserId() {
        return userId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getInitials() {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member member)) return false;
        return Objects.equals(id, member.id);
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

