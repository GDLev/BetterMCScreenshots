package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotPreviewRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private GuiRenderState guiRenderState;

    @Shadow
    private Screen screen;

    @Shadow
    private Overlay overlay;

    @Inject(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void betterScreenshots$renderPreviewBehindScreens(DeltaTracker deltaTracker, boolean shouldRenderLevel, boolean resourcesLoaded, CallbackInfo ci) {
        if (!betterScreenshots$hasGameHud()) {
            return;
        }

        int mouseX = (int) this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
        int mouseY = (int) this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(this.minecraft, this.guiRenderState, mouseX, mouseY);
        graphics.nextStratum();
        ScreenshotPreviewRenderer.render(graphics);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void betterScreenshots$renderPreviewWithHiddenHud(DeltaTracker deltaTracker, boolean shouldRenderLevel, boolean resourcesLoaded, CallbackInfo ci) {
        if (shouldRenderLevel || !betterScreenshots$hasGameHud() || this.overlay != null || this.screen != null) {
            return;
        }

        int mouseX = (int) this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
        int mouseY = (int) this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(this.minecraft, this.guiRenderState, mouseX, mouseY);
        graphics.nextStratum();
        ScreenshotPreviewRenderer.render(graphics);
    }

    private boolean betterScreenshots$hasGameHud() {
        return this.minecraft.level != null && this.minecraft.player != null;
    }
}
