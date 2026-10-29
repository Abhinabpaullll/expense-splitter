package com.expensesplitter.dao;

import com.expensesplitter.model.Expense;
import com.expensesplitter.model.ExpensePayer;
import com.expensesplitter.model.ExpenseSplit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseDao {
    void save(Expense expense, List<ExpensePayer> payers, List<ExpenseSplit> splits);
    void update(Expense expense, List<ExpensePayer> payers, List<ExpenseSplit> splits);
    void delete(String id);
    Optional<Expense> findById(String id);
    List<Expense> findByGroupId(String groupId);
    List<Expense> findAll();
    List<ExpensePayer> findPayersByExpenseId(String expenseId);
    List<ExpenseSplit> findSplitsByExpenseId(String expenseId);
    List<Expense> search(String groupId, String query, String category, String memberId, LocalDate startDate, LocalDate endDate);
}

