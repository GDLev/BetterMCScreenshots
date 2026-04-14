package dev.gdlev.better_screenshots.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

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

    public Corner corner = Corner.BOTTOM_RIGHT;
    public ShutterSound shutterSound = ShutterSound.SOFT;
    public boolean animations = true;
    public AnimationsMode animationsMode = AnimationsMode.ON;
    public ChatNotification chatNotification = ChatNotification.MODERN;
    public FlashMode flashMode = FlashMode.PREVIEW;
    public int previewDurationSeconds = 4;

    private static ScreenshotConfig instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("better_screenshots.json");

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

        if (instance.animationsMode == null) {
            instance.animationsMode = instance.animations ? AnimationsMode.ON : AnimationsMode.OFF;
        }
        instance.animations = instance.animationsMode == AnimationsMode.ON;
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(instance, w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
