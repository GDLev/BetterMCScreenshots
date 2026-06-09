package dev.gdlev.better_screenshots.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gdlev.better_screenshots.Better_screenshots;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Better_screenshots.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public class Better_screenshotsClient {

    private static KeyMapping openConfigKey;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        openConfigKey = new KeyMapping(
                "key.better_screenshots.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "key.categories.better_screenshots"
        );
        event.register(openConfigKey);
    }

    @Mod.EventBusSubscriber(modid = Better_screenshots.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    @SuppressWarnings("removal")
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (openConfigKey != null && openConfigKey.consumeClick()) {
                client.setScreen(new ScreenshotConfigScreen(client.screen));
            }
        }

        @SubscribeEvent
        public static void onMouseButton(InputEvent.MouseButton.Pre event) {
            if (event.getAction() != 1) return; // only press, not release

            Minecraft mc = Minecraft.getInstance();
            double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()
                    / mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight()
                    / mc.getWindow().getScreenHeight();
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
}
