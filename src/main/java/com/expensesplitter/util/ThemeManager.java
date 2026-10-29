package com.expensesplitter.util;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import javafx.application.Application;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Manages application theme (Light Canvas vs Dark Slate) and persists selection.
 */
public final class ThemeManager {

    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);
    private static final String PREF_THEME_KEY = "expense_splitter_theme";
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(ThemeManager.class);

    public enum AppTheme {
        LIGHT("Light Canvas", "/css/tokens-light.css", new PrimerLight()),
        DARK("Dark Slate", "/css/tokens-dark.css", new PrimerDark());

        private final String displayName;
        private final String tokenCssPath;
        private final Theme baseTheme;

        AppTheme(String displayName, String tokenCssPath, Theme baseTheme) {
            this.displayName = displayName;
            this.tokenCssPath = tokenCssPath;
            this.baseTheme = baseTheme;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getTokenCssPath() {
            return tokenCssPath;
        }

        public Theme getBaseTheme() {
            return baseTheme;
        }
    }

    private static AppTheme currentTheme = AppTheme.LIGHT;

    private ThemeManager() {
    }

    public static void initTheme() {
        String savedThemeName = PREFERENCES.get(PREF_THEME_KEY, AppTheme.LIGHT.name());
        try {
            currentTheme = AppTheme.valueOf(savedThemeName);
        } catch (IllegalArgumentException e) {
            currentTheme = AppTheme.LIGHT;
        }
        applyBaseTheme(currentTheme);
    }

    public static AppTheme getCurrentTheme() {
        return currentTheme;
    }

    public static boolean isDarkMode() {
        return currentTheme == AppTheme.DARK;
    }

    public static void setTheme(AppTheme theme, Scene scene) {
        currentTheme = theme;
        PREFERENCES.put(PREF_THEME_KEY, theme.name());
        applyBaseTheme(theme);
        if (scene != null) {
            applyStylesheets(scene);
        }
        logger.info("Theme switched to: {}", theme.getDisplayName());
    }

    public static void toggleTheme(Scene scene) {
        setTheme(isDarkMode() ? AppTheme.LIGHT : AppTheme.DARK, scene);
    }

    public static void applyStylesheets(Scene scene) {
        if (scene == null) return;
        scene.getStylesheets().clear();

        // 1. AtlantaFX base theme
        String baseUserAgent = currentTheme.getBaseTheme().getUserAgentStylesheet();
        Application.setUserAgentStylesheet(baseUserAgent);

        // 2. Token overrides
        String tokensUrl = Objects.requireNonNull(ThemeManager.class.getResource(currentTheme.getTokenCssPath())).toExternalForm();
        scene.getStylesheets().add(tokensUrl);

        // 3. Components styling
        String componentsUrl = Objects.requireNonNull(ThemeManager.class.getResource("/css/components.css")).toExternalForm();
        scene.getStylesheets().add(componentsUrl);
    }

    private static void applyBaseTheme(AppTheme theme) {
        Application.setUserAgentStylesheet(theme.getBaseTheme().getUserAgentStylesheet());
    }
}

