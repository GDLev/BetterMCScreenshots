package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotConfigScreen;
import dev.gdlev.better_screenshots.client.ScreenshotFullscreenScreen;
import dev.gdlev.better_screenshots.client.ScreenshotPreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class LegacyMouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (action != 1) return;

        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

        if (mc.screen instanceof ScreenshotConfigScreen config && config.handleClick(button, mouseX, mouseY)) {
            ci.cancel();
            return;
        }

        if (mc.screen instanceof ScreenshotFullscreenScreen fullscreen && button == 0 && fullscreen.handleNavClick(mouseX, mouseY)) {
            ci.cancel();
            return;
        }

        if (button == 0 && (mc.screen == null
                || mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen
                || ScreenshotPreviewRenderer.isPreviewAboveScreen())
                && ScreenshotPreviewRenderer.handleClick(mouseX, mouseY)) {
            ci.cancel();
        }
    }
}
