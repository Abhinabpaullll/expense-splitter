package com.expensesplitter.dao;

import com.expensesplitter.dao.impl.*;
import com.expensesplitter.model.*;
import com.expensesplitter.util.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DaoCrudTest {

    private DatabaseManager dbManager;
    private MemberDao memberDao;
    private GroupDao groupDao;
    private ExpenseDao expenseDao;
    private PaymentDao paymentDao;
    private ReminderDao reminderDao;

    @BeforeEach
    void setUp() {
        // Use in-memory database with foreign keys enabled
        dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        memberDao = new SqliteMemberDao(dbManager);
        groupDao = new SqliteGroupDao(dbManager);
        expenseDao = new SqliteExpenseDao(dbManager);
        paymentDao = new SqlitePaymentDao(dbManager);
        reminderDao = new SqliteReminderDao(dbManager);
    }

    @Test
    @DisplayName("Member CRUD operations")
    void testMemberCrud() {
        Member member = new Member("m1", "Asha Rao", "asha@example.com", "+919876543210");
        memberDao.save(member);

        Optional<Member> found = memberDao.findById("m1");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Asha Rao");
        assertThat(found.get().getInitials()).isEqualTo("AR");

        Member updated = new Member("m1", "Asha Rao Updated", "asha.new@example.com", "+919876543210");
        memberDao.update(updated);

        Optional<Member> foundUpdated = memberDao.findById("m1");
        assertThat(foundUpdated).isPresent();
        assertThat(foundUpdated.get().getName()).isEqualTo("Asha Rao Updated");

        memberDao.delete("m1");
        assertThat(memberDao.findById("m1")).isEmpty();
    }

    @Test
    @DisplayName("Group CRUD operations and member association")
    void testGroupCrud() {
        Group group = new Group("g1", "Flat 4B", "INR");
        groupDao.save(group);

        Optional<Group> found = groupDao.findById("g1");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Flat 4B");
        assertThat(found.get().getCurrencySymbol()).isEqualTo("₹");

        Member m1 = new Member("m1", "Asha Rao", "asha@example.com", null);
        Member m2 = new Member("m2", "Ravi Kumar", "ravi@example.com", null);
        memberDao.save(m1);
        memberDao.save(m2);

        groupDao.addMemberToGroup("g1", "m1");
        groupDao.addMemberToGroup("g1", "m2");

        assertThat(groupDao.isMemberInGroup("g1", "m1")).isTrue();
        assertThat(groupDao.isMemberInGroup("g1", "m3")).isFalse();

        List<Member> groupMembers = memberDao.findByGroupId("g1");
        assertThat(groupMembers).hasSize(2);

        groupDao.removeMemberFromGroup("g1", "m2");
        assertThat(groupDao.isMemberInGroup("g1", "m2")).isFalse();
    }

    @Test
    @DisplayName("Expense with Payers and Splits CRUD with Search")
    void testExpenseWithPayersAndSplits() {
        Group group = new Group("g1", "Flat 4B", "INR");
        groupDao.save(group);

        Member m1 = new Member("m1", "Asha Rao", null, null);
        Member m2 = new Member("m2", "Ravi Kumar", null, null);
        memberDao.save(m1);
        memberDao.save(m2);
        groupDao.addMemberToGroup("g1", "m1");
        groupDao.addMemberToGroup("g1", "m2");

        Expense expense = new Expense(
            "e1", "g1", "Weekly Groceries", 20000, "Groceries",
            LocalDate.of(2026, 10, 20), SplitType.EQUAL, "Supermarket provisions"
        );

        List<ExpensePayer> payers = List.of(
            new ExpensePayer(UUID.randomUUID().toString(), "e1", "m1", 20000)
        );

        List<ExpenseSplit> splits = List.of(
            new ExpenseSplit(UUID.randomUUID().toString(), "e1", "m1", 10000, BigDecimal.ZERO),
            new ExpenseSplit(UUID.randomUUID().toString(), "e1", "m2", 10000, BigDecimal.ZERO)
        );

        expenseDao.save(expense, payers, splits);

        Optional<Expense> found = expenseDao.findById("e1");
        assertThat(found).isPresent();
        assertThat(found.get().getAmountCents()).isEqualTo(20000);
        assertThat(found.get().getAmountDecimal()).isEqualByComparingTo("200.00");

        List<ExpensePayer> foundPayers = expenseDao.findPayersByExpenseId("e1");
        assertThat(foundPayers).hasSize(1);
        assertThat(foundPayers.get(0).getMemberId()).isEqualTo("m1");

        List<ExpenseSplit> foundSplits = expenseDao.findSplitsByExpenseId("e1");
        assertThat(foundSplits).hasSize(2);

        // Search tests
        List<Expense> searched = expenseDao.search("g1", "Groceries", "Groceries", null, null, null);
        assertThat(searched).hasSize(1);

        List<Expense> emptySearch = expenseDao.search("g1", "Utilities", null, null, null, null);
        assertThat(emptySearch).isEmpty();

        // Foreign Key Cascade check: deleting group should delete expenses, payers, and splits
        groupDao.delete("g1");
        assertThat(expenseDao.findById("e1")).isEmpty();
        assertThat(expenseDao.findPayersByExpenseId("e1")).isEmpty();
        assertThat(expenseDao.findSplitsByExpenseId("e1")).isEmpty();
    }

    @Test
    @DisplayName("Payment and Reminder CRUD")
    void testPaymentAndReminder() {
        Group group = new Group("g1", "Flat 4B", "INR");
        groupDao.save(group);

        Member m1 = new Member("m1", "Asha Rao", null, null);
        Member m2 = new Member("m2", "Ravi Kumar", null, null);
        memberDao.save(m1);
        memberDao.save(m2);

        Payment payment = new Payment(
            "p1", "g1", "m2", "m1", 5000, PaymentMethod.UPI,
            LocalDate.of(2026, 10, 25), "GooglePay settlement"
        );
        paymentDao.save(payment);

        Optional<Payment> foundP = paymentDao.findById("p1");
        assertThat(foundP).isPresent();
        assertThat(foundP.get().getAmountCents()).isEqualTo(5000);
        assertThat(foundP.get().getPaymentMethod()).isEqualTo(PaymentMethod.UPI);

        Reminder reminder = new Reminder("r1", "g1", "m2", 5000);
        reminderDao.save(reminder);

        List<Reminder> pending = reminderDao.findPending();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getId()).isEqualTo("r1");

        reminderDao.update(new Reminder("r1", "g1", "m2", 5000, "SENT", reminder.getScheduledAt(), System.currentTimeMillis()));
        assertThat(reminderDao.findPending()).isEmpty();
    }
}

