package dev.gdlev.better_screenshots.client;

import dev.gdlev.better_screenshots.common.ScreenshotConfigData.ActionButtonCorner;
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
        if (config.pause26_1ButtonLayoutMigrated) return;
        ActionButtonCorner corner = switch (config.menuButtonPosition) {
            case TOP_LEFT -> ActionButtonCorner.TOP_LEFT;
            case TOP_RIGHT -> ActionButtonCorner.TOP_RIGHT;
            case BOTTOM_RIGHT -> ActionButtonCorner.BOTTOM_RIGHT;
            case CENTER, BOTTOM_LEFT, DISABLED -> ActionButtonCorner.BOTTOM_LEFT;
        };
        boolean visible = config.menuButtonPosition != MenuButtonPosition.DISABLED;
        config.pause26_1SettingsCorner = corner;
        config.pause26_1GalleryCorner = corner;
        config.pause26_1ScreenshotCorner = corner;
        config.pause26_1SettingsVisible = visible;
        config.pause26_1GalleryVisible = visible;
        config.pause26_1ScreenshotVisible = visible;
        config.pause26_1ButtonLayoutMigrated = true;
        ScreenshotConfig.save();
    }

    public static PauseButtonAnchor[] anchors() {
        ScreenshotConfig config = ScreenshotConfig.get();
        return new PauseButtonAnchor[] {
                toAnchor(config.pause26_1SettingsCorner),
                toAnchor(config.pause26_1GalleryCorner),
                toAnchor(config.pause26_1ScreenshotCorner)
        };
    }

    public static int[] orders() {
        ScreenshotConfig config = ScreenshotConfig.get();
        return new int[] {
                config.pause26_1SettingsOrder,
                config.pause26_1GalleryOrder,
                config.pause26_1ScreenshotOrder
        };
    }

    public static boolean[] visibility() {
        ScreenshotConfig config = ScreenshotConfig.get();
        return new boolean[] {
                config.pause26_1SettingsVisible,
                config.pause26_1GalleryVisible,
                config.pause26_1ScreenshotVisible
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

    public static ActionButtonCorner fromAnchor(PauseButtonAnchor anchor) {
        return switch (anchor) {
            case TOP_LEFT -> ActionButtonCorner.TOP_LEFT;
            case TOP_RIGHT -> ActionButtonCorner.TOP_RIGHT;
            case BOTTOM_LEFT, CENTER -> ActionButtonCorner.BOTTOM_LEFT;
            case BOTTOM_RIGHT -> ActionButtonCorner.BOTTOM_RIGHT;
        };
    }

    private static PauseButtonAnchor toAnchor(ActionButtonCorner corner) {
        if (corner == null) return PauseButtonAnchor.BOTTOM_LEFT;
        return switch (corner) {
            case TOP_LEFT -> PauseButtonAnchor.TOP_LEFT;
            case TOP_RIGHT -> PauseButtonAnchor.TOP_RIGHT;
            case BOTTOM_LEFT -> PauseButtonAnchor.BOTTOM_LEFT;
            case BOTTOM_RIGHT -> PauseButtonAnchor.BOTTOM_RIGHT;
        };
    }
}
