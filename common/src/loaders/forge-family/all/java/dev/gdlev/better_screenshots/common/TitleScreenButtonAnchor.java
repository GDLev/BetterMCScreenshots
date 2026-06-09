package dev.gdlev.better_screenshots.common;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public record TitleScreenButtonAnchor(int x, int y) {
    private static final Component ACCESSIBILITY_LABEL = Component.translatable("options.accessibility");

    public static TitleScreenButtonAnchor afterAccessibility(Screen screen, int gap, int fallbackX, int fallbackY) {
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.getMessage().equals(ACCESSIBILITY_LABEL)) {
                return new TitleScreenButtonAnchor(widget.getRight() + gap, widget.getY());
            }
        }
        return new TitleScreenButtonAnchor(fallbackX, fallbackY);
    }
}
