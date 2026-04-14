package dev.gdlev.better_screenshots.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

public class ScreenshotCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {

        dispatcher.register(
                ClientCommands.literal("better_screenshots")
                        .then(ClientCommands.literal("preview")
                                .then(ClientCommands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            Minecraft mc = Minecraft.getInstance();
                                            mc.execute(() -> {
                                                ScreenshotFullscreenScreen screen =
                                                        new ScreenshotFullscreenScreen(mc.screen);
                                                mc.setScreen(screen);
                                                ScreenshotPreviewRenderer.loadAndPreview(id, screen);
                                            });
                                            return 1;
                                        }))
                                .executes(ctx -> {
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
                        .then(ClientCommands.literal("copy")
                                .then(ClientCommands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            Minecraft mc = Minecraft.getInstance();
                                            mc.execute(() -> ScreenshotPreviewRenderer.copyFile(id));
                                            return 1;
                                        }))
                                .executes(ctx -> {
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
        );
    }
}
