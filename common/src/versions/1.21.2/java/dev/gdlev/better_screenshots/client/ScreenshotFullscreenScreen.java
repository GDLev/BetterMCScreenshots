package dev.gdlev.better_screenshots.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ScreenshotFullscreenScreen extends Screen {

    private final Screen parent;

    private static final long  ENTER_DURATION_MS  = 350;
    private static final long  EXIT_DURATION_MS   = 500;
    private static final long  BOUNCE_UP_MS       = 120;
    private static final long  BLUR_FADE_MS       = 180;
    private static final float BOUNCE_HEIGHT      = 18f;
    private static final float EXIT_DROP          = 300f;
    private static final int   MARGIN             = 32;

    // Navigation slide transition
    private static final long  NAV_ANIM_MS        = 300;
    private static final float NAV_SLIDE_FRAC     = 0.35f; // fraction of screen width

    // Arrow fade
    private static final long  ARROW_FADE_MS      = 250;
    private static final int   ARROW_W            = 20;
    private static final int   ARROW_H            = 36;
    private static final int   ARROW_MARGIN       = 12;

    // Action buttons
    private static final ResourceLocation ICON_COPY     = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/copy.png");
    private static final ResourceLocation ICON_COPY_H   = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/copy_hover.png");
    private static final ResourceLocation ICON_UPLOAD   = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/upload.png");
    private static final ResourceLocation ICON_UPLOAD_H = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/upload_hover.png");
    private static final ResourceLocation ICON_DELETE   = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/delete.png");
    private static final ResourceLocation ICON_DELETE_H = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/delete_hover.png");
    private static final ResourceLocation ICON_CLOSE    = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/close.png");
    private static final ResourceLocation ICON_CLOSE_H  = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/close_hover.png");

    private static final int ACT_BTN_W   = 12;
    private static final int ACT_BTN_H   = 15;
    private static final int ACT_BTN_GAP = 0; // Brak przerwy
    private static final long COPY_FLASH_MS = 500L;
    private static final int UPLOAD_BAR_H = 2;
    private static final long UPLOAD_STATE_HOLD_MS = 1400L;
    private static final int META_H = 14;
    private static final int EDIT_ICON_W = 10;
    private static final int NAME_MAX_LENGTH = 80;
    private final int[] actionBtnX = new int[4];
    private final int[] actionBtnY = new int[4];

    // ── Screen state ──────────────────────────────────────────────────────────

    private long    openedAt     = -1;
    private long    loadingStart = System.currentTimeMillis();
    private long    closeStart   = -1;
    private boolean loaded       = false;
    private boolean closing      = false;

    private int startX, startY, startW, startH;
    private DynamicTexture expectedTexture = null;
    private boolean fromHud          = false;
    private boolean useFullscreenTex = false;

    private int lastCurX, lastCurY, lastCurW, lastCurH;

    // ── Navigation state ──────────────────────────────────────────────────────

    private List<File> screenshotFiles  = new ArrayList<>();
    private int        currentFileIndex = -1;
    private boolean    navLoading       = false;

    // Slide transition bookkeeping
    private int            navDirection = 0;   // +1 = right/older, -1 = left/newer
    private long           navAnimStart = -1;
    private ResourceLocation     navOldTexId  = null;
    private DynamicTexture navOldTex    = null;
    private File           navOldFile   = null;
    private int            navOldWidth  = 0;
    private int            navOldHeight = 0;

    // Arrow fade-in start timestamp
    private long imageFullyShownAt = -1;

    // Set by navigation to suppress the slide-up entrance animation
    private boolean skipEntranceAnim = false;

    private enum FullscreenUploadState {
        HIDDEN, UPLOADING, SUCCESS, ERROR
    }

    private FullscreenUploadState uploadState = FullscreenUploadState.HIDDEN;
    private float uploadProgressTarget = 0f;
    private float uploadProgressDisplayed = 0f;
    private long uploadLastFrameMs = -1L;
    private long uploadClearAtMs = 0L;
    private float copyFlashAlpha = 0f;
    private long copyFlashLastFrameMs = -1L;
    private EditBox nameEditBox;
    private boolean editingName = false;
    private int lastMetaX = 0;
    private int lastMetaY = 0;
    private int lastMetaW = 0;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ScreenshotFullscreenScreen(Screen parent) {
        super(Component.literal(""));
        this.parent = parent;
    }

    // ── Navigation context ────────────────────────────────────────────────────

    public void setNavigationContext(List<File> files, int currentIndex) {
        this.screenshotFiles  = new ArrayList<>(files);
        this.currentFileIndex = currentIndex;
    }

    public void initNavigationFromScreenshotsDir(File currentFile) {
        Minecraft mc    = Minecraft.getInstance();
        File      dir   = new File(mc.gameDirectory, "screenshots");
        File[]    found = dir.listFiles(
                f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));
        if (found == null) return;
        Arrays.sort(found, Comparator.comparingLong(File::lastModified).reversed());
        screenshotFiles  = new ArrayList<>(Arrays.asList(found));
        currentFileIndex = 0;
        if (currentFile != null) {
            for (int i = 0; i < screenshotFiles.size(); i++) {
                if (screenshotFiles.get(i).getAbsolutePath()
                        .equals(currentFile.getAbsolutePath())) {
                    currentFileIndex = i;
                    break;
                }
            }
        }
    }

    // ── Texture helpers ───────────────────────────────────────────────────────

    public void markLoaded() {
        loaded           = true;
        useFullscreenTex = true;
        expectedTexture  = ScreenshotPreviewRenderer.getFullscreenTexture();
    }

    public void useCurrentTexture() {
        loaded          = true;
        expectedTexture = ScreenshotPreviewRenderer.getPreviewTexture();
    }

    private ResourceLocation getTexId() {
        return useFullscreenTex
                ? ScreenshotPreviewRenderer.FULLSCREEN_ID
                : ScreenshotPreviewRenderer.PREVIEW_ID;
    }

    // ── Init / lifecycle ──────────────────────────────────────────────────────

    @Override
    protected void init() {
        loadingStart      = System.currentTimeMillis();
        openedAt          = -1;
        closing           = false;
        closeStart        = -1;
        imageFullyShownAt = -1;
        navAnimStart      = -1;
        navOldTexId       = null;
        navOldTex         = null;
        navOldFile        = null;
        navOldWidth       = 0;
        navOldHeight      = 0;
        skipEntranceAnim  = false;
        resetUploadOverlay();
        copyFlashAlpha    = 0f;
        copyFlashLastFrameMs = -1L;
        cancelNameEdit();
        initNameEditBox();
        if (loaded && expectedTexture != null) initStartPosition();
    }

    public void setFromHud(boolean value) { this.fromHud = value; }

    private void initStartPosition() {
        var img = expectedTexture;
        if (img == null || img.getPixels() == null) return;

        int   targetW = this.width  - MARGIN * 2;
        int   targetH = this.height - MARGIN * 2 - fullscreenNameBarHeight();
        float scale   = Math.min(
                (float) targetW / img.getPixels().getWidth(),
                (float) targetH / img.getPixels().getHeight());
        targetW = (int)(img.getPixels().getWidth()  * scale);
        targetH = (int)(img.getPixels().getHeight() * scale);

        if (fromHud) {
            ScreenshotConfig cfg = ScreenshotConfig.get();
            int previewW = this.width / 4;
            int previewH = (previewW * img.getPixels().getHeight()) / img.getPixels().getWidth();
            int m = 10;
            startX = switch (cfg.corner) {
                case BOTTOM_RIGHT, TOP_RIGHT -> this.width  - previewW - m;
                case BOTTOM_LEFT,  TOP_LEFT  -> m;
            };
            startY = switch (cfg.corner) {
                case BOTTOM_RIGHT, BOTTOM_LEFT -> this.height - previewH - m;
                case TOP_RIGHT,    TOP_LEFT    -> m;
            };
            startW = previewW;
            startH = previewH;
        } else {
            startX = (this.width  - targetW) / 2;
            startY = this.height;
            startW = targetW;
            startH = targetH;
        }
    }

    // ── Easing ────────────────────────────────────────────────────────────────

    private float easeOutCubic(float t)   { return 1f - (float) Math.pow(1f - t, 3); }
    private float easeInCubic(float t)    { return t * t * t; }
    private float easeInOutCubic(float t) {
        return t < 0.5f
                ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    // ── Close ─────────────────────────────────────────────────────────────────

    private void startClose() {
        if (closing) return;
        cancelNameEdit();
        closing    = true;
        closeStart = System.currentTimeMillis();
    }

    private void closeLikeEscape() {
        if (!loaded || expectedTexture == null || expectedTexture.getPixels() == null) {
            this.minecraft.setScreen(parent);
        } else if (!ScreenshotConfig.get().uiAnimationsEnabled()) {
            this.minecraft.setScreen(parent);
        } else {
            startClose();
        }
    }

    // ── Navigation & File Management ──────────────────────────────────────────

    private boolean hasPrev() { return currentFileIndex > 0; }
    private boolean hasNext() {
        return currentFileIndex >= 0 && currentFileIndex < screenshotFiles.size() - 1;
    }

    private void navigateTo(int direction) {
        if (navLoading) return;
        cancelNameEdit();
        int newIndex = currentFileIndex + direction;
        if (newIndex < 0 || newIndex >= screenshotFiles.size()) return;

        boolean useAnim = ScreenshotConfig.get().uiAnimationsEnabled();
        if (useAnim && expectedTexture != null && expectedTexture.getPixels() != null) {
            NativeImage currentPixels = expectedTexture.getPixels();
            NativeImage snapshot = new NativeImage(currentPixels.getWidth(), currentPixels.getHeight(), false);
            snapshot.copyFrom(currentPixels);
            ScreenshotPreviewRenderer.setOldFullscreenTexture(snapshot);

            navOldTexId  = ScreenshotPreviewRenderer.OLD_FULLSCREEN_ID;
            navOldTex    = ScreenshotPreviewRenderer.getOldFullscreenTexture();
            navOldFile   = currentFile();
            navOldWidth  = navOldTex.getPixels().getWidth();
            navOldHeight = navOldTex.getPixels().getHeight();
            navDirection = direction;
            navAnimStart = System.currentTimeMillis();
        }

        imageFullyShownAt = -1;
        skipEntranceAnim  = true;
        navLoading        = true;
        loaded            = false;
        expectedTexture   = null;
        openedAt          = -1;

        File      file = screenshotFiles.get(newIndex);
        Minecraft mc   = Minecraft.getInstance();

        Thread.ofVirtual().start(() -> {
            try {
                byte[]      bytes = Files.readAllBytes(file.toPath());
                NativeImage img   = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
                mc.execute(() -> {
                    ScreenshotPreviewRenderer.setFullscreenTexture(img);
                    useFullscreenTex = true;
                    expectedTexture  = ScreenshotPreviewRenderer.getFullscreenTexture();
                    loaded           = true;
                    navLoading       = false;
                    currentFileIndex = newIndex;
                    resetUploadOverlay();
                });
            } catch (Exception e) {
                e.printStackTrace();
                mc.execute(() -> { navLoading = false; navAnimStart = -1; });
            }
        });
    }

    private void deleteCurrent() {
        cancelNameEdit();
        if (screenshotFiles.isEmpty() || currentFileIndex < 0) return;
        File file = screenshotFiles.get(currentFileIndex);
        if (file.delete()) {
            if (parent instanceof ScreenshotGalleryScreen gallery) {
                gallery.refreshAfterExternalChange();
            } else if (parent instanceof ScreenshotConfigScreen config) {
                config.refreshAfterExternalChange();
            }
            screenshotFiles.remove(currentFileIndex);
            if (screenshotFiles.isEmpty()) {
                this.minecraft.setScreen(parent);
            } else {
                if (currentFileIndex >= screenshotFiles.size()) {
                    currentFileIndex = screenshotFiles.size() - 1;
                }
                reloadCurrentIndex();
            }
        }
    }

    private void reloadCurrentIndex() {
        navLoading       = true;
        loaded           = false;
        expectedTexture  = null;
        imageFullyShownAt= -1;
        openedAt         = System.currentTimeMillis();
        skipEntranceAnim = true;

        File file    = screenshotFiles.get(currentFileIndex);
        Minecraft mc = Minecraft.getInstance();

        Thread.ofVirtual().start(() -> {
            try {
                byte[] bytes    = Files.readAllBytes(file.toPath());
                NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
                mc.execute(() -> {
                    ScreenshotPreviewRenderer.setFullscreenTexture(img);
                    useFullscreenTex  = true;
                    expectedTexture   = ScreenshotPreviewRenderer.getFullscreenTexture();
                    loaded            = true;
                    navLoading        = false;
                    imageFullyShownAt = System.currentTimeMillis();
                    resetUploadOverlay();
                });
            } catch (Exception e) {
                e.printStackTrace();
                mc.execute(() -> { navLoading = false; });
            }
        });
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private boolean fullscreenNameBarSupported() {
        if (!ScreenshotConfig.get().fullscreenNameBar) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(ScreenshotPreviewRenderer.class
                    .getMethod("supportsFullscreenRename")
                    .invoke(null));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private int fullscreenNameBarHeight() {
        return fullscreenNameBarHeight(currentFile());
    }

    private int fullscreenNameBarHeight(File file) {
        return fullscreenNameBarSupported() && file != null ? META_H : 0;
    }

    private void initNameEditBox() {
        if (!fullscreenNameBarSupported()) return;
        nameEditBox = new EditBox(font, 0, 0, 1, META_H - 2,
                Component.translatable("better_screenshots.gallery.name"));
        nameEditBox.setMaxLength(NAME_MAX_LENGTH);
        nameEditBox.setBordered(false);
        nameEditBox.setTextColor(0xFFE0E0E0);
        nameEditBox.setSuggestion("...");
        nameEditBox.setVisible(false);
        addRenderableWidget(nameEditBox);
    }

    private File currentFile() {
        return currentFileIndex >= 0 && currentFileIndex < screenshotFiles.size()
                ? screenshotFiles.get(currentFileIndex)
                : null;
    }

    private void drawNameBar(
            GuiGraphics context,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY) {
        drawNameBar(context, x, y, width, mouseX, mouseY, 255, true);
    }

    private void drawNameBar(
            GuiGraphics context,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY,
            int alpha,
            boolean interactive) {
        drawNameBar(context, currentFile(), x, y, width, mouseX, mouseY, alpha, interactive);
    }

    private void drawNameBar(
            GuiGraphics context,
            File file,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY,
            int alpha,
            boolean interactive) {
        if (!fullscreenNameBarSupported()) {
            if (nameEditBox != null) nameEditBox.setVisible(false);
            return;
        }
        if (file == null) {
            if (nameEditBox != null) nameEditBox.setVisible(false);
            return;
        }

        if (interactive) {
            lastMetaX = x;
            lastMetaY = y;
            lastMetaW = width;
        }

        alpha = Math.max(0, Math.min(255, alpha));
        if (alpha <= 3) {
            return;
        }
        int clipLeft = Math.max(0, x);
        int clipRight = Math.min(this.width, x + width);
        if (clipRight <= clipLeft) {
            return;
        }
        context.enableScissor(clipLeft, y, clipRight, y + META_H);
        context.fill(x, y, x + width, y + META_H, withAlpha(0x3A3A3A, alpha));
        context.fill(x, y, x + width, y + 1, withAlpha(0x505050, alpha));

        if (interactive) {
            updateNameEditBoxBounds();
        } else if (nameEditBox != null) {
            nameEditBox.setVisible(false);
        }
        if (!editingName) {
            String customName = displayName(file);
            String label = customName != null ? customName : formatScreenshotTime(file.lastModified());
            int labelW = width - (interactive ? EDIT_ICON_W : 0) - 10;
            if (font.width(label) > labelW) {
                String clipped = label;
                while (font.width(clipped + "...") > labelW && !clipped.isEmpty()) {
                    clipped = clipped.substring(0, clipped.length() - 1);
                }
                label = clipped + "...";
            }
            context.drawString(font, Component.literal(label), x + 4, y + 4, withAlpha(0xE0E0E0, alpha));
        }

        if (interactive) {
            int iconColor = isEditIconHit(mouseX, mouseY) ? 0xFFFFFF : 0xBDBDBD;
            iconColor = withAlpha(iconColor, alpha);
            context.drawString(font, Component.literal("✎"), x + width - EDIT_ICON_W - 1, y + 3, iconColor);
        }
        context.disableScissor();
    }

    private int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0x00FFFFFF);
    }

    private void drawFullscreenFrame(
            GuiGraphics context,
            int x,
            int y,
            int width,
            int imageHeight,
            File file,
            int alpha) {
        int totalHeight = imageHeight + fullscreenNameBarHeight(file);
        int left = x - 1;
        int top = y - 1;
        int right = x + width + 1;
        int bottom = y + totalHeight + 1;
        if (right <= left || bottom <= top) return;

        int color = withAlpha(0x555555, alpha);
        context.fill(left, top, right, top + 1, color);
        context.fill(left, bottom - 1, right, bottom, color);
        context.fill(left, top + 1, left + 1, bottom - 1, color);
        context.fill(right - 1, top + 1, right, bottom - 1, color);
    }

    private void startNameEdit() {
        if (!fullscreenNameBarSupported() || currentFile() == null) return;
        editingName = true;
        if (nameEditBox == null) initNameEditBox();
        if (nameEditBox == null) return;
        nameEditBox.setValue("");
        nameEditBox.setFocused(true);
        nameEditBox.setCanLoseFocus(false);
        nameEditBox.moveCursorToEnd(false);
        this.setFocused(nameEditBox);
        updateNameEditBoxBounds();
    }

    private void acceptNameEdit() {
        if (!editingName || nameEditBox == null) {
            cancelNameEdit();
            return;
        }
        String value = nameEditBox.getValue().trim();
        if (!value.isEmpty()) {
            renameCurrentScreenshot(value);
        }
        cancelNameEdit();
    }

    private void cancelNameEdit() {
        editingName = false;
        if (nameEditBox != null) {
            nameEditBox.setValue("");
            nameEditBox.setFocused(false);
            nameEditBox.setCanLoseFocus(true);
            nameEditBox.setVisible(false);
        }
        if (this.getFocused() == nameEditBox) {
            this.setFocused(null);
        }
    }

    private void renameCurrentScreenshot(String requestedName) {
        File source = currentFile();
        if (source == null) return;
        File parentDir = source.getParentFile();
        if (parentDir == null) return;

        String baseName = sanitizeScreenshotName(requestedName);
        if (baseName.isEmpty()) return;

        File target = uniqueScreenshotFile(parentDir, baseName, source);
        if (source.equals(target)) return;

        try {
            Files.move(source.toPath(), target.toPath());
        } catch (Exception ignored) {
            return;
        }

        screenshotFiles.set(currentFileIndex, target);
        if (parent instanceof ScreenshotGalleryScreen gallery) {
            gallery.refreshAfterExternalChange();
        } else if (parent instanceof ScreenshotConfigScreen config) {
            config.refreshAfterExternalChange();
        }
    }

    private String sanitizeScreenshotName(String name) {
        String sanitized = name.trim()
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", " ");
        while (sanitized.endsWith(".") || sanitized.endsWith(" ")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        if (sanitized.toLowerCase(Locale.ROOT).endsWith(".png")) {
            sanitized = sanitized.substring(0, sanitized.length() - 4).trim();
        }
        if (sanitized.length() > NAME_MAX_LENGTH) {
            sanitized = sanitized.substring(0, NAME_MAX_LENGTH).trim();
        }
        return sanitized;
    }

    private File uniqueScreenshotFile(File dir, String baseName, File source) {
        File target = new File(dir, baseName + ".png");
        if (target.equals(source) || !target.exists()) {
            return target;
        }

        for (int i = 2; i < 1000; i++) {
            target = new File(dir, baseName + " (" + i + ").png");
            if (target.equals(source) || !target.exists()) {
                return target;
            }
        }
        return new File(dir, baseName + " (" + System.currentTimeMillis() + ").png");
    }

    private String displayName(File file) {
        String fileName = file.getName();
        if (isDefaultScreenshotName(fileName)) {
            return null;
        }
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".png")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    private boolean isDefaultScreenshotName(String fileName) {
        return fileName.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}(?:_\\d+)?\\.png");
    }

    private static String formatScreenshotTime(long lastModified) {
        ZoneId zone = ZoneId.systemDefault();
        Instant instant = Instant.ofEpochMilli(lastModified);
        LocalDate shotDate = instant.atZone(zone).toLocalDate();
        LocalDate today = LocalDate.now(zone);
        String time = DateTimeFormatter.ofPattern("HH:mm").format(instant.atZone(zone));

        if (shotDate.equals(today)) {
            return Component.translatable("better_screenshots.gallery.time.today", time).getString();
        }
        if (shotDate.equals(today.minusDays(1))) {
            return Component.translatable("better_screenshots.gallery.time.yesterday", time).getString();
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(instant.atZone(zone));
    }

    private boolean updateNameEditBoxBounds() {
        if (!editingName || nameEditBox == null || lastMetaW <= 0) {
            if (nameEditBox != null) nameEditBox.setVisible(false);
            return false;
        }
        nameEditBox.setVisible(true);
        nameEditBox.setX(lastMetaX + 4);
        nameEditBox.setY(lastMetaY + 3);
        nameEditBox.setWidth(Math.max(1, lastMetaW - EDIT_ICON_W - 8));
        nameEditBox.setHeight(META_H - 4);
        return true;
    }

    private boolean isEditIconHit(double mouseX, double mouseY) {
        int iconX = lastMetaX + lastMetaW - EDIT_ICON_W - 3;
        return fullscreenNameBarSupported()
                && currentFile() != null
                && mouseX >= iconX
                && mouseX <= iconX + EDIT_ICON_W
                && mouseY >= lastMetaY + 1
                && mouseY <= lastMetaY + META_H - 1;
    }

    private void drawHintText(GuiGraphics context) {
        context.drawCenteredString(font,
                Component.translatable("better_screenshots.fullscreen.hint"),
                this.width / 2, this.height - 16, 0x66FFFFFF);
    }

    private float arrowAlpha() {
        if (imageFullyShownAt < 0) return 0f;
        boolean useAnim = ScreenshotConfig.get().uiAnimationsEnabled();
        if (!useAnim) return 1f;
        long elapsed = System.currentTimeMillis() - imageFullyShownAt;
        return Math.min(1f, (float) elapsed / ARROW_FADE_MS);
    }

    private void drawActionButtons(GuiGraphics context,
                                   int imgX, int imgY, int imgW, int imgH,
                                   double mouseX, double mouseY) {
        float alpha = arrowAlpha();
        if (alpha <= 0f) return;

        boolean showUploadAction = ScreenshotUploader.isUploaderEnabled();
        boolean[] visible = layoutActionButtons(imgX, imgY, imgW, imgH, showUploadAction);
        ResourceLocation[] icons = { ICON_CLOSE, ICON_COPY, ICON_UPLOAD, ICON_DELETE };
        ResourceLocation[] hoverIcons = { ICON_CLOSE_H, ICON_COPY_H, ICON_UPLOAD_H, ICON_DELETE_H };
        for (int i = 0; i < 4; i++) {
            if (!visible[i]) continue;
            boolean hovered = mouseX >= actionBtnX[i] && mouseX <= actionBtnX[i] + ACT_BTN_W
                    && mouseY >= actionBtnY[i] && mouseY <= actionBtnY[i] + ACT_BTN_H;
            context.blit(RenderType::guiTextured, hovered ? hoverIcons[i] : icons[i],
                    actionBtnX[i], actionBtnY[i], 0f, 0f,
                    ACT_BTN_W, ACT_BTN_H, ACT_BTN_W, ACT_BTN_H);
            if (hovered) {
                ActionButtonTooltips.draw(context, font, this.width, this.height, mouseX, mouseY, i, true);
            }
        }
    }

    private boolean[] layoutActionButtons(
            int x, int y, int w, int h, boolean showUploadAction) {
        boolean hasFileActions = !screenshotFiles.isEmpty() && currentFileIndex >= 0;
        ScreenshotConfig config = ScreenshotConfig.get();
        ScreenshotConfig.ActionButtonCorner[] corners = {
                config.fullscreenCloseCorner, config.fullscreenCopyCorner,
                config.fullscreenUploadCorner, config.fullscreenDeleteCorner
        };
        int[] order = {
                config.fullscreenCloseOrder, config.fullscreenCopyOrder,
                config.fullscreenUploadOrder, config.fullscreenDeleteOrder
        };
        boolean[] visible = {
                config.fullscreenCloseVisible,
                hasFileActions && config.fullscreenCopyVisible,
                hasFileActions && showUploadAction && config.fullscreenUploadVisible,
                hasFileActions && config.fullscreenDeleteVisible
        };
        ActionButtonLayout.arrange(
                actionBtnX, actionBtnY, corners, order, visible, 4,
                x, y, w, h, ACT_BTN_W, ACT_BTN_H, ACT_BTN_GAP, 8);
        return visible;
    }

    private void drawArrows(GuiGraphics context,
                            int imgX, int imgY, int imgW, int imgH,
                            double mouseX, double mouseY) {
        if (screenshotFiles.isEmpty() || currentFileIndex < 0) return;
        float alpha = arrowAlpha();
        if (alpha <= 0f) return;

        int arrowY = imgY + (imgH - ARROW_H) / 2;

        boolean prevExists = hasPrev();
        int     prevX      = imgX - ARROW_W - ARROW_MARGIN;
        boolean prevHov    = prevExists
                && mouseX >= prevX && mouseX <= prevX + ARROW_W
                && mouseY >= arrowY && mouseY <= arrowY + ARROW_H;
        drawArrowShape(context, prevX, arrowY, true,  prevExists, prevHov, alpha);

        boolean nextExists = hasNext();
        int     nextX      = imgX + imgW + ARROW_MARGIN;
        boolean nextHov    = nextExists
                && mouseX >= nextX && mouseX <= nextX + ARROW_W
                && mouseY >= arrowY && mouseY <= arrowY + ARROW_H;
        drawArrowShape(context, nextX, arrowY, false, nextExists, nextHov, alpha);
    }

    private void drawArrowShape(GuiGraphics context,
                                int x, int y,
                                boolean pointLeft,
                                boolean active,
                                boolean hovered,
                                float globalAlpha) {
        int iconBase = active  ? (hovered ? 0xFF : 0xCC) : 0x44;

        int iconAlpha = (int)(iconBase * globalAlpha);

        int cx    = x + ARROW_W / 2;
        int cy    = y + ARROW_H / 2;
        int color = (iconAlpha << 24) | 0x00FFFFFF;

        int halfSize = 6;
        int thickness = 2;
        int startX = cx - 4;

        for (int i = 0; i <= halfSize; i++) {
            int dx = pointLeft ? (halfSize - i) : i;
            context.fill(startX + dx, cy - halfSize + i,
                    startX + dx + thickness, cy - halfSize + i + 1, color);

            if (i != halfSize) {
                context.fill(startX + dx, cy + halfSize - i,
                        startX + dx + thickness, cy + halfSize - i + 1, color);
            }
        }
    }

    // ── Main render ───────────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics context,
                                  int mouseX, int mouseY, float delta) {
        if (parent instanceof ScreenshotGalleryScreen) {
            DynamicTexture bgTex = ScreenshotPreviewRenderer.getBackgroundTexture();
            if (bgTex != null && bgTex.getPixels() != null) {
                NativeImage pixels = bgTex.getPixels();
                if (pixels.getWidth() == this.width && pixels.getHeight() == this.height) {
                    context.blit(RenderType::guiTextured, ScreenshotPreviewRenderer.BACKGROUND_ID,
                            0, 0, 0f, 0f, this.width, this.height,
                            pixels.getWidth(), pixels.getHeight());
                    return;
                }
            }
        }

        if (parent != null) {
            if (parent.width != this.width || parent.height != this.height) {
                parent.resize(this.minecraft, this.width, this.height);
            }
            parent.render(context, -1, -1, delta);
        } else {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void render(GuiGraphics context,
                                   int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // Keep fullscreen content above parent menu.
        context.pose().pushPose();
        context.pose().translate(0, 0, 500f);

        long    now     = System.currentTimeMillis();
        boolean useAnim = ScreenshotConfig.get().uiAnimationsEnabled();

        // ── Background dim ────────────────────────────────────────────────────
        int bgAlpha;
        if (closing && closeStart >= 0) {
            long  exitElapsed = now - closeStart;
            long  totalMs     = EXIT_DURATION_MS + BLUR_FADE_MS;
            if (!useAnim || exitElapsed > totalMs) {
                context.pose().popPose();
                this.minecraft.setScreen(parent);
                return;
            }
            float totalT = Math.min((float) exitElapsed / totalMs, 1f);
            bgAlpha = (int)(Math.max(0f, 0.8f * (1f - totalT)) * 255);
        } else {
            float bgProg = useAnim
                    ? Math.min((float)(now - loadingStart) / ENTER_DURATION_MS, 1.0f)
                    : 1.0f;
            bgAlpha = (int)(0.8f * bgProg * 255);
        }
        context.fill(0, 0, this.width, this.height, (bgAlpha << 24));

        // ── Navigation slide transition ───────────────────────────────────────
        boolean navAnimActive = navAnimStart >= 0
                && (!useAnim || (now - navAnimStart) < NAV_ANIM_MS);

        if (navAnimActive) {
            long  navElapsed = now - navAnimStart;
            float navT       = useAnim
                    ? Math.min((float) navElapsed / NAV_ANIM_MS, 1f)
                    : 1f;
            float easedT     = easeInOutCubic(navT);
            int   slideAmt   = (int)(this.width * NAV_SLIDE_FRAC);

            DynamicTexture refTex = (navOldTex != null && navOldWidth > 0)
                    ? navOldTex : expectedTexture;
            if (refTex == null
                    || (refTex == navOldTex && navOldWidth <= 0)
                    || (refTex == expectedTexture && (expectedTexture == null || expectedTexture.getPixels() == null))) {
                drawLoadingSpinner(context);
                context.pose().popPose();
                return;
            }
            int   tW      = this.width  - MARGIN * 2;
            int   tH      = this.height - MARGIN * 2 - fullscreenNameBarHeight();
            int   imgW    = (refTex == navOldTex) ? navOldWidth  : refTex.getPixels().getWidth();
            int   imgH    = (refTex == navOldTex) ? navOldHeight : refTex.getPixels().getHeight();
            float sc      = Math.min((float) tW / imgW, (float) tH / imgH);
            int   targetW = (int)(imgW * sc);
            int   targetH = (int)(imgH * sc);
            int   targetX = (this.width  - targetW) / 2;
            int   targetY = (this.height - targetH) / 2;

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            int oldOffsetX = (int)(-navDirection * slideAmt * easedT);
            int oldArgb = ((int)((1f - easedT) * 255) << 24) | 0xFFFFFF;
            if (navOldTexId != null && navOldTex != null) {
                int oldAlphaI = (int)((1f - easedT) * 255);
                context.blit(RenderType::guiTextured, navOldTexId,
                        targetX + oldOffsetX, targetY,
                        0f, 0f, targetW, targetH, targetW, targetH,
                        oldArgb);
                drawNameBar(context, navOldFile, targetX + oldOffsetX, targetY + targetH,
                        targetW, mouseX, mouseY, oldAlphaI, false);
                drawFullscreenFrame(context, targetX + oldOffsetX, targetY,
                        targetW, targetH, navOldFile, oldAlphaI);
            }

            if (loaded && expectedTexture != null && expectedTexture.getPixels() != null) {
                int newOffsetX = (int)(navDirection * slideAmt * (1f - easedT));
                int newAlphaI = (int)(easedT * 255);
                int newArgb = (newAlphaI << 24) | 0xFFFFFF;

                int   nW  = expectedTexture.getPixels().getWidth();
                int   nH  = expectedTexture.getPixels().getHeight();
                float nSc = Math.min((float) tW / nW, (float) tH / nH);
                int   nTW = (int)(nW * nSc);
                int   nTH = (int)(nH * nSc);
                int   nTX = (this.width  - nTW) / 2;
                int   nTY = (this.height - nTH) / 2;

                context.blit(RenderType::guiTextured, getTexId(),
                        nTX + newOffsetX, nTY,
                        0f, 0f, nTW, nTH, nTW, nTH,
                        newArgb);
                drawNameBar(context, nTX + newOffsetX, nTY + nTH,
                        nTW, mouseX, mouseY, newAlphaI, false);
                drawFullscreenFrame(context, nTX + newOffsetX, nTY,
                        nTW, nTH, currentFile(), newAlphaI);
            }
            RenderSystem.disableBlend();

            drawCopyFlashFrame(context, targetX, targetY, targetW, targetH, now);

            if (!useAnim || navT >= 1f) {
                ScreenshotPreviewRenderer.deferClose(navOldTex);
                navAnimStart      = -1;
                navOldTexId       = null;
                navOldTex         = null;
                navOldFile        = null;
                navOldWidth       = 0;
                navOldHeight      = 0;
                imageFullyShownAt = now;
                openedAt          = now;
            }

            drawHintText(context);
            context.pose().popPose();
            return;
        }

        // ── No transition: normal loading or not-yet-loaded ───────────────────
        if (!loaded || expectedTexture == null) {
            drawLoadingSpinner(context);
            context.pose().popPose();
            return;
        }

        // ── Compute target rect ───────────────────────────────────────────────
        var   img     = expectedTexture;
        int   tW      = this.width  - MARGIN * 2;
        int   tH      = this.height - MARGIN * 2 - fullscreenNameBarHeight();
        float sc      = Math.min(
                (float) tW / img.getPixels().getWidth(),
                (float) tH / img.getPixels().getHeight());
        int   targetW = (int)(img.getPixels().getWidth()  * sc);
        int   targetH = (int)(img.getPixels().getHeight() * sc);
        int   targetX = (this.width  - targetW) / 2;
        int   targetY = (this.height - targetH) / 2;

        // ── Screen-close exit animation ───────────────────────────────────────
        if (closing && closeStart >= 0) {
            long  exitElapsed = now - closeStart;
            if (exitElapsed <= EXIT_DURATION_MS) {
                float overallT  = (float) exitElapsed / EXIT_DURATION_MS;
                float imgAlpha  = Math.max(0f, 1f - overallT * 1.4f);

                float offsetY;
                if (exitElapsed < BOUNCE_UP_MS) {
                    offsetY = -BOUNCE_HEIGHT
                            * easeOutCubic((float) exitElapsed / BOUNCE_UP_MS);
                } else {
                    float tDrop = (float)(exitElapsed - BOUNCE_UP_MS)
                            / (EXIT_DURATION_MS - BOUNCE_UP_MS);
                    offsetY = -BOUNCE_HEIGHT + EXIT_DROP * easeInCubic(tDrop);
                }

                int imgY      = (int)(lastCurY + offsetY);
                int imgAlphaI = (int)(imgAlpha * 255);
                int imgArgb   = ((int)(Math.max(0f, Math.min(1f, imgAlphaI / 255f)) * 255) << 24) | 0xFFFFFF;
                context.blit(RenderType::guiTextured, getTexId(),
                        lastCurX, imgY, 0f, 0f, lastCurW, lastCurH, lastCurW, lastCurH,
                        imgArgb);
                drawNameBar(context, currentFile(), lastCurX, imgY + lastCurH,
                        lastCurW, mouseX, mouseY, imgAlphaI, false);
                drawFullscreenFrame(context, lastCurX, imgY,
                        lastCurW, lastCurH, currentFile(), imgAlphaI);
            }
            context.pose().popPose();
            return;
        }

        // ── First-open entrance animation ─────────────────────────────────────
        if (skipEntranceAnim) {
            if (openedAt < 0) openedAt = now;
            lastCurX = targetX; lastCurY = targetY;
            lastCurW = targetW; lastCurH = targetH;
            if (imageFullyShownAt < 0) imageFullyShownAt = now;

            context.blit(RenderType::guiTextured, getTexId(),
                    targetX, targetY, 0f, 0f, targetW, targetH, targetW, targetH, 0xFFFFFFFF);
            drawCopyFlashFrame(context, targetX, targetY, targetW, targetH, now);
            drawUploadProgressBar(context, targetX, targetY, targetW, targetH);
            drawNameBar(context, targetX, targetY + targetH, targetW, mouseX, mouseY);
            drawFullscreenFrame(context, targetX, targetY, targetW, targetH, currentFile(), 255);
            drawArrows(context, targetX, targetY, targetW, targetH, mouseX, mouseY);
            drawActionButtons(context, targetX, targetY, targetW, targetH, mouseX, mouseY);
            drawHintText(context);
            if (nameEditBox != null && nameEditBox.isVisible()) {
                nameEditBox.render(context, mouseX, mouseY, delta);
            }

            context.pose().popPose();
            return;
        }

        if (openedAt < 0) {
            initStartPosition();
            openedAt = now;
        }

        float rawProgress = useAnim
                ? Math.min((float)(now - openedAt) / ENTER_DURATION_MS, 1.0f)
                : 1.0f;
        float t = easeOutCubic(rawProgress);

        int curX = (int)(startX + (targetX - startX) * t);
        int curY = (int)(startY + (targetY - startY) * t);
        int curW = (int)(startW + (targetW - startW) * t);
        int curH = (int)(startH + (targetH - startH) * t);

        lastCurX = targetX;
        lastCurY = targetY;
        lastCurW = targetW;
        lastCurH = targetH;

        context.blit(RenderType::guiTextured, getTexId(),
                curX, curY, 0f, 0f, curW, curH, curW, curH, 0xFFFFFFFF);
        drawCopyFlashFrame(context, curX, curY, curW, curH, now);
        drawNameBar(context, curX, curY + curH, curW, mouseX, mouseY,
                (int)(Math.max(0f, Math.min(1f, rawProgress)) * 255), rawProgress >= 1.0f);
        drawFullscreenFrame(context, curX, curY, curW, curH, currentFile(),
                (int)(Math.max(0f, Math.min(1f, rawProgress)) * 255));

        if (rawProgress >= 1.0f) {
            if (imageFullyShownAt < 0) imageFullyShownAt = now;
            drawUploadProgressBar(context, targetX, targetY, targetW, targetH);
            drawArrows(context, targetX, targetY, targetW, targetH, mouseX, mouseY);
            drawActionButtons(context, targetX, targetY, targetW, targetH, mouseX, mouseY);
            drawHintText(context);
        }
        if (nameEditBox != null && nameEditBox.isVisible()) {
            nameEditBox.render(context, mouseX, mouseY, delta);
        }

        context.pose().popPose();
    }

    // ── Loading spinner ───────────────────────────────────────────────────────

    private void drawLoadingSpinner(GuiGraphics context) {
        long  now     = System.currentTimeMillis();
        float elapsed = (now - loadingStart) / 1000f;
        int   cx      = this.width  / 2;
        int   cy      = this.height / 2;
        int   radius  = 16;
        int   dots    = 8;
        for (int i = 0; i < dots; i++) {
            float angle  = (float)(i * Math.PI * 2 / dots) - elapsed * 3f;
            float bright = ((i + (int)(elapsed * dots)) % dots) / (float) dots;
            int   alpha  = (int)(60 + bright * 195);
            int   size   = (int)(1 + bright * 2);
            int   dx     = (int)(Math.cos(angle) * radius);
            int   dy     = (int)(Math.sin(angle) * radius);
            context.fill(cx + dx - size, cy + dy - size,
                    cx + dx + size, cy + dy + size,
                    (alpha << 24) | 0x00FFFFFF);
        }
        context.drawCenteredString(font,
                Component.translatable("better_screenshots.fullscreen.loading"),
                cx, cy + radius + 8, 0x88FFFFFF);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public void mouseMoved(double mouseX, double mouseY) {}

    public boolean handleNavClick(double mouseX, double mouseY) {
        if (!loaded || expectedTexture == null || expectedTexture.getPixels() == null) return false;
        if (closing || navLoading) return false;
        if (imageFullyShownAt < 0) return false;

        var img = expectedTexture;
        if (img == null || img.getPixels() == null) return false;

        int   tW     = this.width  - MARGIN * 2;
        int   tH     = this.height - MARGIN * 2 - fullscreenNameBarHeight();
        float sc     = Math.min(
                (float) tW / img.getPixels().getWidth(),
                (float) tH / img.getPixels().getHeight());
        int targetW  = (int)(img.getPixels().getWidth()  * sc);
        int targetH  = (int)(img.getPixels().getHeight() * sc);
        int targetX  = (this.width  - targetW) / 2;
        int targetY  = (this.height - targetH) / 2;

        if (fullscreenNameBarSupported() && currentFile() != null) {
            lastMetaX = targetX;
            lastMetaY = targetY + targetH;
            lastMetaW = targetW;
            if (isEditIconHit(mouseX, mouseY)) {
                startNameEdit();
                return true;
            }
        }

        boolean showUploadAction = ScreenshotUploader.isUploaderEnabled();
        boolean[] visible =
                layoutActionButtons(targetX, targetY, targetW, targetH, showUploadAction);
        for (int i = 0; i < 4; i++) {
            if (!visible[i]) continue;
            if (mouseX < actionBtnX[i] || mouseX > actionBtnX[i] + ACT_BTN_W
                    || mouseY < actionBtnY[i] || mouseY > actionBtnY[i] + ACT_BTN_H) continue;
            playActionButtonClickSound();
            if (i == 0) closeLikeEscape();
            else if (i == 1) {
                ScreenshotPreviewRenderer.copyFileToClipboard(screenshotFiles.get(currentFileIndex));
                copyFlashAlpha = 1f;
                copyFlashLastFrameMs = -1L;
            } else if (i == 2) {
                uploadCurrent();
            } else deleteCurrent();
            return true;
        }

        // 2. Sprawdzanie strzałek (tylko jeżeli plików jest więcej niż 1)
        if (screenshotFiles.size() <= 1) return false;

        int arrowY   = targetY + (targetH - ARROW_H) / 2;

        int prevX = targetX - ARROW_W - ARROW_MARGIN;
        if (hasPrev()
                && mouseX >= prevX && mouseX <= prevX + ARROW_W
                && mouseY >= arrowY && mouseY <= arrowY + ARROW_H) {
            navigateTo(-1);
            return true;
        }

        int nextX = targetX + targetW + ARROW_MARGIN;
        if (hasNext()
                && mouseX >= nextX && mouseX <= nextX + ARROW_W
                && mouseY >= arrowY && mouseY <= arrowY + ARROW_H) {
            navigateTo(+1);
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode;

        if (editingName) {
            if (key == 257 || key == 335) { // Enter / keypad Enter
                acceptNameEdit();
                return true;
            }
            if (key == 256) { // ESC
                cancelNameEdit();
                return true;
            }
            if (nameEditBox != null) {
                nameEditBox.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }

        if (!closing && loaded && expectedTexture != null
                && screenshotFiles.size() > 1 && currentFileIndex >= 0) {
            if (key == 263 && hasPrev()) { navigateTo(-1); return true; } // ←
            if (key == 262 && hasNext()) { navigateTo(+1); return true; } // →
        }

        if (key == 256) { // ESC
            closeLikeEscape();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editingName && nameEditBox != null) {
            nameEditBox.charTyped(codePoint, modifiers);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (closing || navLoading) return false;
        if (scrollY > 0) {
            if (hasPrev()) navigateTo(-1);
            return true;
        } else if (scrollY < 0) {
            if (hasNext()) navigateTo(+1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void playActionButtonClickSound() {
        if (minecraft == null || minecraft.getSoundManager() == null) return;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 2.0f));
    }

    private void uploadCurrent() {
        if (!ScreenshotUploader.isUploaderEnabled()) return;
        if (screenshotFiles.isEmpty() || currentFileIndex < 0 || currentFileIndex >= screenshotFiles.size()) return;
        File file = screenshotFiles.get(currentFileIndex);
        String uploadId = String.valueOf(System.nanoTime());
        beginUploadOverlay();
        ScreenshotUploader.uploadAsync(file, new ScreenshotUploader.Listener() {
            @Override
            public void onProgress(double progress) {
                minecraft.execute(() -> updateUploadOverlay(progress));
            }

            @Override
            public void onSuccess(String uploadedUrl) {
                minecraft.execute(() -> {
                    ScreenshotPreviewRenderer.registerUploadedUrl(uploadId, uploadedUrl);
                    ScreenshotUploader.copyUrlToClipboard(uploadedUrl);
                    ScreenshotUploader.showUploadSuccessToast();
                    markUploadOverlaySuccess();
                });
            }

            @Override
            public void onError(String error) {
                minecraft.execute(() -> {
                    ScreenshotUploader.showUploadErrorToast(error);
                    markUploadOverlayError();
                });
            }
        });
    }

    private void resetUploadOverlay() {
        uploadState = FullscreenUploadState.HIDDEN;
        uploadProgressTarget = 0f;
        uploadProgressDisplayed = 0f;
        uploadLastFrameMs = -1L;
        uploadClearAtMs = 0L;
    }

    private void beginUploadOverlay() {
        uploadState = FullscreenUploadState.UPLOADING;
        uploadProgressTarget = 0.04f;
        uploadProgressDisplayed = 0f;
        uploadLastFrameMs = -1L;
        uploadClearAtMs = 0L;
    }

    private void updateUploadOverlay(double progress) {
        uploadState = FullscreenUploadState.UPLOADING;
        uploadProgressTarget = Math.max(0.04f, Math.min(1f, (float) progress));
        uploadClearAtMs = 0L;
    }

    private void markUploadOverlaySuccess() {
        uploadState = FullscreenUploadState.SUCCESS;
        uploadProgressTarget = 1f;
        uploadClearAtMs = System.currentTimeMillis() + UPLOAD_STATE_HOLD_MS;
    }

    private void markUploadOverlayError() {
        uploadState = FullscreenUploadState.ERROR;
        uploadProgressTarget = 1f;
        uploadClearAtMs = System.currentTimeMillis() + UPLOAD_STATE_HOLD_MS;
    }

    private void drawUploadProgressBar(GuiGraphics context, int x, int y, int w, int h) {
        if (uploadState == FullscreenUploadState.HIDDEN) return;

        long now = System.currentTimeMillis();
        if (uploadState != FullscreenUploadState.UPLOADING && now > uploadClearAtMs) {
            resetUploadOverlay();
            return;
        }

        if (uploadLastFrameMs < 0L) {
            uploadLastFrameMs = now;
        }
        float dt = Math.max(0f, Math.min(100f, now - uploadLastFrameMs));
        uploadLastFrameMs = now;
        float smoothing = 1f - (float) Math.exp(-dt / 120f);
        uploadProgressDisplayed += (uploadProgressTarget - uploadProgressDisplayed) * smoothing;
        if (Math.abs(uploadProgressTarget - uploadProgressDisplayed) < 0.001f) {
            uploadProgressDisplayed = uploadProgressTarget;
        }

        int barX = x;
        int barY = y + h - UPLOAD_BAR_H;
        int barW = w;
        int fillW;
        int color;
        switch (uploadState) {
            case SUCCESS -> {
                color = 0xFF34C759;
                fillW = barW;
            }
            case ERROR -> {
                color = 0xFFE74C3C;
                fillW = barW;
            }
            default -> {
                color = 0xFF42B9FF;
                float p = Math.max(0.04f, Math.min(1f, uploadProgressDisplayed));
                fillW = Math.max(1, (int) (barW * p));
            }
        }

        context.fill(barX, barY, barX + barW, barY + UPLOAD_BAR_H, 0x66000000);
        context.fill(barX, barY, barX + fillW, barY + UPLOAD_BAR_H, color);
    }

    private void drawCopyFlashFrame(GuiGraphics context, int x, int y, int w, int h, long now) {
        if (copyFlashAlpha <= 0f) return;

        if (copyFlashLastFrameMs < 0L) {
            copyFlashLastFrameMs = now;
        } else {
            float dt = Math.max(0f, Math.min(100f, now - copyFlashLastFrameMs));
            copyFlashLastFrameMs = now;
            copyFlashAlpha = Math.max(0f, copyFlashAlpha - (dt / (float) COPY_FLASH_MS));
        }

        if (copyFlashAlpha <= 0f) {
            copyFlashAlpha = 0f;
            copyFlashLastFrameMs = -1L;
            return;
        }

        int alpha = Math.max(0, Math.min(255, (int) (copyFlashAlpha * 255f)));
        if (alpha <= 0) return;

        int thickness = 2;
        int left = x;
        int top = y;
        int right = x + w;
        int bottom = y + h;
        if (right - left <= thickness * 2 || bottom - top <= thickness * 2) return;

        int color = (alpha << 24) | 0x004499FF;
        context.fill(left, top, right, top + thickness, color);
        context.fill(left, bottom - thickness, right, bottom, color);
        context.fill(left, top + thickness, left + thickness, bottom - thickness, color);
        context.fill(right - thickness, top + thickness, right, bottom - thickness, color);
    }
}
