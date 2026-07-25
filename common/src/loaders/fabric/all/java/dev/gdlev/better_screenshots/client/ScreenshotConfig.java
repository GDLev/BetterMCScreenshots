package dev.gdlev.better_screenshots.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.gdlev.better_screenshots.common.ScreenshotConfigData;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

public class ScreenshotConfig extends ScreenshotConfigData {
    private static ScreenshotConfig instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("better_screenshots.json");

    public static ScreenshotConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        boolean shouldSave = false;
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                instance = GSON.fromJson(r, ScreenshotConfig.class);
                if (instance == null) instance = new ScreenshotConfig();
            } catch (Exception e) {
                instance = new ScreenshotConfig();
            }
        } else {
            instance = new ScreenshotConfig();
            shouldSave = true;
        }

        // Migration / normalization
        if (instance.animationsMode == null) {
            instance.animationsMode = instance.animations ? AnimationsMode.ON : AnimationsMode.OFF;
            shouldSave = true;
        }
        instance.animations = instance.animationsMode == AnimationsMode.ON;

        if (instance.menuButtonPosition == null) {
            instance.menuButtonPosition = defaultMenuButtonPosition();
            shouldSave = true;
        }
        if (isMinecraft26_2()) {
            if (!instance.menuButtonPosition26_2DefaultMigrated) {
                if (instance.menuButtonPosition == MenuButtonPosition.BOTTOM_LEFT) {
                    instance.menuButtonPosition = MenuButtonPosition.CENTER;
                }
                instance.menuButtonPosition26_2DefaultMigrated = true;
                shouldSave = true;
            }
        } else if (instance.menuButtonPosition == MenuButtonPosition.CENTER) {
            instance.menuButtonPosition = MenuButtonPosition.BOTTOM_LEFT;
            shouldSave = true;
        }

        if (instance.uploadProvider == null) {
            instance.uploadProvider = UploadProvider.DISABLED;
            shouldSave = true;
        }
        if (!instance.uploadChatNotification) {
            // If chat notification is disabled, copy must stay enabled.
            instance.uploadCopyToClipboard = true;
        }
        if (instance.customUploadMethod == null) {
            instance.customUploadMethod = UploadMethod.POST;
        }
        if (instance.customUploadBodyType == null) {
            instance.customUploadBodyType = UploadBodyType.MULTIPART;
        }

        if (instance.imgurClientId == null) instance.imgurClientId = "";
        if (instance.imgurAccessToken == null) instance.imgurAccessToken = "";

        if (instance.immichBaseUrl == null) instance.immichBaseUrl = "";
        if (instance.immichApiKey == null) instance.immichApiKey = "";
        if (instance.immichDeviceId == null || instance.immichDeviceId.isBlank()) {
            instance.immichDeviceId = "better-mc-screenshots";
        }
        if (instance.immichAlbumId == null) instance.immichAlbumId = "";
        if (instance.immichAlbumName == null) instance.immichAlbumName = "";

        if (instance.s3Endpoint == null) instance.s3Endpoint = "";
        if (instance.s3Region == null) instance.s3Region = "";
        if (instance.s3Bucket == null) instance.s3Bucket = "";
        if (instance.s3AccessKey == null) instance.s3AccessKey = "";
        if (instance.s3SecretKey == null) instance.s3SecretKey = "";
        if (instance.s3PathPrefix == null || instance.s3PathPrefix.isBlank()) {
            instance.s3PathPrefix = "screenshots/";
        }

        if (instance.customUploadUrl == null) instance.customUploadUrl = "";
        if (instance.customFileField == null || instance.customFileField.isBlank()) {
            instance.customFileField = "file";
        }
        if (instance.customResponseUrlJsonPath == null) instance.customResponseUrlJsonPath = "";
        if (instance.customFallbackUrl == null) instance.customFallbackUrl = "";
        if (instance.customCookieKey == null) instance.customCookieKey = "";
        if (instance.customCookieValue == null) instance.customCookieValue = "";
        if (instance.customHeaderKey == null) instance.customHeaderKey = "";
        if (instance.customHeaderValue == null) instance.customHeaderValue = "";
        if (instance.customHeaders == null) instance.customHeaders = new java.util.ArrayList<>();
        if (!instance.customHeaderKey.isBlank() && instance.customHeaders.stream().noneMatch(entry ->
                instance.customHeaderKey.equals(entry.key))) {
            instance.customHeaders.add(new KeyValueEntry(instance.customHeaderKey, instance.customHeaderValue));
            shouldSave = true;
        }
        if (instance.customPostKey == null) instance.customPostKey = "";
        if (instance.customPostValue == null) instance.customPostValue = "";
        if (instance.customFormFields == null) instance.customFormFields = new java.util.ArrayList<>();
        if (!instance.customPostKey.isBlank() && instance.customFormFields.stream().noneMatch(entry ->
                instance.customPostKey.equals(entry.key))) {
            instance.customFormFields.add(new KeyValueEntry(instance.customPostKey, instance.customPostValue));
            shouldSave = true;
        }
        if (instance.externalUploaderName == null) instance.externalUploaderName = "";

        if (shouldSave) {
            save();
        }
    }

    private static MenuButtonPosition defaultMenuButtonPosition() {
        return isMinecraft26_2() ? MenuButtonPosition.CENTER : MenuButtonPosition.BOTTOM_LEFT;
    }

    private static boolean isMinecraft26_2() {
        return FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString().startsWith("26.2"))
                .orElse(false);
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(instance, w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
