package dev.gdlev.better_screenshots.client;

import dev.gdlev.better_screenshots.common.ScreenshotConfigData.MenuButtonPosition;
import dev.gdlev.better_screenshots.common.ScreenshotConfigData.PauseButtonAnchor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PauseMenuButtonLayout {

    public static final int SETTINGS = 0;
    public static final int GALLERY = 1;
    public static final int SCREENSHOT = 2;
    public static final int ACTION_COUNT = 3;

    private PauseMenuButtonLayout() {}

    public static void ensureMigrated() {
        ScreenshotConfig config = ScreenshotConfig.get();
        if (config.pauseButtonLayoutMigrated) return;
        applyPreset(config.menuButtonPosition, false);
        config.pauseButtonLayoutMigrated = true;
        ScreenshotConfig.save();
    }

    public static void applyPreset(MenuButtonPosition position) {
        applyPreset(position, true);
    }

    private static void applyPreset(MenuButtonPosition position, boolean save) {
        ScreenshotConfig config = ScreenshotConfig.get();
        boolean visible = position != MenuButtonPosition.DISABLED;
        PauseButtonAnchor anchor = switch (position) {
            case TOP_LEFT -> PauseButtonAnchor.TOP_LEFT;
            case TOP_RIGHT -> PauseButtonAnchor.TOP_RIGHT;
            case BOTTOM_LEFT -> PauseButtonAnchor.BOTTOM_LEFT;
            case BOTTOM_RIGHT -> PauseButtonAnchor.BOTTOM_RIGHT;
            case CENTER, DISABLED -> PauseButtonAnchor.CENTER;
        };
        config.pauseSettingsAnchor = anchor;
        config.pauseGalleryAnchor = anchor;
        config.pauseScreenshotAnchor = anchor;
        config.pauseSettingsVisible = visible;
        config.pauseGalleryVisible = visible;
        config.pauseScreenshotVisible = visible;
        config.pauseSettingsOrder = 0;
        config.pauseGalleryOrder = 1;
        config.pauseScreenshotOrder = 2;
        config.pauseButtonLayoutMigrated = true;
        if (save) ScreenshotConfig.save();
    }

    public static PauseButtonAnchor[] anchors() {
        ScreenshotConfig config = ScreenshotConfig.get();
        return new PauseButtonAnchor[] {
                safe(config.pauseSettingsAnchor),
                safe(config.pauseGalleryAnchor),
                safe(config.pauseScreenshotAnchor)
        };
    }

    public static int[] orders() {
        ScreenshotConfig config = ScreenshotConfig.get();
        return new int[] {
                config.pauseSettingsOrder,
                config.pauseGalleryOrder,
                config.pauseScreenshotOrder
        };
    }

    public static boolean[] visibility() {
        ScreenshotConfig config = ScreenshotConfig.get();
        return new boolean[] {
                config.pauseSettingsVisible,
                config.pauseGalleryVisible,
                config.pauseScreenshotVisible
        };
    }

    public static List<Integer> actionsAt(PauseButtonAnchor anchor) {
        PauseButtonAnchor[] anchors = anchors();
        int[] orders = orders();
        boolean[] visible = visibility();
        List<Integer> actions = new ArrayList<>();
        for (int i = 0; i < ACTION_COUNT; i++) {
            if (visible[i] && anchors[i] == anchor) actions.add(i);
        }
        actions.sort(Comparator
                .comparingInt((Integer action) -> orders[action])
                .thenComparingInt(Integer::intValue));
        return actions;
    }

    public static void arrangeCornerButtons(
            int[] x,
            int[] y,
            int screenWidth,
            int screenHeight,
            int buttonSize,
            int gap,
            int margin) {
        for (int i = 0; i < x.length; i++) {
            x[i] = -100;
            y[i] = -100;
        }
        for (PauseButtonAnchor anchor : PauseButtonAnchor.values()) {
            if (anchor == PauseButtonAnchor.CENTER) continue;
            List<Integer> actions = actionsAt(anchor);
            int totalW = actions.size() * buttonSize
                    + Math.max(0, actions.size() - 1) * gap;
            boolean left = anchor == PauseButtonAnchor.TOP_LEFT
                    || anchor == PauseButtonAnchor.BOTTOM_LEFT;
            boolean top = anchor == PauseButtonAnchor.TOP_LEFT
                    || anchor == PauseButtonAnchor.TOP_RIGHT;
            int startX = left ? margin : screenWidth - margin - totalW;
            int buttonY = top ? margin : screenHeight - margin - buttonSize;
            for (int slot = 0; slot < actions.size(); slot++) {
                int action = actions.get(slot);
                x[action] = startX + slot * (buttonSize + gap);
                y[action] = buttonY;
            }
        }
    }

    private static PauseButtonAnchor safe(PauseButtonAnchor anchor) {
        return anchor == null ? PauseButtonAnchor.CENTER : anchor;
    }
}
