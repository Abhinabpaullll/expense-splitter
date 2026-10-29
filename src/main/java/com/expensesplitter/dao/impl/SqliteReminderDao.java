package com.expensesplitter.dao.impl;

import com.expensesplitter.dao.ReminderDao;
import com.expensesplitter.model.Reminder;
import com.expensesplitter.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteReminderDao implements ReminderDao {

    private final DatabaseManager databaseManager;

    public SqliteReminderDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void save(Reminder reminder) {
        String sql = "INSERT INTO reminders (id, group_id, member_id, amount_cents, status, scheduled_at, sent_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reminder.getId());
            ps.setString(2, reminder.getGroupId());
            ps.setString(3, reminder.getMemberId());
            ps.setLong(4, reminder.getAmountCents());
            ps.setString(5, reminder.getStatus());
            ps.setLong(6, reminder.getScheduledAt());
            ps.setLong(7, reminder.getSentAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save reminder: " + reminder.getId(), e);
        }
    }

    @Override
    public void update(Reminder reminder) {
        String sql = "UPDATE reminders SET group_id = ?, member_id = ?, amount_cents = ?, status = ?, scheduled_at = ?, sent_at = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reminder.getGroupId());
            ps.setString(2, reminder.getMemberId());
            ps.setLong(3, reminder.getAmountCents());
            ps.setString(4, reminder.getStatus());
            ps.setLong(5, reminder.getScheduledAt());
            ps.setLong(6, reminder.getSentAt());
            ps.setString(7, reminder.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update reminder: " + reminder.getId(), e);
        }
    }

    @Override
    public void delete(String id) {
        String sql = "DELETE FROM reminders WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete reminder: " + id, e);
        }
    }

    @Override
    public Optional<Reminder> findById(String id) {
        String sql = "SELECT id, group_id, member_id, amount_cents, status, scheduled_at, sent_at FROM reminders WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find reminder: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Reminder> findByGroupId(String groupId) {
        String sql = "SELECT id, group_id, member_id, amount_cents, status, scheduled_at, sent_at FROM reminders WHERE group_id = ? ORDER BY scheduled_at DESC";
        List<Reminder> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find reminders for group: " + groupId, e);
        }
        return list;
    }

    @Override
    public List<Reminder> findPending() {
        String sql = "SELECT id, group_id, member_id, amount_cents, status, scheduled_at, sent_at FROM reminders WHERE status = 'PENDING' ORDER BY scheduled_at ASC";
        List<Reminder> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find pending reminders", e);
        }
        return list;
    }

    @Override
    public List<Reminder> findAll() {
        String sql = "SELECT id, group_id, member_id, amount_cents, status, scheduled_at, sent_at FROM reminders ORDER BY scheduled_at DESC";
        List<Reminder> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list reminders", e);
        }
        return list;
    }

    private Reminder mapRow(ResultSet rs) throws SQLException {
        return new Reminder(
            rs.getString("id"),
            rs.getString("group_id"),
            rs.getString("member_id"),
            rs.getLong("amount_cents"),
            rs.getString("status"),
            rs.getLong("scheduled_at"),
            rs.getLong("sent_at")
        );
    }
}

