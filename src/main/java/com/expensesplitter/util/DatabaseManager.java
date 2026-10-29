package com.expensesplitter.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages SQLite database connections, schema migrations, and transactions.
 * Enforces PRAGMA foreign_keys = ON on every connection.
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    public static String getDefaultDbUrl() {
        String userHome = System.getProperty("user.home");
        java.io.File dir = new java.io.File(userHome, ".expensesplitter");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return "jdbc:sqlite:" + new java.io.File(dir, "expensesplitter.db").getAbsolutePath();
    }

    private final String jdbcUrl;
    private Connection keepAliveConnection; // For in-memory testing so the database persists across calls

    public DatabaseManager(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        if (jdbcUrl.contains(":memory:")) {
            try {
                this.keepAliveConnection = DriverManager.getConnection(jdbcUrl);
                try (Statement stmt = keepAliveConnection.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON;");
                }
            } catch (SQLException e) {
                logger.error("Failed to initialize keep-alive connection for in-memory SQLite", e);
            }
        }
        initSchema();
    }

    public DatabaseManager() {
        this(getDefaultDbUrl());
    }

    public Connection getConnection() throws SQLException {
        if (keepAliveConnection != null) {
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        return null; // Do not close the shared in-memory connection
                    }
                    return method.invoke(keepAliveConnection, args);
                }
            );
        }
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void close() {
        if (keepAliveConnection != null) {
            try {
                keepAliveConnection.close();
            } catch (SQLException e) {
                logger.warn("Failed to close in-memory keep-alive connection: {}", e.getMessage());
            }
        }
    }

    public void initSchema() {
        String schema = """
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                email TEXT,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                last_login_at INTEGER
            );

            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS members (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                email TEXT,
                phone TEXT,
                user_id TEXT,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS groups (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                currency TEXT NOT NULL DEFAULT 'INR',
                status TEXT NOT NULL DEFAULT 'ACTIVE',
                created_by_user_id TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS group_members (
                group_id TEXT NOT NULL,
                member_id TEXT NOT NULL,
                PRIMARY KEY (group_id, member_id),
                FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
                FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS expenses (
                id TEXT PRIMARY KEY,
                group_id TEXT NOT NULL,
                title TEXT NOT NULL,
                amount_cents INTEGER NOT NULL,
                category TEXT NOT NULL,
                date TEXT NOT NULL,
                split_type TEXT NOT NULL,
                notes TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS expense_payers (
                id TEXT PRIMARY KEY,
                expense_id TEXT NOT NULL,
                member_id TEXT NOT NULL,
                amount_cents INTEGER NOT NULL,
                FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
                FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS expense_splits (
                id TEXT PRIMARY KEY,
                expense_id TEXT NOT NULL,
                member_id TEXT NOT NULL,
                share_cents INTEGER NOT NULL,
                exact_param NUMERIC,
                FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
                FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS payments (
                id TEXT PRIMARY KEY,
                group_id TEXT NOT NULL,
                from_member_id TEXT NOT NULL,
                to_member_id TEXT NOT NULL,
                amount_cents INTEGER NOT NULL,
                payment_method TEXT NOT NULL,
                date TEXT NOT NULL,
                notes TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
                FOREIGN KEY (from_member_id) REFERENCES members(id) ON DELETE RESTRICT,
                FOREIGN KEY (to_member_id) REFERENCES members(id) ON DELETE RESTRICT
            );

            CREATE TABLE IF NOT EXISTS reminders (
                id TEXT PRIMARY KEY,
                group_id TEXT NOT NULL,
                member_id TEXT NOT NULL,
                amount_cents INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                scheduled_at INTEGER NOT NULL,
                sent_at INTEGER,
                FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
                FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
            );

            CREATE INDEX IF NOT EXISTS idx_group_members_group ON group_members(group_id);
            CREATE INDEX IF NOT EXISTS idx_group_members_member ON group_members(member_id);
            CREATE INDEX IF NOT EXISTS idx_expenses_group ON expenses(group_id);
            CREATE INDEX IF NOT EXISTS idx_expenses_date ON expenses(date);
            CREATE INDEX IF NOT EXISTS idx_expense_payers_expense ON expense_payers(expense_id);
            CREATE INDEX IF NOT EXISTS idx_expense_splits_expense ON expense_splits(expense_id);
            CREATE INDEX IF NOT EXISTS idx_payments_group ON payments(group_id);
            CREATE INDEX IF NOT EXISTS idx_reminders_group ON reminders(group_id);
        """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            for (String statementSql : schema.split(";")) {
                String trimmed = statementSql.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
            logger.info("Database schema initialized successfully.");
        } catch (SQLException e) {
            logger.error("Failed to initialize database schema", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public boolean hasLegacyDummyData() {
        if (jdbcUrl.contains(":memory:")) return false;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT 1 FROM groups WHERE id = 'demo-flat-4b' LIMIT 1")) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public void cleanLegacyDataAndRecreateSchema(boolean backupFirst) {
        if (backupFirst && !jdbcUrl.contains(":memory:")) {
            try {
                String dbPath = jdbcUrl.replace("jdbc:sqlite:", "");
                java.io.File src = new java.io.File(dbPath);
                if (src.exists()) {
                    java.io.File backup = new java.io.File(src.getParentFile(), "expensesplitter_backup_" + System.currentTimeMillis() + ".db");
                    java.nio.file.Files.copy(src.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Created legacy backup at: {}", backup.getAbsolutePath());
                }
            } catch (Exception e) {
                logger.error("Failed to create legacy backup", e);
            }
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = OFF;");
            String[] tables = {"reminders", "payments", "expense_splits", "expense_payers", "expenses", "group_members", "groups", "members", "settings", "users"};
            for (String t : tables) {
                stmt.execute("DROP TABLE IF EXISTS " + t + ";");
            }
            stmt.execute("PRAGMA foreign_keys = ON;");
            logger.info("Legacy tables dropped successfully.");
        } catch (SQLException e) {
            logger.error("Failed to drop legacy tables", e);
        }
        initSchema();
    }

    @FunctionalInterface
    public interface TransactionFunction<T> {
        T apply(Connection conn) throws SQLException;
    }

    public <T> T inTransaction(TransactionFunction<T> function) throws SQLException {
        Connection conn = getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            T result = function.apply(conn);
            conn.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                logger.error("Rollback failed", ex);
            }
            throw e;
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                logger.warn("Failed to restore auto-commit state: {}", e.getMessage());
            }
            if (keepAliveConnection == null) {
                conn.close();
            }
        }
    }
}
