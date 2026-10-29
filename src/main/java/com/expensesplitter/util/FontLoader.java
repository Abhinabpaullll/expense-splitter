package com.expensesplitter.util;

import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Utility to load bundled IBM Plex Sans and IBM Plex Mono fonts.
 */
public final class FontLoader {

    private static final Logger logger = LoggerFactory.getLogger(FontLoader.class);
    private static boolean fontsLoaded = false;

    private static final String[] FONT_RESOURCES = {
        "/fonts/IBMPlexSans-Regular.ttf",
        "/fonts/IBMPlexSans-Medium.ttf",
        "/fonts/IBMPlexSans-SemiBold.ttf",
        "/fonts/IBMPlexMono-Regular.ttf",
        "/fonts/IBMPlexMono-Medium.ttf",
        "/fonts/IBMPlexMono-SemiBold.ttf"
    };

    private FontLoader() {
    }

    public static synchronized void loadFonts() {
        if (fontsLoaded) {
            return;
        }

        for (String resourcePath : FONT_RESOURCES) {
            try (InputStream stream = FontLoader.class.getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    logger.error("Could not find font resource: {}", resourcePath);
                    continue;
                }
                Font font = Font.loadFont(stream, 14.0);
                if (font != null) {
                    logger.info("Loaded font: {} (family: {})", font.getName(), font.getFamily());
                } else {
                    logger.warn("Failed to parse font: {}", resourcePath);
                }
            } catch (Exception e) {
                logger.error("Error loading font resource: " + resourcePath, e);
            }
        }

        fontsLoaded = true;
    }
}

