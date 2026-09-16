package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotPreviewRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenPreviewMixin {
    @ModifyVariable(method = "extractRenderState", at = @At("HEAD"), argsOnly = true, index = 2)
    private int suppressHoverBehindPreviewX(int mouseX) {
        return ScreenshotPreviewRenderer.blocksScreenHover() ? -1 : mouseX;
    }

    @ModifyVariable(method = "extractRenderState", at = @At("HEAD"), argsOnly = true, index = 3)
    private int suppressHoverBehindPreviewY(int mouseY) {
        return ScreenshotPreviewRenderer.blocksScreenHover() ? -1 : mouseY;
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void renderPreviewAboveScreen(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        context.nextStratum();
        ScreenshotPreviewRenderer.renderAboveScreens(context);
    }
}
