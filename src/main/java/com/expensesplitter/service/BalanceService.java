package com.expensesplitter.service;

import com.expensesplitter.dao.ExpenseDao;
import com.expensesplitter.dao.PaymentDao;
import com.expensesplitter.model.Expense;
import com.expensesplitter.model.ExpensePayer;
import com.expensesplitter.model.ExpenseSplit;
import com.expensesplitter.model.Payment;

import java.util.*;

/**
 * Calculates net balances, bilateral debts, simplified settlements, and dashboard metrics.
 */
public class BalanceService {

    private final ExpenseDao expenseDao;
    private final PaymentDao paymentDao;
    private final DebtSimplificationService debtSimplificationService;

    public BalanceService(ExpenseDao expenseDao, PaymentDao paymentDao, DebtSimplificationService debtSimplificationService) {
        this.expenseDao = expenseDao;
        this.paymentDao = paymentDao;
        this.debtSimplificationService = debtSimplificationService;
    }

    public Map<String, Long> computeNetBalances(String groupId) {
        Map<String, Long> balances = new LinkedHashMap<>();

        List<Expense> expenses = (groupId != null && !groupId.isBlank())
            ? expenseDao.findByGroupId(groupId)
            : expenseDao.findAll();

        for (Expense expense : expenses) {
            List<ExpensePayer> payers = expenseDao.findPayersByExpenseId(expense.getId());
            for (ExpensePayer payer : payers) {
                balances.put(payer.getMemberId(), balances.getOrDefault(payer.getMemberId(), 0L) + payer.getAmountCents());
            }

            List<ExpenseSplit> splits = expenseDao.findSplitsByExpenseId(expense.getId());
            for (ExpenseSplit split : splits) {
                balances.put(split.getMemberId(), balances.getOrDefault(split.getMemberId(), 0L) - split.getShareCents());
            }
        }

        List<Payment> payments = (groupId != null && !groupId.isBlank())
            ? paymentDao.findByGroupId(groupId)
            : paymentDao.findAll();

        for (Payment payment : payments) {
            // fromMember paid -> their balance increases (debt reduced)
            balances.put(payment.getFromMemberId(), balances.getOrDefault(payment.getFromMemberId(), 0L) + payment.getAmountCents());
            // toMember received -> their balance decreases (credit reduced)
            balances.put(payment.getToMemberId(), balances.getOrDefault(payment.getToMemberId(), 0L) - payment.getAmountCents());
        }

        return balances;
    }

    public List<DebtSimplificationService.Transfer> computeSimplifiedTransfers(String groupId) {
        Map<String, Long> netBalances = computeNetBalances(groupId);
        return debtSimplificationService.simplifyDebts(netBalances);
    }

    public long getUserOwed(String memberId, String groupId) {
        List<DebtSimplificationService.Transfer> transfers = computeSimplifiedTransfers(groupId);
        long owed = 0;
        for (DebtSimplificationService.Transfer t : transfers) {
            if (t.getToMemberId().equals(memberId)) {
                owed += t.getAmountCents();
            }
        }
        return owed;
    }

    public long getUserOwes(String memberId, String groupId) {
        List<DebtSimplificationService.Transfer> transfers = computeSimplifiedTransfers(groupId);
        long owes = 0;
        for (DebtSimplificationService.Transfer t : transfers) {
            if (t.getFromMemberId().equals(memberId)) {
                owes += t.getAmountCents();
            }
        }
        return owes;
    }

    public long getUserNetBalance(String memberId, String groupId) {
        Map<String, Long> net = computeNetBalances(groupId);
        return net.getOrDefault(memberId, 0L);
    }

    public Map<String, Long> computeSpendingByCategory(String groupId) {
        Map<String, Long> spending = new LinkedHashMap<>();
        List<Expense> expenses = (groupId != null && !groupId.isBlank())
            ? expenseDao.findByGroupId(groupId)
            : expenseDao.findAll();

        for (Expense e : expenses) {
            String cat = e.getCategory();
            spending.put(cat, spending.getOrDefault(cat, 0L) + e.getAmountCents());
        }
        return spending;
    }
}

