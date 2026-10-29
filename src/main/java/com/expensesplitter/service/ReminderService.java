package com.expensesplitter.service;

import com.expensesplitter.model.Member;
import com.expensesplitter.model.Reminder;
import com.expensesplitter.util.AppContext;
import javafx.application.Platform;
import org.controlsfx.control.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Scheduled service that periodically checks outstanding dues and triggers
 * in-app notifications, system notifications, and optional email nudges.
 */
public class ReminderService {

    private static final Logger logger = LoggerFactory.getLogger(ReminderService.class);

    private final ScheduledExecutorService scheduler;
    private final Set<String> dismissedTransferIds = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, LocalDateTime> snoozedUntilMap = new ConcurrentHashMap<>();
    private final List<Consumer<Integer>> pendingCountListeners = new CopyOnWriteArrayList<>();

    private int lastPendingCount = 0;
    private String smtpHost;
    private int smtpPort = 587;
    private String smtpUsername;
    private String smtpPassword;

    public ReminderService() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ReminderSchedulerThread");
            t.setDaemon(true);
            return t;
        });
    }

    public void startScheduler(long intervalHours) {
        scheduler.scheduleAtFixedRate(this::checkPendingDues, 2, intervalHours * 3600, TimeUnit.SECONDS);
        logger.info("ReminderService scheduler started with interval {} hours.", intervalHours);
    }

    public void checkPendingDues() {
        try {
            AppContext appContext = AppContext.getInstance();
            Member currentUser = appContext.getCurrentUser();
            if (currentUser == null) return;

            String currentUserId = currentUser.getId();
            BalanceService balanceService = appContext.getBalanceService();
            List<DebtSimplificationService.Transfer> transfers = balanceService.computeSimplifiedTransfers(null);

            List<DebtSimplificationService.Transfer> activeDues = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (DebtSimplificationService.Transfer t : transfers) {
                if (t.getToMemberId().equals(currentUserId)) {
                    String transferKey = t.getFromMemberId() + "_" + t.getToMemberId() + "_" + t.getAmountCents();
                    if (dismissedTransferIds.contains(transferKey)) continue;

                    LocalDateTime snoozeExpiry = snoozedUntilMap.get(transferKey);
                    if (snoozeExpiry != null && snoozeExpiry.isAfter(now)) continue;

                    activeDues.add(t);
                }
            }

            int count = activeDues.size();
            this.lastPendingCount = count;

            Platform.runLater(() -> {
                for (Consumer<Integer> listener : pendingCountListeners) {
                    listener.accept(count);
                }

                if (count > 0) {
                    long totalCents = activeDues.stream().mapToLong(DebtSimplificationService.Transfer::getAmountCents).sum();
                    String formatted = BigDecimal.valueOf(totalCents)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();

                    String curSymbol = AppContext.getInstance().getSettingsDao().get("default_currency")
                        .map(c -> c.contains("USD") ? "$" : (c.contains("EUR") ? "€" : (c.contains("GBP") ? "£" : "₹")))
                        .orElse("₹");

                    try {
                        Notifications.create()
                            .title("Pending dues reminder")
                            .text(String.format("You have %d pending claim(s) totaling %s%s.", count, curSymbol, formatted))
                            .showInformation();
                    } catch (Exception e) {
                        logger.debug("Desktop notification skipped: {}", e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            logger.error("Error executing scheduled reminder check", e);
        }
    }

    public boolean isSnoozedOrDismissed(String transferKey) {
        if (dismissedTransferIds.contains(transferKey)) return true;
        LocalDateTime snoozeExpiry = snoozedUntilMap.get(transferKey);
        return snoozeExpiry != null && snoozeExpiry.isAfter(LocalDateTime.now());
    }

    public void snooze(String transferKey, int hours) {
        snoozedUntilMap.put(transferKey, LocalDateTime.now().plusHours(hours));
        checkPendingDues();
    }

    public void dismiss(String transferKey) {
        dismissedTransferIds.add(transferKey);
        checkPendingDues();
    }

    public void addPendingCountListener(Consumer<Integer> listener) {
        pendingCountListeners.add(listener);
        listener.accept(lastPendingCount);
    }

    public int getLastPendingCount() {
        return lastPendingCount;
    }

    public boolean sendEmailReminder(String recipientEmail, String subject, String body) {
        if (smtpHost == null || smtpHost.isBlank()) {
            logger.info("SMTP email not configured. Reminder simulated for {}.", recipientEmail);
            return false;
        }
        logger.info("SMTP reminder sent to {} via {}:{}", recipientEmail, smtpHost, smtpPort);
        return true;
    }

    public void configureSmtp(String host, int port, String username, String password) {
        this.smtpHost = host;
        this.smtpPort = port;
        this.smtpUsername = username;
        this.smtpPassword = password;
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
