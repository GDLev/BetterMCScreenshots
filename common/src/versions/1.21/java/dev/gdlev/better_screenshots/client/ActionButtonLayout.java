package dev.gdlev.better_screenshots.client;

import dev.gdlev.better_screenshots.common.ScreenshotConfigData.ActionButtonCorner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ActionButtonLayout {

    private ActionButtonLayout() {}

    public static void arrange(
            int[] buttonX,
            int[] buttonY,
            ActionButtonCorner[] corners,
            int count,
            int containerX,
            int containerY,
            int containerW,
            int containerH,
            int buttonW,
            int buttonH,
            int gap,
            int margin) {
        int[] order = new int[corners.length];
        boolean[] visible = new boolean[corners.length];
        for (int i = 0; i < corners.length; i++) {
            order[i] = i;
            visible[i] = true;
        }
        arrange(buttonX, buttonY, corners, order, visible, count,
                containerX, containerY, containerW, containerH,
                buttonW, buttonH, gap, margin);
    }

    public static void arrange(
            int[] buttonX,
            int[] buttonY,
            ActionButtonCorner[] corners,
            int[] order,
            boolean[] visible,
            int count,
            int containerX,
            int containerY,
            int containerW,
            int containerH,
            int buttonW,
            int buttonH,
            int gap,
            int margin) {
        int safeCount = Math.min(count, Math.min(buttonX.length, corners.length));
        for (int i = 0; i < buttonX.length; i++) {
            buttonX[i] = -100;
            buttonY[i] = -100;
        }

        for (ActionButtonCorner corner : ActionButtonCorner.values()) {
            List<Integer> cornerActions = new ArrayList<>();
            for (int i = 0; i < safeCount; i++) {
                if (i < visible.length && visible[i] && safeCorner(corners[i]) == corner) {
                    cornerActions.add(i);
                }
            }
            cornerActions.sort(Comparator
                    .comparingInt((Integer action) ->
                            action < order.length ? order[action] : action)
                    .thenComparingInt(Integer::intValue));
            int cornerCount = cornerActions.size();
            if (cornerCount == 0) continue;

            int totalW = cornerCount * buttonW + Math.max(0, cornerCount - 1) * gap;
            int startX = isLeft(corner)
                    ? containerX + margin
                    : containerX + containerW - margin - totalW;
            int y = isTop(corner)
                    ? containerY + margin
                    : containerY + containerH - margin - buttonH;

            for (int slot = 0; slot < cornerActions.size(); slot++) {
                int action = cornerActions.get(slot);
                buttonX[action] = startX + slot * (buttonW + gap);
                buttonY[action] = y;
            }
        }
    }

    public static ActionButtonCorner nearestCorner(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height) {
        boolean left = mouseX < x + width / 2.0;
        boolean top = mouseY < y + height / 2.0;
        if (top) {
            return left ? ActionButtonCorner.TOP_LEFT : ActionButtonCorner.TOP_RIGHT;
        }
        return left ? ActionButtonCorner.BOTTOM_LEFT : ActionButtonCorner.BOTTOM_RIGHT;
    }

    public static ActionButtonCorner safeCorner(ActionButtonCorner corner) {
        return corner == null ? ActionButtonCorner.TOP_RIGHT : corner;
    }

    private static boolean isLeft(ActionButtonCorner corner) {
        return corner == ActionButtonCorner.TOP_LEFT
                || corner == ActionButtonCorner.BOTTOM_LEFT;
    }

    private static boolean isTop(ActionButtonCorner corner) {
        return corner == ActionButtonCorner.TOP_LEFT
                || corner == ActionButtonCorner.TOP_RIGHT;
    }
}
