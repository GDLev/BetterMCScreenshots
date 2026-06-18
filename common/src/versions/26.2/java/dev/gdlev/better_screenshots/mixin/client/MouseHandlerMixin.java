package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotConfigScreen;
import dev.gdlev.better_screenshots.client.ScreenshotFullscreenScreen;
import dev.gdlev.better_screenshots.client.ScreenshotPreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        if (action != 1) return;

        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

        // Configuration - Handle clicks on thumbnails and action buttons
        if (dev.gdlev.better_screenshots.client.MinecraftCompat.screen(mc) instanceof ScreenshotConfigScreen config) {
            if (config.handleClick(input.button(), mouseX, mouseY)) {
                ci.cancel();
                return;
            }
        }

        // Fullscreen - Handle navigation arrow clicks
        if (dev.gdlev.better_screenshots.client.MinecraftCompat.screen(mc) instanceof ScreenshotFullscreenScreen fullscreen) {
            if (input.button() == 0) {
                if (fullscreen.handleNavClick(mouseX, mouseY)) {
                    ci.cancel();
                    return;
                }
            }
        }

        // Mini preview only belongs to gameplay input. Chat is the one allowed
        // screen overlay; normal menus may hide the preview and must not let its
        // stale hitbox consume clicks.
        if (dev.gdlev.better_screenshots.client.MinecraftCompat.screen(mc) == null || dev.gdlev.better_screenshots.client.MinecraftCompat.screen(mc) instanceof net.minecraft.client.gui.screens.ChatScreen) {
            if (input.button() == 0) {
                if (ScreenshotPreviewRenderer.handleClick(mouseX, mouseY)) {
                    ci.cancel();
                }
            }
        }
    }
}
