package dev.gdlev.better_screenshots.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

public class ScreenshotConfig {
    public enum Corner {
        BOTTOM_RIGHT, BOTTOM_LEFT, TOP_RIGHT, TOP_LEFT
    }

    public enum ShutterSound {
        NONE, SOFT, CLASSIC
    }

    public enum AnimationsMode {
        ON, OFF, REDUCED
    }

    public enum ChatNotification {
        MODERN, DEFAULT, DISABLED
    }

    public enum FlashMode {
        PREVIEW, SCREEN
    }

    public enum MenuButtonPosition {
        TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT, DISABLED
    }

    public Corner corner = Corner.BOTTOM_RIGHT;
    public ShutterSound shutterSound = ShutterSound.SOFT;
    // Legacy boolean (kept for backwards compatibility with older config files)
    public boolean animations = true;
    public AnimationsMode animationsMode = AnimationsMode.ON;
    public ChatNotification chatNotification = ChatNotification.MODERN;
    public FlashMode flashMode = FlashMode.PREVIEW;
    public int previewDurationSeconds = 4;
    public MenuButtonPosition menuButtonPosition = MenuButtonPosition.BOTTOM_LEFT;

    private static ScreenshotConfig instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("better_screenshots.json");

    public boolean uiAnimationsEnabled() {
        return animationsMode == AnimationsMode.ON;
    }

    public boolean previewAnimationsEnabled() {
        return animationsMode != AnimationsMode.OFF;
    }

    public static ScreenshotConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                instance = GSON.fromJson(r, ScreenshotConfig.class);
                if (instance == null) instance = new ScreenshotConfig();
            } catch (Exception e) {
                instance = new ScreenshotConfig();
            }
        } else {
            instance = new ScreenshotConfig();
            save();
        }

        // Migration / normalization
        if (instance.animationsMode == null) {
            instance.animationsMode = instance.animations ? AnimationsMode.ON : AnimationsMode.OFF;
        }
        instance.animations = instance.animationsMode == AnimationsMode.ON;

        if (instance.menuButtonPosition == null) {
            instance.menuButtonPosition = MenuButtonPosition.BOTTOM_LEFT;
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(instance, w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}