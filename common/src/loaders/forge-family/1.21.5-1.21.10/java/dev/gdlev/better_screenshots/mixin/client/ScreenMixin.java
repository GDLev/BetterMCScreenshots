package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotFullscreenScreen;
import dev.gdlev.better_screenshots.client.ScreenshotPreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void renderPreviewAboveScreen(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ScreenshotPreviewRenderer.renderAboveScreens(context);
    }

    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void onComponentClicked(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (style == null || !(style.getClickEvent() instanceof ClickEvent.RunCommand runCommand)) {
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
            Minecraft mc = Minecraft.getInstance();
            ScreenshotFullscreenScreen screen = new ScreenshotFullscreenScreen(mc.screen);
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

        cir.setReturnValue(true);
    }
}
