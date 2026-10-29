package com.expensesplitter.dao.impl;

import com.expensesplitter.dao.UserDao;
import com.expensesplitter.model.User;
import com.expensesplitter.util.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteUserDao implements UserDao {

    private static final Logger logger = LoggerFactory.getLogger(SqliteUserDao.class);
    private final DatabaseManager dbManager;

    public SqliteUserDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public void save(User user) {
        String sql = """
            INSERT INTO users (id, display_name, email, password_hash, salt, created_at, last_login_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.id());
            stmt.setString(2, user.displayName());
            stmt.setString(3, user.email());
            stmt.setString(4, user.passwordHash());
            stmt.setString(5, user.salt());
            stmt.setLong(6, user.createdAt());
            if (user.lastLoginAt() != null) {
                stmt.setLong(7, user.lastLoginAt());
            } else {
                stmt.setNull(7, Types.INTEGER);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving user", e);
            throw new RuntimeException("Failed to save user account", e);
        }
    }

    @Override
    public void update(User user) {
        String sql = """
            UPDATE users
            SET display_name = ?, email = ?, password_hash = ?, salt = ?, last_login_at = ?
            WHERE id = ?
        """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.displayName());
            stmt.setString(2, user.email());
            stmt.setString(3, user.passwordHash());
            stmt.setString(4, user.salt());
            if (user.lastLoginAt() != null) {
                stmt.setLong(5, user.lastLoginAt());
            } else {
                stmt.setNull(5, Types.INTEGER);
            }
            stmt.setString(6, user.id());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating user", e);
            throw new RuntimeException("Failed to update user account", e);
        }
    }

    @Override
    public void updateLastLogin(String userId, long timestamp) {
        String sql = "UPDATE users SET last_login_at = ? WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, timestamp);
            stmt.setString(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating last login", e);
            throw new RuntimeException("Failed to update user last login", e);
        }
    }

    @Override
    public Optional<User> findById(String id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by id", e);
            throw new RuntimeException("Database error finding user", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM users WHERE lower(email) = lower(?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by email", e);
            throw new RuntimeException("Database error finding user by email", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findByDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM users WHERE lower(display_name) = lower(?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, displayName.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by display name", e);
            throw new RuntimeException("Database error finding user by display name", e);
        }
        return Optional.empty();
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY created_at ASC";
        List<User> users = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all users", e);
            throw new RuntimeException("Database error loading user accounts", e);
        }
        return users;
    }

    @Override
    public void delete(String id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting user", e);
            throw new RuntimeException("Failed to delete user account", e);
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        long lastLoginVal = rs.getLong("last_login_at");
        Long lastLogin = rs.wasNull() ? null : lastLoginVal;
        return new User(
            rs.getString("id"),
            rs.getString("display_name"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("salt"),
            rs.getLong("created_at"),
            lastLogin
        );
    }
}

