package com.expensesplitter.dao;

import com.expensesplitter.model.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentDao {
    void save(Payment payment);
    void update(Payment payment);
    void delete(String id);
    Optional<Payment> findById(String id);
    List<Payment> findByGroupId(String groupId);
    List<Payment> findAll();
}

