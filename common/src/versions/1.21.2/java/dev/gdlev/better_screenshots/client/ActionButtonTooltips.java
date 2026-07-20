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
        int textW = font.width(text);
        int x = Math.min((int) mouseX + 10, screenW - textW - 8);
        int y = Math.min((int) mouseY + 10, screenH - 16);
        x = Math.max(4, x);
        y = Math.max(4, y);
        context.pose().pushPose();
        context.pose().translate(0, 0, 800f);
        context.fill(x - 3, y - 3, x + textW + 3, y + 11, 0xF0101010);
        context.fill(x - 3, y - 3, x + textW + 3, y - 2, 0xFF555555);
        context.fill(x - 3, y + 10, x + textW + 3, y + 11, 0xFF555555);
        context.fill(x - 3, y - 3, x - 2, y + 11, 0xFF555555);
        context.fill(x + textW + 2, y - 3, x + textW + 3, y + 11, 0xFF555555);
        context.drawString(font, text, x, y, 0xFFFFFFFF, false);
        context.pose().popPose();
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
