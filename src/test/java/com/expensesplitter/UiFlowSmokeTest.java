package com.expensesplitter;

import com.expensesplitter.dao.impl.*;
import com.expensesplitter.model.*;
import com.expensesplitter.service.BalanceService;
import com.expensesplitter.service.DebtSimplificationService;
import com.expensesplitter.service.ExpenseService;
import com.expensesplitter.service.GroupService;
import com.expensesplitter.service.SplitCalculationService;
import com.expensesplitter.util.DatabaseManager;
import com.expensesplitter.util.PasswordUtil;
import com.expensesplitter.util.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class UiFlowSmokeTest {

    @Test
    void testEndToEndUserGroupExpenseSettlementFlow(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("smoke_test.db");
        DatabaseManager dbManager = new DatabaseManager("jdbc:sqlite:" + dbPath.toAbsolutePath());

        SqliteUserDao userDao = new SqliteUserDao(dbManager);
        SqliteMemberDao memberDao = new SqliteMemberDao(dbManager);
        SqliteGroupDao groupDao = new SqliteGroupDao(dbManager);
        SqliteExpenseDao expenseDao = new SqliteExpenseDao(dbManager);
        SqlitePaymentDao paymentDao = new SqlitePaymentDao(dbManager);
        SqliteSettingsDao settingsDao = new SqliteSettingsDao(dbManager);

        SessionManager sessionManager = new SessionManager(userDao, memberDao);
        SplitCalculationService splitCalculationService = new SplitCalculationService();
        DebtSimplificationService debtSimplificationService = new DebtSimplificationService();
        BalanceService balanceService = new BalanceService(expenseDao, paymentDao, debtSimplificationService);
        GroupService groupService = new GroupService(groupDao, memberDao, expenseDao, paymentDao, splitCalculationService);
        ExpenseService expenseService = new ExpenseService(expenseDao, splitCalculationService);

        // 1. Initial State: No users, not signed in
        assertThat(userDao.findAll()).isEmpty();
        assertThat(sessionManager.isSignedIn()).isFalse();

        // 2. User Registration (Account A: "Maya Chen")
        String saltA = PasswordUtil.generateSalt();
        String hashA = PasswordUtil.hashPassword("securePass123", saltA);
        User userA = new User("usr_maya", "Maya Chen", "maya@example.com", hashA, saltA, System.currentTimeMillis(), System.currentTimeMillis());
        userDao.save(userA);
        sessionManager.setCurrentUser(userA);

        assertThat(sessionManager.isSignedIn()).isTrue();
        assertThat(sessionManager.getCurrentUser().displayName()).isEqualTo("Maya Chen");
        Member memberA = sessionManager.getCurrentMember();
        assertThat(memberA).isNotNull();
        assertThat(memberA.getUserId()).isEqualTo("usr_maya");

        // 3. User A creates Group "Apartment 3A"
        String groupId = UUID.randomUUID().toString();
        Group group = new Group(groupId, "Apartment 3A", "USD", GroupStatus.ACTIVE, userA.id(), System.currentTimeMillis(), System.currentTimeMillis());
        groupService.createGroup(group, List.of(memberA.getId()));

        List<Group> mayaGroups = groupService.getGroupsForUser(userA.id());
        assertThat(mayaGroups).hasSize(1);
        assertThat(mayaGroups.get(0).getName()).isEqualTo("Apartment 3A");

        // 4. Maya adds a roommate "Leo" (unregistered member)
        String leoId = UUID.randomUUID().toString();
        Member memberLeo = new Member(leoId, "Leo Thorne", "leo@example.com", null, null, System.currentTimeMillis());
        memberDao.save(memberLeo);
        groupService.addMemberToGroup(groupId, leoId);

        List<Member> members = groupService.getGroupMembers(groupId);
        assertThat(members).hasSize(2);
        assertThat(members).extracting(Member::getName).contains("Maya Chen", "Leo Thorne");

        // 5. Maya adds an expense ($120.00 = 12000 cents for Groceries, split equal)
        List<ExpensePayer> payers = List.of(new ExpensePayer(UUID.randomUUID().toString(), "", memberA.getId(), 12000L));
        List<String> participants = List.of(memberA.getId(), leoId);

        expenseService.createExpense(
            groupId,
            "Costco Restock",
            12000L,
            "Groceries",
            LocalDate.now(),
            "Receipt #4812",
            SplitType.EQUAL,
            payers,
            participants,
            Map.of()
        );

        // 6. Verify Balances from Maya's perspective
        long mayaNet = balanceService.getUserNetBalance(memberA.getId(), groupId);
        long mayaOwed = balanceService.getUserOwed(memberA.getId(), groupId);
        long mayaOwes = balanceService.getUserOwes(memberA.getId(), groupId);

        assertThat(mayaNet).isEqualTo(6000L);   // Maya paid 120, share is 60 -> net +60.00
        assertThat(mayaOwed).isEqualTo(6000L);  // Leo owes Maya 60.00
        assertThat(mayaOwes).isZero();

        // Verify Leo's balance
        long leoNet = balanceService.getUserNetBalance(leoId, groupId);
        assertThat(leoNet).isEqualTo(-6000L);

        // 7. Leo pays Maya $60.00 to settle up
        Payment settlement = new Payment(
            UUID.randomUUID().toString(),
            groupId,
            leoId,
            memberA.getId(),
            6000L,
            PaymentMethod.UPI,
            LocalDate.now(),
            "Settled via app"
        );
        paymentDao.save(settlement);

        // 8. Verify Balances are now 0.00
        assertThat(balanceService.getUserNetBalance(memberA.getId(), groupId)).isZero();
        assertThat(balanceService.getUserNetBalance(leoId, groupId)).isZero();

        // 9. Account Switching: Sign out Maya, Register User B ("Liam Vance")
        sessionManager.signOut();
        assertThat(sessionManager.isSignedIn()).isFalse();

        String saltB = PasswordUtil.generateSalt();
        String hashB = PasswordUtil.hashPassword("liamPass456", saltB);
        User userB = new User("usr_liam", "Liam Vance", "liam@example.com", hashB, saltB, System.currentTimeMillis(), System.currentTimeMillis());
        userDao.save(userB);
        sessionManager.setCurrentUser(userB);

        // Liam does NOT see Maya's "Apartment 3A" group
        List<Group> liamGroups = groupService.getGroupsForUser(userB.id());
        assertThat(liamGroups).isEmpty();

        // Settings persistence: default currency and theme
        settingsDao.set("default_currency", "EUR (€)");
        settingsDao.set("theme", "DARK");
        assertThat(settingsDao.get("default_currency")).contains("EUR (€)");
        assertThat(settingsDao.get("theme")).contains("DARK");

        dbManager.close();
    }
}

