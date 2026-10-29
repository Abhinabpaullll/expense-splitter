package com.expensesplitter.dao.impl;

import com.expensesplitter.dao.SettingsDao;
import com.expensesplitter.util.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class SqliteSettingsDao implements SettingsDao {

    private static final Logger logger = LoggerFactory.getLogger(SqliteSettingsDao.class);
    private final DatabaseManager dbManager;

    public SqliteSettingsDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public Optional<String> get(String key) {
        String sql = "SELECT value FROM settings WHERE key = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("value"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error reading setting: " + key, e);
        }
        return Optional.empty();
    }

    @Override
    public void set(String key, String value) {
        String sql = """
            INSERT INTO settings (key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
        """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving setting: " + key, e);
            throw new RuntimeException("Failed to save setting: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        String sql = "DELETE FROM settings WHERE key = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting setting: " + key, e);
            throw new RuntimeException("Failed to delete setting: " + key, e);
        }
    }
}

