package com.expensesplitter.dao;

import com.expensesplitter.model.User;
import java.util.List;
import java.util.Optional;

public interface UserDao {
    void save(User user);
    void update(User user);
    void updateLastLogin(String userId, long timestamp);
    Optional<User> findById(String id);
    Optional<User> findByEmail(String email);
    Optional<User> findByDisplayName(String displayName);
    List<User> findAll();
    void delete(String id);
}

