package dev.gdlev.better_screenshots.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import dev.gdlev.better_screenshots.Better_screenshots;
import dev.gdlev.better_screenshots.client.Better_screenshotsClient;
import dev.gdlev.better_screenshots.client.ScreenshotConfig;
import dev.gdlev.better_screenshots.client.ScreenshotPreviewRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

@Mixin(Screenshot.class)
public class ScreenshotRecorderMixin {
    @Unique
    private static boolean suppressNext = false;

    @Inject(method = "grab*", at = @At("HEAD"), cancellable = true)
    private static void onSaveScreenshot(
            File gameDirectory,
            RenderTarget framebuffer,
            Consumer<Component> messageReceiver,
            CallbackInfo ci
    ) {
        if (suppressNext) {
            suppressNext = false;
            return;
        }

        if (framebuffer == null) return;

        Minecraft client = Minecraft.getInstance();
        ScreenshotConfig cfg   = ScreenshotConfig.get();

        suppressNext = true;
        Screenshot.grab(gameDirectory, framebuffer, cfg.chatNotification == ScreenshotConfig.ChatNotification.DEFAULT ? messageReceiver : msg -> {});

        final boolean fullscreenOpen = client.screen instanceof dev.gdlev.better_screenshots.client.ScreenshotFullscreenScreen;
        final String screenshotId = Integer.toHexString((int)System.currentTimeMillis());

        NativeImage image = Screenshot.takeScreenshot(framebuffer);
        if (!fullscreenOpen) {
            client.execute(() -> ScreenshotPreviewRenderer.setPreview(image));
        } else {
            image.close();
        }

        client.execute(() -> {
            File screenshotsDir = new File(client.gameDirectory, "screenshots");
            File[] files = screenshotsDir.listFiles(
                    f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));
            String fileName = Component.translatable(
                    "better_screenshots.chat.default_filename").getString();
            File screenshotFile = null;
            if (files != null && files.length > 0) {
                java.util.Arrays.sort(files,
                        java.util.Comparator.comparingLong(File::lastModified).reversed());
                screenshotFile = files[0];
                fileName = screenshotFile.getName();
            }

            if (screenshotFile != null) {
                ScreenshotPreviewRenderer.registerFile(screenshotId, screenshotFile);
            }

            final String finalFileName = fileName;

            MutableComponent prefix = Component.literal("").withStyle(ChatFormatting.GRAY);

            MutableComponent fileLink = Component.literal(finalFileName)
                    .withStyle(s -> s
                            .withUnderlined(true)
                            .withColor(ChatFormatting.GRAY)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("better_screenshots.chat.open_folder_hint")
                                            .withStyle(ChatFormatting.DARK_GRAY)))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE,
                                    new File(client.gameDirectory, "screenshots")
                                            .getAbsolutePath())));

            MutableComponent sep = Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY);

            MutableComponent previewBtn = Component.literal("[")
                    .append(Component.translatable("better_screenshots.chat.preview"))
                    .append("]")
                    .withStyle(s -> s
                            .withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "bs-action:preview:" + screenshotId))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("better_screenshots.chat.preview_hint")
                                            .withStyle(ChatFormatting.DARK_GRAY))));

            MutableComponent sep2 = Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY);

            MutableComponent copyBtn = Component.literal("[")
                    .append(Component.translatable("better_screenshots.chat.copy"))
                    .append("]")
                    .withStyle(s -> s
                            .withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "bs-action:copy:" + screenshotId))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("better_screenshots.chat.copy_hint")
                                            .withStyle(ChatFormatting.DARK_GRAY))));


            if (client.player != null) {
                client.player.displayClientMessage(
                        prefix.append(fileLink).append(sep)
                                .append(previewBtn).append(sep2).append(copyBtn), false);
            }
        });
        client.execute(() -> {
            if (cfg.shutterSound == ScreenshotConfig.ShutterSound.NONE) return;
            net.minecraft.sounds.SoundEvent sound = switch (cfg.shutterSound) {
                case SOFT    -> Better_screenshotsClient.SHUTTER_SOFT;
                case CLASSIC -> Better_screenshotsClient.SHUTTER_CLASSIC;
                default      -> null;
            };
            if (sound != null)
                client.getSoundManager().play(
                        SimpleSoundInstance.forUI(sound, 1.0f));
        });

        ci.cancel();
    }
}
