package dev.gdlev.better_screenshots.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.gdlev.better_screenshots.Better_screenshots;
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
        final String screenshotId = String.valueOf(System.nanoTime());

        Screenshot.takeScreenshot(framebuffer, image -> {
            if (!fullscreenOpen) {
                client.execute(() -> ScreenshotPreviewRenderer.setPreview(image));
            } else {
                image.close();
            }

            if (cfg.chatNotification == ScreenshotConfig.ChatNotification.MODERN) {
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
                                    .withHoverEvent(new HoverEvent.ShowText(
                                            Component.translatable("better_screenshots.chat.open_folder_hint")
                                                    .withStyle(ChatFormatting.DARK_GRAY)))
                                    .withClickEvent(new ClickEvent.OpenFile(
                                            new File(client.gameDirectory, "screenshots")
                                                    .getAbsolutePath())));

                    MutableComponent sep = Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY);

                    MutableComponent previewBtn = Component.literal("[")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.translatable("better_screenshots.chat.preview")
                                    .withStyle(s -> s
                                            .withColor(ChatFormatting.YELLOW)
                                            .withBold(false)
                                            .withHoverEvent(new HoverEvent.ShowText(
                                                    Component.translatable("better_screenshots.chat.preview_hint")
                                                            .withStyle(ChatFormatting.DARK_GRAY)))
                                            .withClickEvent(new ClickEvent.RunCommand(
                                                    "/better_screenshots preview " + screenshotId))))
                            .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));

                    MutableComponent sep2 = Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY);

                    MutableComponent copyBtn = Component.literal("[")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.translatable("better_screenshots.chat.copy")
                                    .withStyle(s -> s
                                            .withColor(ChatFormatting.GREEN)
                                            .withBold(false)
                                            .withHoverEvent(new HoverEvent.ShowText(
                                                    Component.translatable("better_screenshots.chat.copy_hint")
                                                            .withStyle(ChatFormatting.DARK_GRAY)))
                                            .withClickEvent(new ClickEvent.RunCommand(
                                                    "/better_screenshots copy " + screenshotId))))
                            .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));

                    if (client.player != null) {
                        client.player.displayClientMessage(
                                prefix.append(fileLink).append(sep)
                                        .append(previewBtn).append(sep2).append(copyBtn), false);
                    }
                });
            }

            client.execute(() -> {
                if (cfg.shutterSound == ScreenshotConfig.ShutterSound.NONE) return;
                net.minecraft.sounds.SoundEvent sound = switch (cfg.shutterSound) {
                    case SOFT    -> Better_screenshots.SHUTTER_SOFT.get();
                    case CLASSIC -> Better_screenshots.SHUTTER_CLASSIC.get();
                    default      -> null;
                };
                if (sound != null)
                    client.getSoundManager().play(
                            SimpleSoundInstance.forUI(sound, 1.0f));
            });
        });

        ci.cancel();
    }
}