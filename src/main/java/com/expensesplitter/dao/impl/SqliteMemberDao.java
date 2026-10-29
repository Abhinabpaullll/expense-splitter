package com.expensesplitter.dao.impl;

import com.expensesplitter.dao.MemberDao;
import com.expensesplitter.model.Member;
import com.expensesplitter.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteMemberDao implements MemberDao {

    private final DatabaseManager databaseManager;

    public SqliteMemberDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void save(Member member) {
        String sql = "INSERT INTO members (id, name, email, phone, user_id, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, member.getId());
            ps.setString(2, member.getName());
            ps.setString(3, member.getEmail());
            ps.setString(4, member.getPhone());
            ps.setString(5, member.getUserId());
            ps.setLong(6, member.getCreatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save member: " + member.getName(), e);
        }
    }

    @Override
    public void update(Member member) {
        String sql = "UPDATE members SET name = ?, email = ?, phone = ?, user_id = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, member.getName());
            ps.setString(2, member.getEmail());
            ps.setString(3, member.getPhone());
            ps.setString(4, member.getUserId());
            ps.setString(5, member.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update member: " + member.getId(), e);
        }
    }

    @Override
    public void delete(String id) {
        String sql = "DELETE FROM members WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete member: " + id, e);
        }
    }

    @Override
    public Optional<Member> findById(String id) {
        String sql = "SELECT id, name, email, phone, user_id, created_at FROM members WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find member: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Member> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        String sql = "SELECT id, name, email, phone, user_id, created_at FROM members WHERE user_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find member by user_id: " + userId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Member> findAll() {
        String sql = "SELECT id, name, email, phone, user_id, created_at FROM members ORDER BY name ASC";
        List<Member> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list members", e);
        }
        return list;
    }

    @Override
    public List<Member> findByGroupId(String groupId) {
        String sql = """
            SELECT m.id, m.name, m.email, m.phone, m.user_id, m.created_at
            FROM members m
            JOIN group_members gm ON m.id = gm.member_id
            WHERE gm.group_id = ?
            ORDER BY m.name ASC
        """;
        List<Member> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find members for group: " + groupId, e);
        }
        return list;
    }

    private Member mapRow(ResultSet rs) throws SQLException {
        return new Member(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("user_id"),
            rs.getLong("created_at")
        );
    }
}

