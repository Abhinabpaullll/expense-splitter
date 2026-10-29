package com.expensesplitter.util;

import com.expensesplitter.dao.*;
import com.expensesplitter.dao.impl.*;
import com.expensesplitter.model.Member;
import com.expensesplitter.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global application dependency registry with constructor injection.
 */
public class AppContext {

    private static final Logger logger = LoggerFactory.getLogger(AppContext.class);
    private static AppContext instance;

    private final DatabaseManager databaseManager;
    private final MemberDao memberDao;
    private final GroupDao groupDao;
    private final ExpenseDao expenseDao;
    private final PaymentDao paymentDao;
    private final ReminderDao reminderDao;
    private final UserDao userDao;
    private final SettingsDao settingsDao;
    private final SessionManager sessionManager;

    private final SplitCalculationService splitCalculationService;
    private final DebtSimplificationService debtSimplificationService;
    private final BalanceService balanceService;
    private final GroupService groupService;
    private final ExpenseService expenseService;
    private final NavigationService navigationService;
    private final ReminderService reminderService;

    public AppContext(DatabaseManager dbManager) {
        this.databaseManager = dbManager;
        this.memberDao = new SqliteMemberDao(databaseManager);
        this.groupDao = new SqliteGroupDao(databaseManager);
        this.expenseDao = new SqliteExpenseDao(databaseManager);
        this.paymentDao = new SqlitePaymentDao(databaseManager);
        this.reminderDao = new SqliteReminderDao(databaseManager);
        this.userDao = new SqliteUserDao(databaseManager);
        this.settingsDao = new SqliteSettingsDao(databaseManager);
        this.sessionManager = new SessionManager(userDao, memberDao);

        this.splitCalculationService = new SplitCalculationService();
        this.debtSimplificationService = new DebtSimplificationService();
        this.balanceService = new BalanceService(expenseDao, paymentDao, debtSimplificationService);
        this.groupService = new GroupService(groupDao, memberDao, expenseDao, paymentDao, splitCalculationService);
        this.expenseService = new ExpenseService(expenseDao, splitCalculationService);
        this.navigationService = new NavigationService();
        this.reminderService = new ReminderService();
        this.reminderService.startScheduler(4);

        // Auto-login last active user if session exists
        settingsDao.get("last_signed_in_user_id").ifPresent(lastUserId -> {
            userDao.findById(lastUserId).ifPresent(sessionManager::setCurrentUser);
        });

        // Save last active user on session changes
        sessionManager.addSessionListener(user -> {
            if (user != null) {
                settingsDao.set("last_signed_in_user_id", user.id());
            } else {
                settingsDao.delete("last_signed_in_user_id");
            }
        });
    }

    public static synchronized void initialize(DatabaseManager dbManager) {
        if (instance == null) {
            instance = new AppContext(dbManager);
        }
    }

    public static synchronized AppContext getInstance() {
        if (instance == null) {
            instance = new AppContext(new DatabaseManager());
        }
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public MemberDao getMemberDao() {
        return memberDao;
    }

    public GroupDao getGroupDao() {
        return groupDao;
    }

    public ExpenseDao getExpenseDao() {
        return expenseDao;
    }

    public PaymentDao getPaymentDao() {
        return paymentDao;
    }

    public ReminderDao getReminderDao() {
        return reminderDao;
    }

    public SplitCalculationService getSplitCalculationService() {
        return splitCalculationService;
    }

    public DebtSimplificationService getDebtSimplificationService() {
        return debtSimplificationService;
    }

    public BalanceService getBalanceService() {
        return balanceService;
    }

    public GroupService getGroupService() {
        return groupService;
    }

    public ExpenseService getExpenseService() {
        return expenseService;
    }

    public NavigationService getNavigationService() {
        return navigationService;
    }

    public ReminderService getReminderService() {
        return reminderService;
    }

    public UserDao getUserDao() {
        return userDao;
    }

    public SettingsDao getSettingsDao() {
        return settingsDao;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public Member getCurrentUser() {
        return sessionManager.getCurrentMember();
    }

    public com.expensesplitter.model.User getCurrentAccountUser() {
        return sessionManager.getCurrentUser();
    }

    public void setCurrentUser(Member currentUser) {
        // Kept for backward compatibility if needed
    }
}

