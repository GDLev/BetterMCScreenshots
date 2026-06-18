package dev.gdlev.better_screenshots.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;

public final class MinecraftCompat {
    private MinecraftCompat() {
    }

    public static Screen screen(Minecraft minecraft) {
        return minecraft.gui.screen();
    }

    public static void setScreen(Minecraft minecraft, Screen screen) {
        minecraft.gui.setScreen(screen);
    }

    public static RenderTarget mainRenderTarget(Minecraft minecraft) {
        return minecraft.gameRenderer.mainRenderTarget();
    }

    public static ToastManager toastManager(Minecraft minecraft) {
        return minecraft.gui.toastManager();
    }
}
