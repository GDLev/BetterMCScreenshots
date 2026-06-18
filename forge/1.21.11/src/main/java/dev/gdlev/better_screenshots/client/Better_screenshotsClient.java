package dev.gdlev.better_screenshots.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gdlev.better_screenshots.Better_screenshots;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Better_screenshots.MODID, value = Dist.CLIENT)
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
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        ScreenshotCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static boolean onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != 1) return false; // only press, not release

        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() / mc.getWindow().getGuiScale();
        double mouseY = mc.mouseHandler.ypos() / mc.getWindow().getGuiScale();
        int button = event.getButton();

        if (mc.screen instanceof ScreenshotConfigScreen config) {
            if (config.handleClick(button, mouseX, mouseY)) {
                return true;
            }
        }

        if (mc.screen instanceof ScreenshotFullscreenScreen fullscreen) {
            if (button == 0) {
                if (fullscreen.handleNavClick(mouseX, mouseY)) {
                    return true;
                }
            }
        }

        if (mc.screen == null || mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen) {
            if (button == 0) {
                double previewMouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()
                        / mc.getWindow().getScreenWidth();
                double previewMouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight()
                        / mc.getWindow().getScreenHeight();
                if (ScreenshotPreviewRenderer.handleClick(previewMouseX, previewMouseY)) {
                    return true;
                }
            }
        }
        return false;
    }
}
