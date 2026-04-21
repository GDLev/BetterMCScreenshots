package dev.gdlev.better_screenshots.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gdlev.better_screenshots.Better_screenshots;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
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
                Component.translatable("key.categories.better_screenshots").getString()
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
        ScreenshotPreviewRenderer.flushPendingClose();
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        ScreenshotCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != 1) return; // only press, not release

        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() / mc.getWindow().getGuiScale();
        double mouseY = mc.mouseHandler.ypos() / mc.getWindow().getGuiScale();
        int button = event.getButton();

        if (mc.screen instanceof ScreenshotConfigScreen config) {
            if (config.handleClick(button, mouseX, mouseY)) {
                event.setCanceled(true);
                return;
            }
        }

        if (mc.screen instanceof ScreenshotFullscreenScreen fullscreen) {
            if (button == 0) {
                if (fullscreen.handleNavClick(mouseX, mouseY)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }

        if (!(mc.screen instanceof ScreenshotGalleryScreen)
                && !(mc.screen instanceof ScreenshotFullscreenScreen)
                && !(mc.screen instanceof ScreenshotConfigScreen)) {
            if (button == 0) {
                if (ScreenshotPreviewRenderer.handleClick(mouseX, mouseY)) {
                    event.setCanceled(true);
                }
            }
        }
    }
}
