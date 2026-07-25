package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotPreviewRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenPreviewMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void renderPreviewAboveScreen(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ScreenshotPreviewRenderer.renderAboveScreens(context);
    }
}
