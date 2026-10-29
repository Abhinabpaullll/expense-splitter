package com.expensesplitter.util;

import com.expensesplitter.dao.MemberDao;
import com.expensesplitter.dao.UserDao;
import com.expensesplitter.model.Member;
import com.expensesplitter.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages the authenticated user session, account switching, and sign-out state.
 */
public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final UserDao userDao;
    private final MemberDao memberDao;
    private User currentUser;
    private Member currentMember;
    private final List<Consumer<User>> sessionListeners = new CopyOnWriteArrayList<>();

    public SessionManager(UserDao userDao, MemberDao memberDao) {
        this.userDao = userDao;
        this.memberDao = memberDao;
    }

    public synchronized void setCurrentUser(User user) {
        this.currentUser = user;
        if (user != null) {
            // Ensure a matching Member record exists linked to this user_id
            Optional<Member> existing = memberDao.findByUserId(user.id());
            if (existing.isPresent()) {
                this.currentMember = existing.get();
                // Update display name if changed
                if (!existing.get().getName().equals(user.displayName()) ||
                    (user.email() != null && !user.email().equals(existing.get().getEmail()))) {
                    Member updated = new Member(existing.get().getId(), user.displayName(), user.email(), existing.get().getPhone(), user.id(), existing.get().getCreatedAt());
                    memberDao.update(updated);
                    this.currentMember = updated;
                }
            } else {
                Member newMember = new Member(UUID.randomUUID().toString(), user.displayName(), user.email(), null, user.id(), System.currentTimeMillis());
                memberDao.save(newMember);
                this.currentMember = newMember;
            }
            userDao.updateLastLogin(user.id(), System.currentTimeMillis());
            logger.info("Session established for user: {} ({})", user.displayName(), user.id());
        } else {
            this.currentMember = null;
            logger.info("User signed out.");
        }
        notifyListeners();
    }

    public synchronized User getCurrentUser() {
        return currentUser;
    }

    public synchronized Member getCurrentMember() {
        return currentMember;
    }

    public synchronized boolean isSignedIn() {
        return currentUser != null;
    }

    public synchronized void signOut() {
        setCurrentUser(null);
    }

    public void addSessionListener(Consumer<User> listener) {
        sessionListeners.add(listener);
        listener.accept(currentUser);
    }

    public void removeSessionListener(Consumer<User> listener) {
        sessionListeners.remove(listener);
    }

    private void notifyListeners() {
        for (Consumer<User> listener : sessionListeners) {
            try {
                listener.accept(currentUser);
            } catch (Exception e) {
                logger.error("Error notifying session listener", e);
            }
        }
    }
}

