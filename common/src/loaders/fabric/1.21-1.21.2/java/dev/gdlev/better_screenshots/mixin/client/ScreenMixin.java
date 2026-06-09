package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotFullscreenScreen;
import dev.gdlev.better_screenshots.client.ScreenshotPreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void onComponentClicked(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (style != null && style.getClickEvent() != null) {
            ClickEvent event = style.getClickEvent();
            if (event.getAction() == ClickEvent.Action.RUN_COMMAND) {
                String value = event.getValue();
                if (value.startsWith("bs-action:")) {
                    String actionData = value.substring("bs-action:".length());
                    String[] parts = actionData.split(":");
                    if (parts.length >= 2) {
                        String action = parts[0];
                        String id = parts[1];
                        
                        if ("preview".equals(action)) {
                            Minecraft mc = Minecraft.getInstance();
                            ScreenshotFullscreenScreen screen = new ScreenshotFullscreenScreen(null);
                            screen.setFromHud(false);
                            ScreenshotPreviewRenderer.loadAndPreview(id, screen);
                            ScreenshotPreviewRenderer.captureBackground(() -> mc.setScreen(screen));
                        } else if ("copy".equals(action)) {
                            ScreenshotPreviewRenderer.copyFile(id);
                        }
                    }
                    cir.setReturnValue(true);
                    cir.cancel();
                }
            }
        }
    }
}
