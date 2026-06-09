package dev.gdlev.better_screenshots.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.gdlev.better_screenshots.common.ScreenshotConfigData;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.*;

public class ScreenshotConfig extends ScreenshotConfigData {
    private static ScreenshotConfig instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("better_screenshots.json");

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
