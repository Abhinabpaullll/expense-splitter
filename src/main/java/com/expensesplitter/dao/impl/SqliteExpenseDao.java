package com.expensesplitter.dao.impl;

import com.expensesplitter.dao.ExpenseDao;
import com.expensesplitter.model.Expense;
import com.expensesplitter.model.ExpensePayer;
import com.expensesplitter.model.ExpenseSplit;
import com.expensesplitter.model.SplitType;
import com.expensesplitter.util.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteExpenseDao implements ExpenseDao {

    private final DatabaseManager databaseManager;

    public SqliteExpenseDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void save(Expense expense, List<ExpensePayer> payers, List<ExpenseSplit> splits) {
        try {
            databaseManager.inTransaction(conn -> {
                String sqlExp = "INSERT INTO expenses (id, group_id, title, amount_cents, category, date, split_type, notes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlExp)) {
                    ps.setString(1, expense.getId());
                    ps.setString(2, expense.getGroupId());
                    ps.setString(3, expense.getTitle());
                    ps.setLong(4, expense.getAmountCents());
                    ps.setString(5, expense.getCategory());
                    ps.setString(6, expense.getDate().toString());
                    ps.setString(7, expense.getSplitType().name());
                    ps.setString(8, expense.getNotes());
                    ps.setLong(9, expense.getCreatedAt());
                    ps.executeUpdate();
                }

                if (payers != null && !payers.isEmpty()) {
                    String sqlPayer = "INSERT INTO expense_payers (id, expense_id, member_id, amount_cents) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sqlPayer)) {
                        for (ExpensePayer p : payers) {
                            ps.setString(1, p.getId());
                            ps.setString(2, p.getExpenseId());
                            ps.setString(3, p.getMemberId());
                            ps.setLong(4, p.getAmountCents());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                if (splits != null && !splits.isEmpty()) {
                    String sqlSplit = "INSERT INTO expense_splits (id, expense_id, member_id, share_cents, exact_param) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sqlSplit)) {
                        for (ExpenseSplit s : splits) {
                            ps.setString(1, s.getId());
                            ps.setString(2, s.getExpenseId());
                            ps.setString(3, s.getMemberId());
                            ps.setLong(4, s.getShareCents());
                            ps.setBigDecimal(5, s.getExactParam());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                return null;
            });
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save expense: " + expense.getTitle(), e);
        }
    }

    @Override
    public void update(Expense expense, List<ExpensePayer> payers, List<ExpenseSplit> splits) {
        try {
            databaseManager.inTransaction(conn -> {
                String sqlExp = "UPDATE expenses SET group_id = ?, title = ?, amount_cents = ?, category = ?, date = ?, split_type = ?, notes = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sqlExp)) {
                    ps.setString(1, expense.getGroupId());
                    ps.setString(2, expense.getTitle());
                    ps.setLong(3, expense.getAmountCents());
                    ps.setString(4, expense.getCategory());
                    ps.setString(5, expense.getDate().toString());
                    ps.setString(6, expense.getSplitType().name());
                    ps.setString(7, expense.getNotes());
                    ps.setString(8, expense.getId());
                    ps.executeUpdate();
                }

                try (PreparedStatement psDelP = conn.prepareStatement("DELETE FROM expense_payers WHERE expense_id = ?");
                     PreparedStatement psDelS = conn.prepareStatement("DELETE FROM expense_splits WHERE expense_id = ?")) {
                    psDelP.setString(1, expense.getId());
                    psDelP.executeUpdate();
                    psDelS.setString(1, expense.getId());
                    psDelS.executeUpdate();
                }

                if (payers != null && !payers.isEmpty()) {
                    String sqlPayer = "INSERT INTO expense_payers (id, expense_id, member_id, amount_cents) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sqlPayer)) {
                        for (ExpensePayer p : payers) {
                            ps.setString(1, p.getId());
                            ps.setString(2, p.getExpenseId());
                            ps.setString(3, p.getMemberId());
                            ps.setLong(4, p.getAmountCents());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                if (splits != null && !splits.isEmpty()) {
                    String sqlSplit = "INSERT INTO expense_splits (id, expense_id, member_id, share_cents, exact_param) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sqlSplit)) {
                        for (ExpenseSplit s : splits) {
                            ps.setString(1, s.getId());
                            ps.setString(2, s.getExpenseId());
                            ps.setString(3, s.getMemberId());
                            ps.setLong(4, s.getShareCents());
                            ps.setBigDecimal(5, s.getExactParam());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                return null;
            });
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update expense: " + expense.getId(), e);
        }
    }

    @Override
    public void delete(String id) {
        String sql = "DELETE FROM expenses WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete expense: " + id, e);
        }
    }

    @Override
    public Optional<Expense> findById(String id) {
        String sql = "SELECT id, group_id, title, amount_cents, category, date, split_type, notes, created_at FROM expenses WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find expense: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Expense> findByGroupId(String groupId) {
        String sql = "SELECT id, group_id, title, amount_cents, category, date, split_type, notes, created_at FROM expenses WHERE group_id = ? ORDER BY date DESC, created_at DESC";
        List<Expense> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find expenses by group: " + groupId, e);
        }
        return list;
    }

    @Override
    public List<Expense> findAll() {
        String sql = "SELECT id, group_id, title, amount_cents, category, date, split_type, notes, created_at FROM expenses ORDER BY date DESC, created_at DESC";
        List<Expense> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list expenses", e);
        }
        return list;
    }

    @Override
    public List<ExpensePayer> findPayersByExpenseId(String expenseId) {
        String sql = "SELECT id, expense_id, member_id, amount_cents FROM expense_payers WHERE expense_id = ?";
        List<ExpensePayer> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, expenseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ExpensePayer(
                        rs.getString("id"),
                        rs.getString("expense_id"),
                        rs.getString("member_id"),
                        rs.getLong("amount_cents")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find payers for expense: " + expenseId, e);
        }
        return list;
    }

    @Override
    public List<ExpenseSplit> findSplitsByExpenseId(String expenseId) {
        String sql = "SELECT id, expense_id, member_id, share_cents, exact_param FROM expense_splits WHERE expense_id = ?";
        List<ExpenseSplit> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, expenseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ExpenseSplit(
                        rs.getString("id"),
                        rs.getString("expense_id"),
                        rs.getString("member_id"),
                        rs.getLong("share_cents"),
                        rs.getBigDecimal("exact_param")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find splits for expense: " + expenseId, e);
        }
        return list;
    }

    @Override
    public List<Expense> search(String groupId, String query, String category, String memberId, LocalDate startDate, LocalDate endDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT e.id, e.group_id, e.title, e.amount_cents, e.category, e.date, e.split_type, e.notes, e.created_at
            FROM expenses e
            LEFT JOIN expense_payers ep ON e.id = ep.expense_id
            LEFT JOIN expense_splits es ON e.id = es.expense_id
            WHERE 1=1
        """);
        List<Object> params = new ArrayList<>();

        if (groupId != null && !groupId.isBlank()) {
            sql.append(" AND e.group_id = ?");
            params.add(groupId);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (LOWER(e.title) LIKE ? OR LOWER(e.notes) LIKE ?)");
            String q = "%" + query.toLowerCase() + "%";
            params.add(q);
            params.add(q);
        }
        if (category != null && !category.isBlank() && !category.equalsIgnoreCase("all")) {
            sql.append(" AND LOWER(e.category) = LOWER(?)");
            params.add(category);
        }
        if (memberId != null && !memberId.isBlank()) {
            sql.append(" AND (ep.member_id = ? OR es.member_id = ?)");
            params.add(memberId);
            params.add(memberId);
        }
        if (startDate != null) {
            sql.append(" AND e.date >= ?");
            params.add(startDate.toString());
        }
        if (endDate != null) {
            sql.append(" AND e.date <= ?");
            params.add(endDate.toString());
        }

        sql.append(" ORDER BY e.date DESC, e.created_at DESC");

        List<Expense> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search expenses", e);
        }
        return list;
    }

    private Expense mapRow(ResultSet rs) throws SQLException {
        return new Expense(
            rs.getString("id"),
            rs.getString("group_id"),
            rs.getString("title"),
            rs.getLong("amount_cents"),
            rs.getString("category"),
            LocalDate.parse(rs.getString("date")),
            SplitType.fromString(rs.getString("split_type")),
            rs.getString("notes"),
            rs.getLong("created_at")
        );
    }
}

