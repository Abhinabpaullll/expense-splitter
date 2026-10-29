package com.expensesplitter.service;

import com.expensesplitter.dao.ExpenseDao;
import com.expensesplitter.dao.PaymentDao;
import com.expensesplitter.dao.impl.SqliteExpenseDao;
import com.expensesplitter.dao.impl.SqliteGroupDao;
import com.expensesplitter.dao.impl.SqliteMemberDao;
import com.expensesplitter.dao.impl.SqlitePaymentDao;
import com.expensesplitter.model.*;
import com.expensesplitter.util.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceServiceTest {

    private DatabaseManager dbManager;
    private ExpenseDao expenseDao;
    private PaymentDao paymentDao;
    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        expenseDao = new SqliteExpenseDao(dbManager);
        paymentDao = new SqlitePaymentDao(dbManager);
        SqliteMemberDao memberDao = new SqliteMemberDao(dbManager);
        SqliteGroupDao groupDao = new SqliteGroupDao(dbManager);

        Group g = new Group("g1", "Flat 4B", "INR");
        groupDao.save(g);

        memberDao.save(new Member("m1", "Asha", null, null));
        memberDao.save(new Member("m2", "Ravi", null, null));
        memberDao.save(new Member("m3", "Meera", null, null));

        groupDao.addMemberToGroup("g1", "m1");
        groupDao.addMemberToGroup("g1", "m2");
        groupDao.addMemberToGroup("g1", "m3");

        DebtSimplificationService debtSimplificationService = new DebtSimplificationService();
        balanceService = new BalanceService(expenseDao, paymentDao, debtSimplificationService);
    }

    @Test
    @DisplayName("Compute net balances from expenses and payments")
    void testComputeNetBalances() {
        // Asha pays 3000 split equally among Asha, Ravi, Meera (1000 each)
        // Asha net: +2000, Ravi net: -1000, Meera net: -1000
        Expense exp = new Expense("e1", "g1", "Dinner", 3000, "Dining", LocalDate.now(), SplitType.EQUAL, null);
        List<ExpensePayer> payers = List.of(new ExpensePayer(UUID.randomUUID().toString(), "e1", "m1", 3000));
        List<ExpenseSplit> splits = List.of(
            new ExpenseSplit(UUID.randomUUID().toString(), "e1", "m1", 1000, BigDecimal.ZERO),
            new ExpenseSplit(UUID.randomUUID().toString(), "e1", "m2", 1000, BigDecimal.ZERO),
            new ExpenseSplit(UUID.randomUUID().toString(), "e1", "m3", 1000, BigDecimal.ZERO)
        );
        expenseDao.save(exp, payers, splits);

        Map<String, Long> balancesBeforePay = balanceService.computeNetBalances("g1");
        assertThat(balancesBeforePay.get("m1")).isEqualTo(2000L);
        assertThat(balancesBeforePay.get("m2")).isEqualTo(-1000L);
        assertThat(balancesBeforePay.get("m3")).isEqualTo(-1000L);

        // Ravi pays Asha 1000 to settle his debt
        Payment payment = new Payment(UUID.randomUUID().toString(), "g1", "m2", "m1", 1000, PaymentMethod.UPI, LocalDate.now(), "Settlement");
        paymentDao.save(payment);

        Map<String, Long> balancesAfterPay = balanceService.computeNetBalances("g1");
        assertThat(balancesAfterPay.get("m1")).isEqualTo(1000L); // Asha is now owed 1000
        assertThat(balancesAfterPay.get("m2")).isEqualTo(0L);    // Ravi is fully settled
        assertThat(balancesAfterPay.get("m3")).isEqualTo(-1000L); // Meera still owes 1000

        // User metrics for Asha (m1)
        long ashaOwed = balanceService.getUserOwed("m1", "g1");
        long ashaOwes = balanceService.getUserOwes("m1", "g1");
        assertThat(ashaOwed).isEqualTo(1000L);
        assertThat(ashaOwes).isZero();
    }
}

