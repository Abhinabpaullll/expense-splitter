package com.expensesplitter;

import com.expensesplitter.dao.impl.*;
import com.expensesplitter.model.*;
import com.expensesplitter.util.DatabaseManager;
import com.expensesplitter.util.PasswordUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PersistenceAndRollbackTest {

    @Test
    void testRealDbFilePersistenceAcrossRestarts(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("test_persistence.db");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        // 1. Initial connection: save user, group, and member
        DatabaseManager dbManager1 = new DatabaseManager(jdbcUrl);
        SqliteUserDao userDao1 = new SqliteUserDao(dbManager1);
        SqliteGroupDao groupDao1 = new SqliteGroupDao(dbManager1);
        SqliteMemberDao memberDao1 = new SqliteMemberDao(dbManager1);

        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword("secret123", salt);
        User user1 = new User("u1", "Morgan Freeman", "morgan@example.com", hash, salt, System.currentTimeMillis(), null);
        userDao1.save(user1);

        Group group1 = new Group("g1", "Trip to Tokyo", "USD", GroupStatus.ACTIVE, "u1", System.currentTimeMillis(), System.currentTimeMillis());
        groupDao1.save(group1);

        Member member1 = new Member("m1", "Morgan", "morgan@example.com", null, "u1", System.currentTimeMillis());
        memberDao1.save(member1);
        groupDao1.addMemberToGroup("g1", "m1");

        dbManager1.close();

        // 2. Re-open connection with a new DatabaseManager pointing to the same file
        DatabaseManager dbManager2 = new DatabaseManager(jdbcUrl);
        SqliteUserDao userDao2 = new SqliteUserDao(dbManager2);
        SqliteGroupDao groupDao2 = new SqliteGroupDao(dbManager2);
        SqliteMemberDao memberDao2 = new SqliteMemberDao(dbManager2);

        Optional<User> loadedUser = userDao2.findById("u1");
        assertThat(loadedUser).isPresent();
        assertThat(loadedUser.get().displayName()).isEqualTo("Morgan Freeman");
        assertThat(PasswordUtil.verifyPassword("secret123", loadedUser.get().salt(), loadedUser.get().passwordHash())).isTrue();

        Optional<Group> loadedGroup = groupDao2.findById("g1");
        assertThat(loadedGroup).isPresent();
        assertThat(loadedGroup.get().getName()).isEqualTo("Trip to Tokyo");
        assertThat(loadedGroup.get().getCurrency()).isEqualTo("USD");

        List<Member> groupMembers = memberDao2.findByGroupId("g1");
        assertThat(groupMembers).hasSize(1);
        assertThat(groupMembers.get(0).getName()).isEqualTo("Morgan");

        dbManager2.close();
    }

    @Test
    void testTransactionRollbackOnSaveFailure(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("test_rollback.db");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        DatabaseManager dbManager = new DatabaseManager(jdbcUrl);
        SqliteGroupDao groupDao = new SqliteGroupDao(dbManager);
        SqliteMemberDao memberDao = new SqliteMemberDao(dbManager);
        SqliteExpenseDao expenseDao = new SqliteExpenseDao(dbManager);

        Group g = new Group("g100", "Apartment", "EUR");
        groupDao.save(g);
        Member m = new Member("m100", "Alice", null, null);
        memberDao.save(m);
        groupDao.addMemberToGroup("g100", "m100");

        String expenseId = "exp_fail_1";
        Expense expense = new Expense(expenseId, "g100", "Rent Payment", 100000L, "Rent", LocalDate.now(), SplitType.EQUAL, "Monthly rent");
        List<ExpensePayer> payers = List.of(new ExpensePayer("p1", expenseId, "m100", 100000L));

        // Intentionally create a split that references a non-existent member violating foreign keys
        // or trigger a simulated runtime exception in transaction
        assertThatThrownBy(() -> {
            dbManager.inTransaction(conn -> {
                // First insert expense
                try (var ps = conn.prepareStatement("INSERT INTO expenses (id, group_id, title, amount_cents, category, date, split_type, notes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
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

                // Simulate failure halfway through transaction
                throw new SQLException("Simulated database failure during batch insertion");
            });
        }).hasMessageContaining("Simulated database failure");

        // Verify that expense was rolled back and is not in DB
        Optional<Expense> notFound = expenseDao.findById(expenseId);
        assertThat(notFound).isEmpty();

        dbManager.close();
    }

    @Test
    void testAccountIsolation(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("test_isolation.db");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        DatabaseManager dbManager = new DatabaseManager(jdbcUrl);
        SqliteUserDao userDao = new SqliteUserDao(dbManager);
        SqliteGroupDao groupDao = new SqliteGroupDao(dbManager);

        // User A and User B
        User userA = new User("ua", "User Alpha", "alpha@test.com", "hash", "salt", 1000L, null);
        User userB = new User("ub", "User Beta", "beta@test.com", "hash", "salt", 1000L, null);
        userDao.save(userA);
        userDao.save(userB);

        // Group created by User A
        Group groupA = new Group("ga", "Alpha Private Ledger", "INR", GroupStatus.ACTIVE, "ua", 1000L, 1000L);
        groupDao.save(groupA);

        // User A sees their group
        List<Group> userAGroups = groupDao.findByUserId("ua");
        assertThat(userAGroups).extracting(Group::getId).contains("ga");

        // User B does NOT see group A
        List<Group> userBGroups = groupDao.findByUserId("ub");
        assertThat(userBGroups).extracting(Group::getId).doesNotContain("ga");

        // Now add User B's member to Group A
        SqliteMemberDao memberDao = new SqliteMemberDao(dbManager);
        Member memberB = new Member("mb", "Beta Member", "beta@test.com", null, "ub", 1000L);
        memberDao.save(memberB);
        groupDao.addMemberToGroup("ga", "mb");

        // Now User B sees Group A because they are a member
        List<Group> userBGroupsAfter = groupDao.findByUserId("ub");
        assertThat(userBGroupsAfter).extracting(Group::getId).contains("ga");

        dbManager.close();
    }

    @Test
    void testWrongPasswordAuthentication() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword("CorrectHorseBatteryStaple", salt);

        assertThat(PasswordUtil.verifyPassword("CorrectHorseBatteryStaple", salt, hash)).isTrue();
        assertThat(PasswordUtil.verifyPassword("wrongpassword", salt, hash)).isFalse();
        assertThat(PasswordUtil.verifyPassword("", salt, hash)).isFalse();
        assertThat(PasswordUtil.verifyPassword("CorrectHorseBatteryStaple!", salt, hash)).isFalse();
    }
}

