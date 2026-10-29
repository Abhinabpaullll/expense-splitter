package com.expensesplitter.dao;

import java.util.Optional;

public interface SettingsDao {
    Optional<String> get(String key);
    void set(String key, String value);
    void delete(String key);
}

