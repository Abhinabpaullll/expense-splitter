package com.expensesplitter.dao;

import com.expensesplitter.model.Reminder;

import java.util.List;
import java.util.Optional;

public interface ReminderDao {
    void save(Reminder reminder);
    void update(Reminder reminder);
    void delete(String id);
    Optional<Reminder> findById(String id);
    List<Reminder> findByGroupId(String groupId);
    List<Reminder> findPending();
    List<Reminder> findAll();
}

