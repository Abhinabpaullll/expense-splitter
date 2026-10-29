package com.expensesplitter.dao.impl;

import com.expensesplitter.dao.GroupDao;
import com.expensesplitter.model.Group;
import com.expensesplitter.model.GroupStatus;
import com.expensesplitter.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteGroupDao implements GroupDao {

    private final DatabaseManager databaseManager;

    public SqliteGroupDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void save(Group group) {
        String sql = "INSERT INTO groups (id, name, currency, status, created_by_user_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, group.getId());
            ps.setString(2, group.getName());
            ps.setString(3, group.getCurrency());
            ps.setString(4, group.getStatus().name());
            ps.setString(5, group.getCreatedByUserId());
            ps.setLong(6, group.getCreatedAt());
            ps.setLong(7, group.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save group: " + group.getName(), e);
        }
    }

    @Override
    public void update(Group group) {
        String sql = "UPDATE groups SET name = ?, currency = ?, status = ?, created_by_user_id = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, group.getName());
            ps.setString(2, group.getCurrency());
            ps.setString(3, group.getStatus().name());
            ps.setString(4, group.getCreatedByUserId());
            ps.setLong(5, System.currentTimeMillis());
            ps.setString(6, group.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update group: " + group.getId(), e);
        }
    }

    @Override
    public void delete(String id) {
        String sql = "DELETE FROM groups WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete group: " + id, e);
        }
    }

    @Override
    public Optional<Group> findById(String id) {
        String sql = "SELECT id, name, currency, status, created_by_user_id, created_at, updated_at FROM groups WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find group: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Group> findAll() {
        String sql = "SELECT id, name, currency, status, created_by_user_id, created_at, updated_at FROM groups ORDER BY updated_at DESC";
        List<Group> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list groups", e);
        }
        return list;
    }

    @Override
    public List<Group> findByStatus(GroupStatus status) {
        String sql = "SELECT id, name, currency, status, created_by_user_id, created_at, updated_at FROM groups WHERE status = ? ORDER BY updated_at DESC";
        List<Group> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find groups by status: " + status, e);
        }
        return list;
    }

    @Override
    public List<Group> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) return findAll();
        String sql = """
            SELECT DISTINCT g.id, g.name, g.currency, g.status, g.created_by_user_id, g.created_at, g.updated_at
            FROM groups g
            LEFT JOIN group_members gm ON g.id = gm.group_id
            LEFT JOIN members m ON gm.member_id = m.id
            WHERE g.created_by_user_id = ? OR m.user_id = ? OR m.id = ?
            ORDER BY g.updated_at DESC
        """;
        List<Group> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, userId);
            ps.setString(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find groups for user: " + userId, e);
        }
        return list;
    }

    @Override
    public List<Group> findByUserIdAndStatus(String userId, GroupStatus status) {
        if (userId == null || userId.isBlank()) return findByStatus(status);
        String sql = """
            SELECT DISTINCT g.id, g.name, g.currency, g.status, g.created_by_user_id, g.created_at, g.updated_at
            FROM groups g
            LEFT JOIN group_members gm ON g.id = gm.group_id
            LEFT JOIN members m ON gm.member_id = m.id
            WHERE (g.created_by_user_id = ? OR m.user_id = ? OR m.id = ?) AND g.status = ?
            ORDER BY g.updated_at DESC
        """;
        List<Group> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, userId);
            ps.setString(3, userId);
            ps.setString(4, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find groups for user and status: " + userId, e);
        }
        return list;
    }

    @Override
    public void addMemberToGroup(String groupId, String memberId) {
        String sql = "INSERT OR IGNORE INTO group_members (group_id, member_id) VALUES (?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add member to group", e);
        }
    }

    @Override
    public void removeMemberFromGroup(String groupId, String memberId) {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND member_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove member from group", e);
        }
    }

    @Override
    public boolean isMemberInGroup(String groupId, String memberId) {
        String sql = "SELECT 1 FROM group_members WHERE group_id = ? AND member_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check group membership", e);
        }
    }

    private Group mapRow(ResultSet rs) throws SQLException {
        return new Group(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("currency"),
            GroupStatus.fromString(rs.getString("status")),
            rs.getString("created_by_user_id"),
            rs.getLong("created_at"),
            rs.getLong("updated_at")
        );
    }
}

