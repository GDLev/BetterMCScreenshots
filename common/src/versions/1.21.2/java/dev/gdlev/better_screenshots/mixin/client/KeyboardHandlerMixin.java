package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotPreviewRenderer;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void betterScreenshots$handlePreviewRenameKey(
            long window,
            int key,
            int scancode,
            int action,
            int modifiers,
            CallbackInfo ci) {
        if (ScreenshotPreviewRenderer.handleRenameKey(action, key)) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void betterScreenshots$handlePreviewRenameChar(
            long window,
            int codePoint,
            int modifiers,
            CallbackInfo ci) {
        if (ScreenshotPreviewRenderer.handleRenameChar(codePoint)) {
            ci.cancel();
        }
    }
}
