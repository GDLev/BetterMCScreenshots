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
        CENTER, TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT, DISABLED
    }

    public enum UploadProvider {
        DISABLED, IMGUR, S3, CUSTOM_HTTP, CATBOX
    }

    public enum UploadMethod {
        POST, PUT
    }

    public enum ActionButtonCorner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    public enum PauseButtonAnchor {
        CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
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
    public boolean menuButtonPosition26_2DefaultMigrated = false;
    public int screenshotsFirstRowTopMargin = 0;
    public boolean renderTopBar = true;
    public boolean hideMiniPreviewActionButtons = false;
    public boolean actionButtonTooltips = true;
    public boolean pixelatedPreviews = false;

    public ActionButtonCorner miniPreviewShowCorner = ActionButtonCorner.TOP_RIGHT;
    public ActionButtonCorner miniPreviewCopyCorner = ActionButtonCorner.TOP_RIGHT;
    public ActionButtonCorner miniPreviewUploadCorner = ActionButtonCorner.TOP_RIGHT;
    public ActionButtonCorner miniPreviewDeleteCorner = ActionButtonCorner.TOP_RIGHT;
    public boolean miniPreviewShowVisible = true;
    public boolean miniPreviewCopyVisible = true;
    public boolean miniPreviewUploadVisible = true;
    public boolean miniPreviewDeleteVisible = true;
    public int miniPreviewShowOrder = 0;
    public int miniPreviewCopyOrder = 1;
    public int miniPreviewUploadOrder = 2;
    public int miniPreviewDeleteOrder = 3;

    public ActionButtonCorner galleryShowCorner = ActionButtonCorner.TOP_RIGHT;
    public ActionButtonCorner galleryCopyCorner = ActionButtonCorner.TOP_RIGHT;
    public ActionButtonCorner galleryUploadCorner = ActionButtonCorner.TOP_RIGHT;
    public ActionButtonCorner galleryDeleteCorner = ActionButtonCorner.TOP_RIGHT;
    public boolean galleryShowVisible = true;
    public boolean galleryCopyVisible = true;
    public boolean galleryUploadVisible = true;
    public boolean galleryDeleteVisible = true;
    public int galleryShowOrder = 0;
    public int galleryCopyOrder = 1;
    public int galleryUploadOrder = 2;
    public int galleryDeleteOrder = 3;

    public ActionButtonCorner configMenuShowCorner = ActionButtonCorner.TOP_RIGHT;
    public ActionButtonCorner configMenuCopyCorner = ActionButtonCorner.TOP_RIGHT;
    public ActionButtonCorner configMenuUploadCorner = ActionButtonCorner.TOP_RIGHT;
    public ActionButtonCorner configMenuDeleteCorner = ActionButtonCorner.TOP_RIGHT;
    public boolean configMenuShowVisible = true;
    public boolean configMenuCopyVisible = true;
    public boolean configMenuUploadVisible = true;
    public boolean configMenuDeleteVisible = true;
    public int configMenuShowOrder = 0;
    public int configMenuCopyOrder = 1;
    public int configMenuUploadOrder = 2;
    public int configMenuDeleteOrder = 3;

    public ActionButtonCorner fullscreenCloseCorner = ActionButtonCorner.TOP_LEFT;
    public ActionButtonCorner fullscreenCopyCorner = ActionButtonCorner.TOP_RIGHT;
    public ActionButtonCorner fullscreenUploadCorner = ActionButtonCorner.TOP_RIGHT;
    public ActionButtonCorner fullscreenDeleteCorner = ActionButtonCorner.TOP_RIGHT;
    public boolean fullscreenCloseVisible = true;
    public boolean fullscreenCopyVisible = true;
    public boolean fullscreenUploadVisible = true;
    public boolean fullscreenDeleteVisible = true;
    public int fullscreenCloseOrder = 0;
    public int fullscreenCopyOrder = 1;
    public int fullscreenUploadOrder = 2;
    public int fullscreenDeleteOrder = 3;

    public boolean pauseButtonLayoutMigrated = false;
    public PauseButtonAnchor pauseSettingsAnchor = PauseButtonAnchor.CENTER;
    public PauseButtonAnchor pauseGalleryAnchor = PauseButtonAnchor.CENTER;
    public PauseButtonAnchor pauseScreenshotAnchor = PauseButtonAnchor.CENTER;
    public boolean pauseSettingsVisible = true;
    public boolean pauseGalleryVisible = true;
    public boolean pauseScreenshotVisible = true;
    public int pauseSettingsOrder = 0;
    public int pauseGalleryOrder = 1;
    public int pauseScreenshotOrder = 2;

    public boolean pause26_1ButtonLayoutMigrated = false;
    public ActionButtonCorner pause26_1SettingsCorner = ActionButtonCorner.BOTTOM_LEFT;
    public ActionButtonCorner pause26_1GalleryCorner = ActionButtonCorner.BOTTOM_LEFT;
    public ActionButtonCorner pause26_1ScreenshotCorner = ActionButtonCorner.BOTTOM_LEFT;
    public boolean pause26_1SettingsVisible = true;
    public boolean pause26_1GalleryVisible = true;
    public boolean pause26_1ScreenshotVisible = true;
    public int pause26_1SettingsOrder = 0;
    public int pause26_1GalleryOrder = 1;
    public int pause26_1ScreenshotOrder = 2;

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
