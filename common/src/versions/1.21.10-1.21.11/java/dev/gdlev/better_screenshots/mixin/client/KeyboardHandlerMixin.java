package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotPreviewRenderer;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void betterScreenshots$handlePreviewRenameKey(
            long window,
            int action,
            KeyEvent input,
            CallbackInfo ci) {
        if (ScreenshotPreviewRenderer.handleRenameKey(action, input.key())) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void betterScreenshots$handlePreviewRenameChar(
            long window,
            CharacterEvent input,
            CallbackInfo ci) {
        if (ScreenshotPreviewRenderer.handleRenameChar(input.codepointAsString())) {
            ci.cancel();
        }
    }
}
