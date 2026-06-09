package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotFullscreenScreen;
import dev.gdlev.better_screenshots.client.ScreenshotPreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(
            method = {"defaultHandleGameClickEvent", "m_401746_"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void onGameClickEvent(
            ClickEvent clickEvent,
            Minecraft mc,
            Screen parent,
            CallbackInfo ci
    ) {
        if (!(clickEvent instanceof ClickEvent.RunCommand runCommand)) {
            return;
        }

        String value = runCommand.command();
        if (!value.startsWith("/better_screenshots ")) {
            return;
        }

        String[] parts = value.trim().split("\\s+", 3);
        if (parts.length < 3) {
            return;
        }

        String action = parts[1];
        String id = parts[2];
        if ("preview".equals(action)) {
            ScreenshotFullscreenScreen screen = new ScreenshotFullscreenScreen(parent);
            screen.setFromHud(false);
            mc.setScreen(screen);
            ScreenshotPreviewRenderer.loadAndPreview(id, screen);
        } else if ("copy".equals(action)) {
            ScreenshotPreviewRenderer.copyFile(id);
        } else if ("copy_upload".equals(action)) {
            ScreenshotPreviewRenderer.copyUploadedUrl(id);
        } else {
            return;
        }

        ci.cancel();
    }
}
