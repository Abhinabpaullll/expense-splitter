package com.expensesplitter.service;

import com.expensesplitter.model.Group;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Handles view routing and state navigation across screens.
 */
public class NavigationService {

    public interface ModalRequestHandler {
        void openAddExpenseDialog(Object target);
        void closeModal();
    }

    private ViewType currentView = ViewType.DASHBOARD;
    private Object currentParameter = null;
    private final List<BiConsumer<ViewType, Object>> navigationListeners = new CopyOnWriteArrayList<>();
    private ModalRequestHandler modalRequestHandler;

    public void setModalRequestHandler(ModalRequestHandler handler) {
        this.modalRequestHandler = handler;
    }

    public void openAddExpense(Object target) {
        if (modalRequestHandler != null) {
            modalRequestHandler.openAddExpenseDialog(target);
        }
    }

    public void closeModal() {
        if (modalRequestHandler != null) {
            modalRequestHandler.closeModal();
        }
    }

    public void addNavigationListener(BiConsumer<ViewType, Object> listener) {
        navigationListeners.add(listener);
    }

    public void navigateTo(ViewType viewType) {
        navigateTo(viewType, null);
    }

    public void navigateTo(ViewType viewType, Object parameter) {
        this.currentView = viewType;
        this.currentParameter = parameter;
        for (BiConsumer<ViewType, Object> listener : navigationListeners) {
            listener.accept(viewType, parameter);
        }
    }

    public ViewType getCurrentView() {
        return currentView;
    }

    public Object getCurrentParameter() {
        return currentParameter;
    }
}

