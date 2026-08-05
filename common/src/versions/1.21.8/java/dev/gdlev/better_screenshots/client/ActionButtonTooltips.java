package dev.gdlev.better_screenshots.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class ActionButtonTooltips {
    private ActionButtonTooltips() {}

    static void draw(
            GuiGraphics context,
            Font font,
            int screenW,
            int screenH,
            double mouseX,
            double mouseY,
            int action,
            boolean closeFirst) {
        if (action < 0 || !ScreenshotConfig.get().actionButtonTooltips) return;
        Component text = label(action, closeFirst);
        context.setTooltipForNextFrame(font, text, (int) mouseX, (int) mouseY);
    }

    private static Component label(int action, boolean closeFirst) {
        return Component.translatable(switch (action) {
            case 0 -> closeFirst
                    ? "better_screenshots.config.actions.action.close"
                    : "better_screenshots.config.actions.action.show";
            case 1 -> "better_screenshots.config.actions.action.copy";
            case 2 -> "better_screenshots.config.actions.action.upload";
            case 3 -> "better_screenshots.config.actions.action.delete";
            default -> "better_screenshots.config.actions.configure";
        });
    }

}
