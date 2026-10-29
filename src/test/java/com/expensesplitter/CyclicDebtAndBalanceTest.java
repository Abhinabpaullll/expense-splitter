package com.expensesplitter;

import com.expensesplitter.dao.impl.*;
import com.expensesplitter.model.*;
import com.expensesplitter.service.BalanceService;
import com.expensesplitter.service.DebtSimplificationService;
import com.expensesplitter.service.SplitCalculationService;
import com.expensesplitter.util.DatabaseManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CyclicDebtAndBalanceTest {

    @Test
    void testCyclicDebtSimplificationToZero() {
        DebtSimplificationService simplificationService = new DebtSimplificationService();

        // Cyclic:
        // A owes B 5000 cents (net: A -5000, B +5000)
        // B owes C 5000 cents (net: B -5000, C +5000)
        // C owes A 5000 cents (net: C -5000, A +5000)
        // Resulting net balance for everyone is 0:
        Map<String, Long> netBalances = Map.of(
            "mem_A", 0L,
            "mem_B", 0L,
            "mem_C", 0L
        );

        List<DebtSimplificationService.Transfer> transfers = simplificationService.simplifyDebts(netBalances);
        assertThat(transfers).isEmpty();
    }

    @Test
    void testCyclicDebtWithNetResidue() {
        DebtSimplificationService simplificationService = new DebtSimplificationService();

        // A owes B 30.00 (3000 cents)
        // B owes C 50.00 (5000 cents)
        // C owes A 10.00 (1000 cents)
        // Net:
        // A: -3000 + 1000 = -2000
        // B: +3000 - 5000 = -2000
        // C: +5000 - 1000 = +4000
        Map<String, Long> netBalances = Map.of(
            "mem_A", -2000L,
            "mem_B", -2000L,
            "mem_C", 4000L
        );

        List<DebtSimplificationService.Transfer> transfers = simplificationService.simplifyDebts(netBalances);
        assertThat(transfers).hasSize(2);

        // Sum of all simplified transfer amounts must equal total positive balance (4000)
        long totalTransferred = transfers.stream().mapToLong(DebtSimplificationService.Transfer::getAmountCents).sum();
        assertThat(totalTransferred).isEqualTo(4000L);

        for (DebtSimplificationService.Transfer t : transfers) {
            assertThat(t.getToMemberId()).isEqualTo("mem_C");
            assertThat(t.getAmountCents()).isEqualTo(2000L);
        }
    }

    @Test
    void testEqualSplitWithRemainderSummingExactlyToTotal() {
        SplitCalculationService splitService = new SplitCalculationService();

        // 100 cents split 3 ways: 34, 33, 33
        List<String> participants = List.of("p1", "p2", "p3");
        SplitCalculationService.SplitResult result = splitService.calculateSplits(100L, SplitType.EQUAL, participants, null);

        assertThat(result.isValid()).isTrue();
        long sum = participants.stream().mapToLong(result::getShare).sum();
        assertThat(sum).isEqualTo(100L);

        // Check shares: one person gets 34, two get 33
        long share1 = result.getShare("p1");
        long share2 = result.getShare("p2");
        long share3 = result.getShare("p3");
        assertThat(share1 + share2 + share3).isEqualTo(100L);
        assertThat(List.of(share1, share2, share3)).contains(34L, 33L, 33L);
    }

    @Test
    void testDynamicBalanceRecalculationAfterPayment() {
        DatabaseManager dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        SqliteGroupDao groupDao = new SqliteGroupDao(dbManager);
        SqliteMemberDao memberDao = new SqliteMemberDao(dbManager);
        SqliteExpenseDao expenseDao = new SqliteExpenseDao(dbManager);
        SqlitePaymentDao paymentDao = new SqlitePaymentDao(dbManager);
        DebtSimplificationService debtService = new DebtSimplificationService();
        BalanceService balanceService = new BalanceService(expenseDao, paymentDao, debtService);

        Group g = new Group("grp_test", "Trip Group", "INR");
        groupDao.save(g);

        Member m1 = new Member("m_alice", "Alice", null, null);
        Member m2 = new Member("m_bob", "Bob", null, null);
        memberDao.save(m1);
        memberDao.save(m2);
        groupDao.addMemberToGroup("grp_test", "m_alice");
        groupDao.addMemberToGroup("grp_test", "m_bob");

        // Alice pays 10000 cents ($100.00), split equally between Alice and Bob (5000 each)
        // Alice net: +5000, Bob net: -5000
        String expId = UUID.randomUUID().toString();
        Expense exp = new Expense(expId, "grp_test", "Dinner", 10000L, "Dining", LocalDate.now(), SplitType.EQUAL, "");
        List<ExpensePayer> payers = List.of(new ExpensePayer(UUID.randomUUID().toString(), expId, "m_alice", 10000L));
        List<ExpenseSplit> splits = List.of(
            new ExpenseSplit(UUID.randomUUID().toString(), expId, "m_alice", 5000L),
            new ExpenseSplit(UUID.randomUUID().toString(), expId, "m_bob", 5000L)
        );
        expenseDao.save(exp, payers, splits);

        Map<String, Long> initialBalances = balanceService.computeNetBalances("grp_test");
        assertThat(initialBalances.get("m_alice")).isEqualTo(5000L);
        assertThat(initialBalances.get("m_bob")).isEqualTo(-5000L);

        // Bob makes a partial payment of 3000 cents ($30.00) to Alice
        Payment partialPayment = new Payment(
            UUID.randomUUID().toString(),
            "grp_test",
            "m_bob",
            "m_alice",
            3000L,
            PaymentMethod.UPI,
            LocalDate.now(),
            "Partial payment"
        );
        paymentDao.save(partialPayment);

        // Recalculated dynamically from database rows
        Map<String, Long> balancesAfterPartial = balanceService.computeNetBalances("grp_test");
        assertThat(balancesAfterPartial.get("m_alice")).isEqualTo(2000L);
        assertThat(balancesAfterPartial.get("m_bob")).isEqualTo(-2000L);

        // Bob settles remaining 2000 cents ($20.00)
        Payment fullPayment = new Payment(
            UUID.randomUUID().toString(),
            "grp_test",
            "m_bob",
            "m_alice",
            2000L,
            PaymentMethod.CASH,
            LocalDate.now(),
            "Final settlement"
        );
        paymentDao.save(fullPayment);

        Map<String, Long> balancesAfterFull = balanceService.computeNetBalances("grp_test");
        assertThat(balancesAfterFull.get("m_alice")).isZero();
        assertThat(balancesAfterFull.get("m_bob")).isZero();

        dbManager.close();
    }
}

