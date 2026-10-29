package com.expensesplitter.service;

import com.expensesplitter.dao.ExpenseDao;
import com.expensesplitter.model.Expense;
import com.expensesplitter.model.ExpensePayer;
import com.expensesplitter.model.ExpenseSplit;
import com.expensesplitter.model.SplitType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ExpenseService {

    private final ExpenseDao expenseDao;
    private final SplitCalculationService splitCalculationService;

    public ExpenseService(ExpenseDao expenseDao, SplitCalculationService splitCalculationService) {
        this.expenseDao = expenseDao;
        this.splitCalculationService = splitCalculationService;
    }

    public List<Expense> getExpensesByGroup(String groupId) {
        return expenseDao.findByGroupId(groupId);
    }

    public List<Expense> getAllExpenses() {
        return expenseDao.findAll();
    }

    public Optional<Expense> getExpenseById(String id) {
        return expenseDao.findById(id);
    }

    public List<ExpensePayer> getPayers(String expenseId) {
        return expenseDao.findPayersByExpenseId(expenseId);
    }

    public List<ExpenseSplit> getSplits(String expenseId) {
        return expenseDao.findSplitsByExpenseId(expenseId);
    }

    public void createExpense(String groupId, String title, long amountCents, String category,
                              LocalDate date, String notes, SplitType splitType,
                              List<ExpensePayer> payers, List<String> participantIds,
                              Map<String, BigDecimal> splitParams) {

        // Validate payers sum
        long payerTotal = 0;
        for (ExpensePayer p : payers) {
            payerTotal += p.getAmountCents();
        }
        if (payerTotal != amountCents) {
            throw new IllegalArgumentException("Payers total does not match expense total amount.");
        }

        // Calculate splits with remainder distribution
        SplitCalculationService.SplitResult splitResult = splitCalculationService.calculateSplits(
            amountCents, splitType, participantIds, splitParams
        );
        if (!splitResult.isValid()) {
            throw new IllegalArgumentException(splitResult.getErrorMessage());
        }

        String expenseId = UUID.randomUUID().toString();
        Expense expense = new Expense(expenseId, groupId, title, amountCents, category, date, splitType, notes);

        List<ExpensePayer> persistedPayers = payers.stream()
            .map(p -> new ExpensePayer(UUID.randomUUID().toString(), expenseId, p.getMemberId(), p.getAmountCents()))
            .toList();

        List<ExpenseSplit> persistedSplits = participantIds.stream()
            .map(mId -> new ExpenseSplit(
                UUID.randomUUID().toString(),
                expenseId,
                mId,
                splitResult.getShare(mId),
                splitParams != null ? splitParams.getOrDefault(mId, BigDecimal.ZERO) : BigDecimal.ZERO
            ))
            .toList();

        expenseDao.save(expense, persistedPayers, persistedSplits);
    }

    public void updateExpense(Expense expense, List<ExpensePayer> payers, List<ExpenseSplit> splits) {
        expenseDao.update(expense, payers, splits);
    }

    public void deleteExpense(String id) {
        expenseDao.delete(id);
    }

    public List<Expense> search(String groupId, String query, String category, String memberId, LocalDate startDate, LocalDate endDate) {
        return expenseDao.search(groupId, query, category, memberId, startDate, endDate);
    }
}

