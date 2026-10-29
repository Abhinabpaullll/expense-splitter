package com.expensesplitter.dao.impl;

import com.expensesplitter.dao.PaymentDao;
import com.expensesplitter.model.Payment;
import com.expensesplitter.model.PaymentMethod;
import com.expensesplitter.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqlitePaymentDao implements PaymentDao {

    private final DatabaseManager databaseManager;

    public SqlitePaymentDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void save(Payment payment) {
        String sql = "INSERT INTO payments (id, group_id, from_member_id, to_member_id, amount_cents, payment_method, date, notes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, payment.getId());
            ps.setString(2, payment.getGroupId());
            ps.setString(3, payment.getFromMemberId());
            ps.setString(4, payment.getToMemberId());
            ps.setLong(5, payment.getAmountCents());
            ps.setString(6, payment.getPaymentMethod().name());
            ps.setString(7, payment.getDate().toString());
            ps.setString(8, payment.getNotes());
            ps.setLong(9, payment.getCreatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save payment: " + payment.getId(), e);
        }
    }

    @Override
    public void update(Payment payment) {
        String sql = "UPDATE payments SET group_id = ?, from_member_id = ?, to_member_id = ?, amount_cents = ?, payment_method = ?, date = ?, notes = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, payment.getGroupId());
            ps.setString(2, payment.getFromMemberId());
            ps.setString(3, payment.getToMemberId());
            ps.setLong(4, payment.getAmountCents());
            ps.setString(5, payment.getPaymentMethod().name());
            ps.setString(6, payment.getDate().toString());
            ps.setString(7, payment.getNotes());
            ps.setString(8, payment.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update payment: " + payment.getId(), e);
        }
    }

    @Override
    public void delete(String id) {
        String sql = "DELETE FROM payments WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete payment: " + id, e);
        }
    }

    @Override
    public Optional<Payment> findById(String id) {
        String sql = "SELECT id, group_id, from_member_id, to_member_id, amount_cents, payment_method, date, notes, created_at FROM payments WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find payment: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Payment> findByGroupId(String groupId) {
        String sql = "SELECT id, group_id, from_member_id, to_member_id, amount_cents, payment_method, date, notes, created_at FROM payments WHERE group_id = ? ORDER BY date DESC, created_at DESC";
        List<Payment> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find payments by group: " + groupId, e);
        }
        return list;
    }

    @Override
    public List<Payment> findAll() {
        String sql = "SELECT id, group_id, from_member_id, to_member_id, amount_cents, payment_method, date, notes, created_at FROM payments ORDER BY date DESC, created_at DESC";
        List<Payment> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list payments", e);
        }
        return list;
    }

    private Payment mapRow(ResultSet rs) throws SQLException {
        return new Payment(
            rs.getString("id"),
            rs.getString("group_id"),
            rs.getString("from_member_id"),
            rs.getString("to_member_id"),
            rs.getLong("amount_cents"),
            PaymentMethod.fromString(rs.getString("payment_method")),
            LocalDate.parse(rs.getString("date")),
            rs.getString("notes"),
            rs.getLong("created_at")
        );
    }
}

