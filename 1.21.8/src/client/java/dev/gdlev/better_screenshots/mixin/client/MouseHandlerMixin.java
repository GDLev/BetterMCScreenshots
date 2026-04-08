package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotConfigScreen;
import dev.gdlev.better_screenshots.client.ScreenshotFullscreenScreen;
import dev.gdlev.better_screenshots.client.ScreenshotGalleryScreen;
import dev.gdlev.better_screenshots.client.ScreenshotPreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (action != 1) return;

        Minecraft mc = Minecraft.getInstance();

        double mouseX = mc.mouseHandler.xpos() / mc.getWindow().getGuiScale();
        double mouseY = mc.mouseHandler.ypos() / mc.getWindow().getGuiScale();

        // Configuration - Handle clicks on thumbnails and action buttons
        if (mc.screen instanceof ScreenshotConfigScreen config) {
            if (config.handleClick(button, mouseX, mouseY)) {
                ci.cancel();
                return;
            }
        }

        // Fullscreen - Handle navigation arrow/action clicks
        if (mc.screen instanceof ScreenshotFullscreenScreen fullscreen) {
            if (button == 0) {
                if (fullscreen.handleNavClick(mouseX, mouseY)) {
                    ci.cancel();
                    return;
                }
            }
        }

        // Preview - works when there is no screenshot OR when the screenshot is not a gallery/fullscreen
        if (!(mc.screen instanceof ScreenshotGalleryScreen)
                && !(mc.screen instanceof ScreenshotFullscreenScreen)
                && !(mc.screen instanceof ScreenshotConfigScreen)) {
            if (button == 0) {
                if (ScreenshotPreviewRenderer.handleClick(mouseX, mouseY)) {
                    ci.cancel();
                }
            }
        }
    }
}
