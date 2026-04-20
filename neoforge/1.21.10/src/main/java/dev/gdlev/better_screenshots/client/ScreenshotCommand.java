package dev.gdlev.better_screenshots.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ScreenshotCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("better_screenshots")
                .then(Commands.literal("preview")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(context -> {
                                    String id = StringArgumentType.getString(context, "id");
                                    ScreenshotFullscreenScreen screen = new ScreenshotFullscreenScreen(null);
                                    screen.setFromHud(false);
                                    ScreenshotPreviewRenderer.loadAndPreview(id, screen);
                                    ScreenshotPreviewRenderer.captureBackground(() ->
                                            net.minecraft.client.Minecraft.getInstance().setScreen(screen));
                                    return 1;
                                })))
                .then(Commands.literal("copy")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(context -> {
                                    String id = StringArgumentType.getString(context, "id");
                                    ScreenshotPreviewRenderer.copyFile(id);
                                    return 1;
                                })))
        );
    }
}
