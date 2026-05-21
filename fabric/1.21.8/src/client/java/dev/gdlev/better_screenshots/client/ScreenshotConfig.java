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

    public enum UploadProvider {
        DISABLED, IMGUR, S3, CUSTOM_HTTP, CATBOX
    }

    public enum UploadMethod {
        POST, PUT
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
    // File-only advanced option: adds extra top offset for the first screenshots row.
    // Useful for custom/modpack menus where default spacing collides with other UI.
    public int screenshotsFirstRowTopMargin = 0;
    // File-only advanced option: hides mini-preview action buttons and disables their clicks.
    // Useful for modpack/custom UI setups where those buttons should not be visible.
    public boolean hideMiniPreviewActionButtons = false;

    public UploadProvider uploadProvider = UploadProvider.DISABLED;
    public boolean uploadAutoUpload = false;
    public boolean uploadChatNotification = true;
    public boolean uploadCopyToClipboard = true;

    public String imgurClientId = "";
    public String imgurAccessToken = "";

    public String s3Endpoint = "";
    public String s3Region = "";
    public String s3Bucket = "";
    public String s3AccessKey = "";
    public String s3SecretKey = "";
    public String s3PathPrefix = "screenshots/";

    public String customUploadUrl = "";
    public UploadMethod customUploadMethod = UploadMethod.POST;
    public String customCookieKey = "";
    public String customCookieValue = "";
    public String customHeaderKey = "";
    public String customHeaderValue = "";
    public String customPostKey = "";
    public String customPostValue = "";

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

        if (instance.uploadProvider == null) {
            instance.uploadProvider = UploadProvider.DISABLED;
        }
        if (!instance.uploadChatNotification) {
            // If chat notification is disabled, copy must stay enabled.
            instance.uploadCopyToClipboard = true;
        }
        if (instance.customUploadMethod == null) {
            instance.customUploadMethod = UploadMethod.POST;
        }

        if (instance.imgurClientId == null) instance.imgurClientId = "";
        if (instance.imgurAccessToken == null) instance.imgurAccessToken = "";

        if (instance.s3Endpoint == null) instance.s3Endpoint = "";
        if (instance.s3Region == null) instance.s3Region = "";
        if (instance.s3Bucket == null) instance.s3Bucket = "";
        if (instance.s3AccessKey == null) instance.s3AccessKey = "";
        if (instance.s3SecretKey == null) instance.s3SecretKey = "";
        if (instance.s3PathPrefix == null || instance.s3PathPrefix.isBlank()) {
            instance.s3PathPrefix = "screenshots/";
        }

        if (instance.customUploadUrl == null) instance.customUploadUrl = "";
        if (instance.customCookieKey == null) instance.customCookieKey = "";
        if (instance.customCookieValue == null) instance.customCookieValue = "";
        if (instance.customHeaderKey == null) instance.customHeaderKey = "";
        if (instance.customHeaderValue == null) instance.customHeaderValue = "";
        if (instance.customPostKey == null) instance.customPostKey = "";
        if (instance.customPostValue == null) instance.customPostValue = "";
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(instance, w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
