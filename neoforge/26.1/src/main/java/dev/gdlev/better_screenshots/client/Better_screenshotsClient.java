package dev.gdlev.better_screenshots.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gdlev.better_screenshots.Better_screenshots;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Better_screenshots.MODID, value = Dist.CLIENT)
public class Better_screenshotsClient {

    private static KeyMapping openConfigKey;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        openConfigKey = new KeyMapping(
                "key.better_screenshots.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                new KeyMapping.Category(Identifier.fromNamespaceAndPath(Better_screenshots.MODID, "main"))
        );
        event.register(openConfigKey);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (openConfigKey != null && openConfigKey.consumeClick()) {
            client.setScreen(new ScreenshotConfigScreen(client.screen));
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ScreenshotPreviewRenderer.render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        ScreenshotCommand.register(event.getDispatcher());
    }
}
