package dev.gdlev.better_screenshots.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ScreenshotCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("better_screenshots")
                        .then(Commands.literal("preview")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(context -> {
                                            String id = StringArgumentType.getString(context, "id");
                                            Minecraft mc = Minecraft.getInstance();
                                            mc.execute(() -> {
                                                ScreenshotFullscreenScreen screen =
                                                        new ScreenshotFullscreenScreen(mc.screen);
                                                mc.setScreen(screen);
                                                ScreenshotPreviewRenderer.loadAndPreview(id, screen);
                                            });
                                            return 1;
                                        }))
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    mc.execute(() -> {
                                        if (ScreenshotPreviewRenderer.getPreviewTexture() != null) {
                                            ScreenshotFullscreenScreen screen =
                                                    new ScreenshotFullscreenScreen(mc.screen);
                                            screen.useCurrentTexture();
                                            mc.setScreen(screen);
                                        }
                                    });
                                    return 1;
                                }))
                        .then(Commands.literal("copy")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(context -> {
                                            String id = StringArgumentType.getString(context, "id");
                                            Minecraft mc = Minecraft.getInstance();
                                            mc.execute(() -> ScreenshotPreviewRenderer.copyFile(id));
                                            return 1;
                                        }))
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    mc.execute(() -> {
                                        try {
                                            java.lang.reflect.Method m =
                                                    ScreenshotPreviewRenderer.class
                                                            .getDeclaredMethod("copyToClipboard");
                                            m.setAccessible(true);
                                            m.invoke(null);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    });
                                    return 1;
                                }))
                        .then(Commands.literal("copy_upload")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(context -> {
                                            String id = StringArgumentType.getString(context, "id");
                                            Minecraft mc = Minecraft.getInstance();
                                            mc.execute(() -> ScreenshotPreviewRenderer.copyUploadedUrl(id));
                                            return 1;
                                        })))
        );
    }
}
