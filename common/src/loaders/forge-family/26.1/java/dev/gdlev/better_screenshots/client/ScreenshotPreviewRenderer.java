package dev.gdlev.better_screenshots.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public class ScreenshotPreviewRenderer {

    private static DynamicTexture previewTexture;
    public static final Identifier PREVIEW_ID =
            Identifier.fromNamespaceAndPath("better_screenshots", "screenshot_preview");

    private static DynamicTexture fullscreenTexture;
    public static final Identifier FULLSCREEN_ID =
            Identifier.fromNamespaceAndPath("better_screenshots", "screenshot_fullscreen");

    public static DynamicTexture getFullscreenTexture() { return fullscreenTexture; }

    private static DynamicTexture backgroundTexture;
    public static final Identifier BACKGROUND_ID =
            Identifier.fromNamespaceAndPath("better_screenshots", "screenshot_background");
    public static DynamicTexture getBackgroundTexture() { return backgroundTexture; }

    public static void captureBackground(Runnable onReady) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> mc.execute(() -> {
            NativeImage finalImage = image;
            int guiW = mc.getWindow().getGuiScaledWidth();
            int guiH = mc.getWindow().getGuiScaledHeight();
            if (image.getWidth() != guiW || image.getHeight() != guiH) {
                NativeImage scaled = new NativeImage(guiW, guiH, false);
                int srcW = image.getWidth();
                int srcH = image.getHeight();

                // Sample source at destination pixel centers to avoid bias/offset
                // when GUI scale introduces non-integer framebuffer-to-GUI mapping.
                for (int y = 0; y < guiH; y++) {
                    int sy = Math.min((int) Math.floor(((y + 0.5f) * srcH) / guiH), srcH - 1);
                    for (int x = 0; x < guiW; x++) {
                        int sx = Math.min((int) Math.floor(((x + 0.5f) * srcW) / guiW), srcW - 1);
                        scaled.setPixel(x, y, image.getPixel(sx, sy));
                    }
                }
                image.close();
                finalImage = scaled;
            }
            if (backgroundTexture != null) backgroundTexture.close();
            backgroundTexture = new DynamicTexture(() -> "screenshot_background", finalImage);
            mc.getTextureManager().register(BACKGROUND_ID, backgroundTexture);
            if (onReady != null) onReady.run();
        }));
    }

    public static void setFullscreenTexture(NativeImage image) {
        Minecraft mc = Minecraft.getInstance();
        if (fullscreenTexture != null) fullscreenTexture.close();
        fullscreenTexture = new DynamicTexture(() -> "screenshot_fullscreen", image);
        mc.getTextureManager().register(FULLSCREEN_ID, fullscreenTexture);
    }

    private static long showUntil      = -1;
    private static long showFrom       = -1;
    private static long flashStart     = -1;
    private static long copyFlashStart = -1;
    private static long closeStart     = -1;

    private static final long  FLASH_DURATION_MS = 400;
    private static final long  COPY_FLASH_MS     = 350;
    private static final long  ENTER_DURATION_MS = 300;
    private static final long  EXIT_DURATION_MS  = 500;
    private static final long  CLOSE_DURATION_MS = 300;
    private static final long  BOUNCE_UP_MS      = 150;
    private static final float BOUNCE_HEIGHT     = 18f;
    private static final float EXIT_DROP         = 120f;

    private static final int BTN_W   = 8;
    private static final int BTN_H   = 10;
    private static final int BTN_GAP = 0;

    private static final Identifier ICON_SHOW    = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/show.png");
    private static final Identifier ICON_SHOW_H  = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/show_hover.png");
    private static final Identifier ICON_COPY    = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/copy.png");
    private static final Identifier ICON_COPY_H  = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/copy_hover.png");
    private static final Identifier ICON_UPLOAD  = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/upload.png");
    private static final Identifier ICON_UPLOAD_H= Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/upload_hover.png");
    private static final Identifier ICON_DELETE  = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/delete.png");
    private static final Identifier ICON_DELETE_H= Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/delete_hover.png");

    private static int       hoveredButton = -1;
    private static final int[] btnX        = new int[4];
    private static final int[] btnY        = new int[4];
    private static final long DOUBLE_CLICK_MS = 300L;
    private static int previewHitX = -100;
    private static int previewHitY = -100;
    private static int previewHitW = 0;
    private static int previewHitH = 0;
    private static long lastPreviewClickMs = -1L;

    private enum UploadState {
        HIDDEN, UPLOADING, SUCCESS, ERROR
    }

    private static UploadState uploadState = UploadState.HIDDEN;
    private static boolean uploadEnabledForCurrentPreview = false;
    private static float uploadProgressTarget = 0f;
    private static float uploadProgressDisplayed = 0f;
    private static long lastProgressFrameMs = -1L;
    private static String lastUploadUrl = "";
    private static String lastUploadError = "";

    public static DynamicTexture getPreviewTexture() { return previewTexture; }

    private static final java.util.concurrent.ConcurrentHashMap<String, java.io.File> pendingFiles
            = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, String> uploadedUrls
            = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile String currentPreviewId = null;
    private static volatile java.io.File currentPreviewFile = null;

    public static void registerFile(String id, java.io.File file) {
        pendingFiles.put(id, file);
        currentPreviewId = id;
        currentPreviewFile = file;
    }

    public static void registerUploadedUrl(String id, String url) {
        if (id == null || id.isBlank() || url == null || url.isBlank()) return;
        uploadedUrls.put(id, url);
    }

    public static void copyUploadedUrl(String id) {
        if (id == null || id.isBlank()) return;
        String url = uploadedUrls.get(id);
        if (url == null || url.isBlank()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(url);
            copyFlashStart = System.currentTimeMillis();
        }
    }

    public static void loadAndPreview(String id, ScreenshotFullscreenScreen screen) {
        java.io.File file = pendingFiles.get(id);
        if (file == null || !file.exists()) return;
        Minecraft mc = Minecraft.getInstance();
        Thread.ofVirtual().start(() -> {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
                mc.execute(() -> {
                    if (fullscreenTexture != null) fullscreenTexture.close();
                    fullscreenTexture = new DynamicTexture(() -> "screenshot_fullscreen", img);
                    mc.getTextureManager().register(FULLSCREEN_ID, fullscreenTexture);
                    screen.markLoaded();
                });
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    public static void copyFile(String id) {
        java.io.File file = pendingFiles.get(id);
        if (file == null || !file.exists()) return;
        copyFileToClipboard(file);
    }

    public static void copyFileToClipboard(java.io.File file) {
        if (file == null || !file.exists()) return;
        Minecraft mc = Minecraft.getInstance();
        Thread.ofVirtual().start(() -> {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
                mc.execute(() -> {
                    try {
                        java.io.File tmp = java.io.File.createTempFile("better_screenshots_", ".png");
                        tmp.deleteOnExit();
                        img.writeToFile(tmp.toPath());
                        copyPathToClipboard(tmp.getAbsolutePath());
                    } catch (Exception e) { e.printStackTrace(); }
                    img.close();
                });
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    public static int getCopyFlashAlpha() {
        if (copyFlashStart < 0) return 0;
        long ce = System.currentTimeMillis() - copyFlashStart;
        if (ce >= COPY_FLASH_MS) { copyFlashStart = -1; return 0; }
        return (int)((1f - (float) ce / COPY_FLASH_MS) * 110);
    }


    public static void setPreview(NativeImage image) {
        if (previewTexture != null) previewTexture.close();
        previewTexture = new DynamicTexture(() -> "screenshot_preview", image);
        Minecraft.getInstance().getTextureManager()
                .register(PREVIEW_ID, previewTexture);
        showFrom       = System.currentTimeMillis();
        showUntil      = showFrom + (ScreenshotConfig.get().previewDurationSeconds * 1000L);
        flashStart     = ScreenshotConfig.get().previewAnimationsEnabled() ? showFrom : -1;
        closeStart     = -1;
        copyFlashStart = -1;
        hoveredButton  = -1;
        lastPreviewClickMs = -1L;
        uploadState = UploadState.HIDDEN;
        uploadEnabledForCurrentPreview = false;
        uploadProgressTarget = 0f;
        uploadProgressDisplayed = 0f;
        lastProgressFrameMs = -1L;
        lastUploadUrl = "";
        lastUploadError = "";
        currentPreviewId = null;
        currentPreviewFile = null;
    }

    public static void prepareUploadIndicator(boolean enabled) {
        uploadEnabledForCurrentPreview = enabled;
        if (!enabled) {
            uploadState = UploadState.HIDDEN;
            uploadProgressTarget = 0f;
            uploadProgressDisplayed = 0f;
            lastProgressFrameMs = -1L;
            lastUploadUrl = "";
            lastUploadError = "";
            return;
        }
        uploadState = UploadState.HIDDEN;
        uploadProgressTarget = 0f;
        uploadProgressDisplayed = 0f;
        lastProgressFrameMs = -1L;
        lastUploadUrl = "";
        lastUploadError = "";
    }

    public static void beginUploadIndicator() {
        if (!uploadEnabledForCurrentPreview) return;
        uploadState = UploadState.UPLOADING;
        uploadProgressTarget = 0f;
        uploadProgressDisplayed = 0f;
        lastProgressFrameMs = -1L;
        lastUploadUrl = "";
        lastUploadError = "";
    }

    public static void updateUploadProgress(double progress) {
        if (!uploadEnabledForCurrentPreview) return;
        uploadState = UploadState.UPLOADING;
        uploadProgressTarget = Math.max(0f, Math.min(1f, (float) progress));
    }

    public static void markUploadSuccess(String url) {
        if (!uploadEnabledForCurrentPreview) return;
        uploadState = UploadState.SUCCESS;
        uploadProgressTarget = 1f;
        lastUploadUrl = url == null ? "" : url;
        lastUploadError = "";

        long now = System.currentTimeMillis();
        showUntil = Math.max(showUntil, now + 500L);
    }

    public static void markUploadError(String error) {
        if (!uploadEnabledForCurrentPreview) return;
        uploadState = UploadState.ERROR;
        uploadProgressTarget = 1f;
        lastUploadError = error == null ? "" : error;
        lastUploadUrl = "";

        long now = System.currentTimeMillis();
        showUntil = Math.max(showUntil, now + 500L);
    }

    public static void close() {
        if (closeStart == -1) closeStart = System.currentTimeMillis();
    }

    private static float easeOutCubic(float t) { return 1f - (float) Math.pow(1f - t, 3); }
    private static float easeInCubic(float t)  { return t * t * t; }
    private static float easeOutQuad(float t)  { return 1f - (1f - t) * (1f - t); }

    public static void render(GuiGraphicsExtractor context) {
        long now = System.currentTimeMillis();
        if (showFrom == -1) return;
        if (previewTexture == null || previewTexture.getPixels() == null) return;

        Minecraft mc = Minecraft.getInstance();
        ScreenshotConfig cfg = ScreenshotConfig.get();

        int screenW    = context.guiWidth();
        int screenH    = context.guiHeight();
        int baseWidth  = screenW / 4;
        int baseHeight = (baseWidth * previewTexture.getPixels().getHeight())
                / previewTexture.getPixels().getWidth();
        int margin     = 10;
        boolean showUploadBar = uploadEnabledForCurrentPreview;
        int uploadBarH = showUploadBar ? 2 : 0;
        int uploadBarGap = 0;
        int uploadBarYOffset = showUploadBar ? (uploadBarH + uploadBarGap) : 0;

        int baseX = switch (cfg.corner) {
            case BOTTOM_RIGHT, TOP_RIGHT -> screenW - baseWidth - margin;
            case BOTTOM_LEFT,  TOP_LEFT  -> margin;
        };
        int baseY = switch (cfg.corner) {
            case BOTTOM_RIGHT, BOTTOM_LEFT -> screenH - baseHeight - margin - uploadBarYOffset;
            case TOP_RIGHT,    TOP_LEFT    -> margin;
        };

        float alpha   = 1f;
        float scale   = 1f;
        float offsetY = 0f;
        float offsetX = 0f;

        long elapsed   = now - showFrom;
        if (uploadEnabledForCurrentPreview && uploadState == UploadState.UPLOADING && now >= showUntil) {
            // Keep preview visible while upload is still in progress.
            showUntil = now + 1000L;
        }
        long remaining = showUntil - now;

        if (closeStart != -1) {
            long ce = now - closeStart;
            if (!cfg.previewAnimationsEnabled() || ce > CLOSE_DURATION_MS) {
                showUntil = -1; showFrom = -1; closeStart = -1;
                flashStart = -1; copyFlashStart = -1;
                clearPreviewHitBounds();
                return;
            }
            float t    = easeInCubic((float) ce / CLOSE_DURATION_MS);
            float dir  = switch (cfg.corner) {
                case BOTTOM_RIGHT, TOP_RIGHT ->  1f;
                case BOTTOM_LEFT,  TOP_LEFT  -> -1f;
            };
            offsetX = dir * (baseWidth + margin + 10) * t;
            alpha   = 1f - easeOutQuad(t);

        } else if (remaining <= 0) {
            long exitElapsed = now - showUntil;
            if (exitElapsed > EXIT_DURATION_MS) {
                showUntil = -1; showFrom = -1; flashStart = -1;
                clearPreviewHitBounds();
                return;
            }
            if (cfg.previewAnimationsEnabled()) {
                float dropDir = switch (cfg.corner) {
                    case TOP_RIGHT,    TOP_LEFT     -> -1f;
                    case BOTTOM_RIGHT, BOTTOM_LEFT  ->  1f;
                };
                if (exitElapsed < BOUNCE_UP_MS) {
                    float t = (float) exitElapsed / BOUNCE_UP_MS;
                    offsetY = -dropDir * BOUNCE_HEIGHT * easeOutCubic(t);
                } else {
                    float t = (float)(exitElapsed - BOUNCE_UP_MS)
                            / (EXIT_DURATION_MS - BOUNCE_UP_MS);
                    offsetY = -dropDir * BOUNCE_HEIGHT + dropDir * EXIT_DROP * easeInCubic(t);
                    alpha   = Math.max(0f, 1f - t * 1.5f);
                }
            } else {
                showUntil = -1; showFrom = -1; flashStart = -1;
                clearPreviewHitBounds();
                return;
            }

        } else if (elapsed < ENTER_DURATION_MS && cfg.previewAnimationsEnabled()) {
            float t = easeOutCubic((float) elapsed / ENTER_DURATION_MS);
            scale = 1.15f - 0.15f * t;
            alpha = t;
        }

        int drawWidth  = (int)(baseWidth  * scale);
        int drawHeight = (int)(baseHeight * scale);

        int drawX = switch (cfg.corner) {
            case BOTTOM_RIGHT, TOP_RIGHT -> (int)(baseX + baseWidth  - drawWidth  + offsetX);
            case BOTTOM_LEFT,  TOP_LEFT  -> (int)(baseX + offsetX);
        };
        int drawY = switch (cfg.corner) {
            case BOTTOM_RIGHT, BOTTOM_LEFT -> (int)(baseY + baseHeight - drawHeight + offsetY);
            case TOP_RIGHT,    TOP_LEFT    -> (int)(baseY + offsetY);
        };

        int alphaInt = Math.max(0, Math.min(255, (int)(alpha * 255f)));
        previewHitX = drawX;
        previewHitY = drawY;
        previewHitW = drawWidth;
        previewHitH = drawHeight;

        // Frame
        context.fill(drawX - 1, drawY - 1,
                drawX + drawWidth + 1, drawY + drawHeight + 1,
                (alphaInt << 24));

        // Image
        context.blit(RenderPipelines.GUI_TEXTURED, PREVIEW_ID,
                drawX, drawY, 0f, 0f,
                drawWidth, drawHeight, drawWidth, drawHeight);

        // Overlay
        if (alphaInt < 255) {
            context.fill(drawX, drawY, drawX + drawWidth, drawY + drawHeight,
                    ((255 - alphaInt) << 24));
        }

        // ScreenShot animation
        if (cfg.previewAnimationsEnabled() && flashStart != -1) {
            long fe = now - flashStart;
            if (fe < FLASH_DURATION_MS) {
                int fa = (int)((1f - (float) fe / FLASH_DURATION_MS) * 255);
                if (cfg.flashMode == ScreenshotConfig.FlashMode.SCREEN) {
                    context.fill(0, 0, screenW, screenH, (fa << 24) | 0x00FFFFFF);
                } else {
                    context.fill(drawX, drawY, drawX + drawWidth, drawY + drawHeight,
                            (fa << 24) | 0x00FFFFFF);
                }
            } else {
                flashStart = -1;
            }
        }

        // Copy animation
        if (cfg.previewAnimationsEnabled() && copyFlashStart != -1) {
            long ce = now - copyFlashStart;
            if (ce < COPY_FLASH_MS) {
                int ca = (int)((1f - (float) ce / COPY_FLASH_MS) * 110);
                context.fill(drawX, drawY, drawX + drawWidth, drawY + drawHeight,
                        (ca << 24) | 0x004499FF);
            } else {
                copyFlashStart = -1;
            }
        }
        // Action buttons (optional, file-only config)
        if (!cfg.hideMiniPreviewActionButtons) {
            boolean showUploadButton = ScreenshotUploader.isUploaderEnabled() && !cfg.uploadAutoUpload;
            int visibleButtons = showUploadButton ? 4 : 3;
            int totalBtnsW = visibleButtons * BTN_W + Math.max(0, visibleButtons - 1) * BTN_GAP;
            int btnsStartX = drawX + drawWidth - totalBtnsW - 2;
            int btnsY      = drawY + 2;

            // Convert raw window-space mouse coords to current GUI-space coords.
            double mouseX = mc.mouseHandler.xpos() * context.guiWidth() / mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos() * context.guiHeight() / mc.getWindow().getScreenHeight();
            hoveredButton = -1;

            for (int i = 0; i < visibleButtons; i++) {
                btnX[i] = btnsStartX + i * (BTN_W + BTN_GAP);
                btnY[i] = btnsY;
                if (mouseX >= btnX[i] && mouseX <= btnX[i] + BTN_W
                        && mouseY >= btnY[i] && mouseY <= btnY[i] + BTN_H) {
                    hoveredButton = i;
                }
            }
            for (int i = visibleButtons; i < btnX.length; i++) {
                btnX[i] = -100;
                btnY[i] = -100;
            }

            Identifier[] icons = showUploadButton
                    ? new Identifier[] {
                            hoveredButton == 0 ? ICON_SHOW_H   : ICON_SHOW,
                            hoveredButton == 1 ? ICON_COPY_H   : ICON_COPY,
                            hoveredButton == 2 ? ICON_UPLOAD_H : ICON_UPLOAD,
                            hoveredButton == 3 ? ICON_DELETE_H : ICON_DELETE
                    }
                    : new Identifier[] {
                            hoveredButton == 0 ? ICON_SHOW_H  : ICON_SHOW,
                            hoveredButton == 1 ? ICON_COPY_H  : ICON_COPY,
                            hoveredButton == 2 ? ICON_DELETE_H: ICON_DELETE
                    };
            for (int i = 0; i < visibleButtons; i++) {
                context.blit(RenderPipelines.GUI_TEXTURED, icons[i],
                        btnX[i], btnY[i], 0f, 0f,
                        BTN_W, BTN_H, BTN_W, BTN_H);
            }
        } else {
            hoveredButton = -1;
            for (int i = 0; i < btnX.length; i++) {
                btnX[i] = -100;
                btnY[i] = -100;
            }
        }

        if (showUploadBar) {
            if (lastProgressFrameMs < 0) {
                lastProgressFrameMs = now;
            }
            float dt = Math.max(0f, Math.min(100f, now - lastProgressFrameMs));
            lastProgressFrameMs = now;
            // Exponential smoothing for visually fluid progress transitions.
            float smoothing = 1f - (float) Math.exp(-dt / 120f);
            uploadProgressDisplayed += (uploadProgressTarget - uploadProgressDisplayed) * smoothing;
            if (Math.abs(uploadProgressTarget - uploadProgressDisplayed) < 0.001f) {
                uploadProgressDisplayed = uploadProgressTarget;
            }

            int barX = drawX;
            int barY = drawY + drawHeight + uploadBarGap;
            int barW = drawWidth;
            int barFillW;
            int barColor;

            switch (uploadState) {
                case SUCCESS -> {
                    barColor = 0xFF34C759;
                    barFillW = barW;
                }
                case ERROR -> {
                    barColor = 0xFFE74C3C;
                    barFillW = barW;
                }
                case UPLOADING -> {
                    barColor = 0xFF42B9FF;
                    float p = Math.max(0.04f, Math.min(1f, uploadProgressDisplayed));
                    barFillW = Math.max(1, (int) (barW * p));
                }
                default -> {
                    barColor = 0x6642B9FF;
                    barFillW = 0;
                }
            }

            context.fill(barX, barY, barX + barW, barY + uploadBarH, 0x66000000);
            if (barFillW > 0) {
                context.fill(barX, barY, barX + barFillW, barY + uploadBarH, barColor);
            }
        }
    }

    public static boolean handleClick(double mouseX, double mouseY) {
        if (showFrom == -1) return false;
        if (closeStart != -1) return false;
        if (showUntil != -1 && System.currentTimeMillis() > showUntil) return false;

        ScreenshotConfig cfg = ScreenshotConfig.get();
        if (!cfg.hideMiniPreviewActionButtons) {
            boolean showUploadButton = ScreenshotUploader.isUploaderEnabled() && !cfg.uploadAutoUpload;
            int visibleButtons = showUploadButton ? 4 : 3;

            for (int i = 0; i < visibleButtons; i++) {
                if (btnX[i] == 0 && btnY[i] == 0) continue;
                if (mouseX >= btnX[i] && mouseX <= btnX[i] + BTN_W
                        && mouseY >= btnY[i] && mouseY <= btnY[i] + BTN_H) {
                    playActionButtonClickSound();
                    if (showUploadButton) {
                        switch (i) {
                            case 0 -> openFullscreen();
                            case 1 -> copyToClipboard();
                            case 2 -> uploadCurrentPreview();
                            case 3 -> deleteCurrentPreview();
                        }
                    } else {
                        switch (i) {
                            case 0 -> openFullscreen();
                            case 1 -> copyToClipboard();
                            case 2 -> deleteCurrentPreview();
                        }
                    }
                    return true;
                }
            }
        }

        if (isInsidePreview(mouseX, mouseY)) {
            long now = System.currentTimeMillis();
            if (lastPreviewClickMs > 0 && now - lastPreviewClickMs <= DOUBLE_CLICK_MS) {
                lastPreviewClickMs = -1L;
                playActionButtonClickSound();
                openFullscreen();
                return true;
            }
            lastPreviewClickMs = now;
            return true;
        }
        return false;
    }

    private static boolean isInsidePreview(double mouseX, double mouseY) {
        return previewHitW > 0 && previewHitH > 0
                && mouseX >= previewHitX && mouseX <= previewHitX + previewHitW
                && mouseY >= previewHitY && mouseY <= previewHitY + previewHitH;
    }

    private static void clearPreviewHitBounds() {
        previewHitX = -100;
        previewHitY = -100;
        previewHitW = 0;
        previewHitH = 0;
        lastPreviewClickMs = -1L;
    }

    private static void playActionButtonClickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 2.0f));
    }

    private static void copyToClipboard() {
        if (previewTexture == null || previewTexture.getPixels() == null) return;
        try {
            NativeImage img = previewTexture.getPixels();
            java.io.File tmp = java.io.File.createTempFile("better_screenshots_", ".png");
            tmp.deleteOnExit();
            img.writeToFile(tmp.toPath());
            copyPathToClipboard(tmp.getAbsolutePath());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void copyPathToClipboard(String path) {
        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{
                        "osascript", "-e",
                        "set the clipboard to (read (POSIX file \""
                                + path + "\") as «class PNGf»)"
                });
            } else if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"powershell", "-command",
                        "Add-Type -Assembly 'System.Windows.Forms';" +
                                "Add-Type -Assembly 'System.Drawing';" +
                                "[System.Windows.Forms.Clipboard]::SetImage(" +
                                "[System.Drawing.Image]::FromFile('" +
                                path.replace("'", "''") + "'))"});
            } else {
                try {
                    Runtime.getRuntime().exec(new String[]{
                            "xclip", "-selection", "clipboard",
                            "-t", "image/png", "-i", path}).waitFor();
                } catch (Exception e) {
                    Runtime.getRuntime().exec(new String[]{
                            "xsel", "--clipboard", "--input", path});
                }
            }
            copyFlashStart = System.currentTimeMillis();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void openFullscreen() {
        Minecraft mc = Minecraft.getInstance();
        if (previewTexture != null) {
            mc.execute(() -> {
                ScreenshotFullscreenScreen screen =
                        new ScreenshotFullscreenScreen(mc.screen);
                screen.useCurrentTexture();
                screen.setFromHud(true);

                // Find the most-recently-modified file in screenshots dir as current
                java.io.File dir = new java.io.File(mc.gameDirectory, "screenshots");
                java.io.File[] found = dir.listFiles(
                        f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));
                java.io.File currentFile = null;
                if (found != null && found.length > 0) {
                    java.util.Arrays.sort(found,
                            java.util.Comparator.comparingLong(java.io.File::lastModified).reversed());
                    currentFile = found[0];
                }
                screen.initNavigationFromScreenshotsDir(currentFile);

                captureBackground(() -> mc.setScreen(screen));
            });
        }
    }

    private static void uploadCurrentPreview() {
        if (!ScreenshotUploader.isUploaderEnabled()) return;
        String id = currentPreviewId == null || currentPreviewId.isBlank()
                ? String.valueOf(System.nanoTime())
                : currentPreviewId;
        java.io.File file = currentPreviewFile;
        if (file == null || !file.exists()) {
            java.io.File dir = new java.io.File(Minecraft.getInstance().gameDirectory, "screenshots");
            java.io.File[] found = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));
            if (found != null && found.length > 0) {
                java.util.Arrays.sort(found, java.util.Comparator.comparingLong(java.io.File::lastModified).reversed());
                file = found[0];
            }
        }
        ScreenshotUploader.uploadWithClientFeedback(file, id, true);
    }

    private static void deleteCurrentPreview() {
        java.io.File file = currentPreviewFile;
        if (file == null || !file.exists()) {
            java.io.File dir = new java.io.File(Minecraft.getInstance().gameDirectory, "screenshots");
            java.io.File[] found = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));
            if (found != null && found.length > 0) {
                java.util.Arrays.sort(found, java.util.Comparator.comparingLong(java.io.File::lastModified).reversed());
                file = found[0];
            }
        }
        if (file != null && file.exists() && file.delete()) {
            if (currentPreviewId != null && !currentPreviewId.isBlank()) {
                pendingFiles.remove(currentPreviewId);
                uploadedUrls.remove(currentPreviewId);
            }
            currentPreviewFile = null;
            currentPreviewId = null;
            close();
        }
    }
}
