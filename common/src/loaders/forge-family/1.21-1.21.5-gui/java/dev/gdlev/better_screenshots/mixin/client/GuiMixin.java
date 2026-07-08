package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotPreviewRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "renderChat", at = @At("TAIL"))
    private void renderPreviewAfterChat(
            GuiGraphics context, DeltaTracker deltaTracker, CallbackInfo ci) {
        ScreenshotPreviewRenderer.renderInChatLayer(context);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void renderPreviewWithHiddenHud(GuiGraphics context, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!Minecraft.getInstance().options.hideGui) {
            return;
        }

        ScreenshotPreviewRenderer.render(context);
    }
}
