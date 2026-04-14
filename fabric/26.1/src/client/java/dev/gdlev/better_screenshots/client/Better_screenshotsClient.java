package dev.gdlev.better_screenshots.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.glfw.GLFW;

public class Better_screenshotsClient implements ClientModInitializer {

    public static final Identifier SHUTTER_SOFT_ID    = Identifier.fromNamespaceAndPath("better_screenshots", "shutter_soft");
    public static final Identifier SHUTTER_CLASSIC_ID = Identifier.fromNamespaceAndPath("better_screenshots", "shutter_classic");
    private static final Identifier HUD_PREVIEW_ID    = Identifier.fromNamespaceAndPath("better_screenshots", "preview_hud");
    public static SoundEvent SHUTTER_SOFT;
    public static SoundEvent SHUTTER_CLASSIC;

    private static KeyMapping openConfigKey;

    @Override
    public void onInitializeClient() {
        ScreenshotConfig.load();

        // Sound mapping
        SHUTTER_SOFT    = Registry.register(BuiltInRegistries.SOUND_EVENT, SHUTTER_SOFT_ID,    SoundEvent.createVariableRangeEvent(SHUTTER_SOFT_ID));
        SHUTTER_CLASSIC = Registry.register(BuiltInRegistries.SOUND_EVENT, SHUTTER_CLASSIC_ID, SoundEvent.createVariableRangeEvent(SHUTTER_CLASSIC_ID));

        // Keybind mapping
        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.better_screenshots.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                new KeyMapping.Category(Identifier.parse("category.better_screenshots"))
        ));

        // Keybinds
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openConfigKey.consumeClick()) {
                client.setScreen(new ScreenshotConfigScreen(client.screen));
            }
        });

        // Connecting the renderer to the HUD
        HudElementRegistry.addLast(HUD_PREVIEW_ID, (context, deltaTracker) -> ScreenshotPreviewRenderer.render(context));

        // Command mapping
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT
                .register((dispatcher, buildContext) ->
                        ScreenshotCommand.register(dispatcher));
    }
}
