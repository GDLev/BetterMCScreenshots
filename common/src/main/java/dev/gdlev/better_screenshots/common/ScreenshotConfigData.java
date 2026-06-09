package dev.gdlev.better_screenshots.common;

/**
 * Loader-independent screenshot configuration values.
 *
 * <p>Each platform owns loading and saving the file, while this class keeps
 * the persisted shape shared across all supported loaders and game versions.
 */
public class ScreenshotConfigData {
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
    // Kept for backwards compatibility with older config files.
    public boolean animations = true;
    public AnimationsMode animationsMode = AnimationsMode.ON;
    public ChatNotification chatNotification = ChatNotification.MODERN;
    public FlashMode flashMode = FlashMode.PREVIEW;
    public int previewDurationSeconds = 4;
    public MenuButtonPosition menuButtonPosition = MenuButtonPosition.BOTTOM_LEFT;
    public int screenshotsFirstRowTopMargin = 0;
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

    public boolean uiAnimationsEnabled() {
        return animationsMode == AnimationsMode.ON;
    }

    public boolean previewAnimationsEnabled() {
        return animationsMode != AnimationsMode.OFF;
    }
}
