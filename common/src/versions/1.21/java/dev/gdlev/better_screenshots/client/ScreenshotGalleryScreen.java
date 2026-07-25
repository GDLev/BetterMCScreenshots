package dev.gdlev.better_screenshots.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.Util;

public class ScreenshotGalleryScreen extends Screen {

    private final Screen parent;

    private static final int MIN_THUMB_W = 112;
    private static final int MAX_THUMB_W = 132;
    private static final int META_H      = 14;
    private static final int THUMB_GAP   = 6;
    private static final int GRID_PAD_X  = 14;
    private static final int TOP_PAD    = 30;
    private static final int BOTTOM_PAD = 8;
    private static final int SORT_W     = 126;
    private static final int FOLDER_W   = 58;
    private static final int SORT_H     = 18;
    private static final int CONTROLS_Y = 4;
    private static final int EDIT_ICON_W = 10;
    private static final int NAME_MAX_LENGTH = 80;

    private final List<File>                     files         = new ArrayList<>();
    private final List<ResourceLocation>               thumbIds      = new ArrayList<>();
    private final List<DynamicTexture> thumbTextures = new ArrayList<>();

    private int   scrollOffset   = 0;
    private int   preservedScrollOffset = 0;
    private boolean preserveScrollOnReload = false;
    private int   totalContentH  = 0;
    private int   selectedIdx    = -1;

    private boolean draggingScrollbar = false;
    private double  scrollbarDragOffsetY = 0.0;
    private static final int SCROLLBAR_HIT_W = 10; // easier to grab than 3px

    private final int[] actionBtnX = new int[4];
    private final int[] actionBtnY = new int[4];
    private static final int ACT_BTN_W = 8;
    private static final int ACT_BTN_H = 10;
    private static final int ACT_BTN_GAP = 0;
    private static final int UPLOAD_BAR_H = 2;
    private static final long UPLOAD_STATE_HOLD_MS = 1400L;

    private static final ResourceLocation ICON_SHOW    = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/show.png");
    private static final ResourceLocation ICON_SHOW_H  = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/show_hover.png");
    private static final ResourceLocation ICON_COPY    = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/copy.png");
    private static final ResourceLocation ICON_COPY_H  = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/copy_hover.png");
    private static final ResourceLocation ICON_UPLOAD  = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/upload.png");
    private static final ResourceLocation ICON_UPLOAD_H= ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/upload_hover.png");
    private static final ResourceLocation ICON_DELETE  = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/delete.png");
    private static final ResourceLocation ICON_DELETE_H= ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/delete_hover.png");

    private boolean pendingExternalRefresh = false;
    private final Map<String, ThumbUploadOverlay> thumbUploadStates = new HashMap<>();
    private static final long DOUBLE_CLICK_MS = 300L;
    private int lastClickedThumbIdx = -1;
    private long lastThumbClickMs = -1L;
    private int thumbnailTextureScale = 1;
    private SortMode sortMode = SortMode.NEWEST_FIRST;
    private Button backButton;
    private Button sortButton;
    private Button folderButton;
    private int hoveredActionButton = -1;
    private EditBox nameEditBox;
    private int editingNameIdx = -1;

    private enum SortMode {
        NEWEST_FIRST("better_screenshots.gallery.sort.newest"),
        OLDEST_FIRST("better_screenshots.gallery.sort.oldest"),
        NAME_A_Z("better_screenshots.gallery.sort.az"),
        NAME_Z_A("better_screenshots.gallery.sort.za");

        private final String translationKey;

        SortMode(String translationKey) {
            this.translationKey = translationKey;
        }

        private SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private enum ThumbUploadState {
        UPLOADING, SUCCESS, ERROR
    }

    private static final class ThumbUploadOverlay {
        ThumbUploadState state = ThumbUploadState.UPLOADING;
        float target = 0f;
        float displayed = 0f;
        long lastFrameMs = -1L;
        long clearAtMs = 0L;
    }

    public ScreenshotGalleryScreen(Screen parent) {
        super(Component.translatable("better_screenshots.gallery.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildControls();

        if (pendingExternalRefresh) {
            pendingExternalRefresh = false;
            loadScreenshots();
        } else if (files.isEmpty() && thumbIds.isEmpty()) {
            loadScreenshots();
        }
        restorePreservedScroll();
    }

    private static final int MAX_CONCURRENT_LOADS = 4;
    private final java.util.concurrent.Semaphore loadSemaphore =
            new java.util.concurrent.Semaphore(MAX_CONCURRENT_LOADS);

    private void loadScreenshots() {
        for (DynamicTexture t : thumbTextures) if (t != null) t.close();
        thumbTextures.clear();
        thumbIds.clear();
        files.clear();
        int scrollToRestore = preserveScrollOnReload ? preservedScrollOffset : 0;
        selectedIdx  = -1;
        cancelNameEdit();
        scrollOffset = 0;
        thumbUploadStates.clear();

        Minecraft mc = Minecraft.getInstance();
        File dir = new File(mc.gameDirectory, "screenshots");
        if (!dir.exists()) return;

        File[] found = dir.listFiles(
                f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));
        if (found == null) return;

        thumbnailTextureScale = calculateThumbnailTextureScale(mc);
        Collections.addAll(files, found);
        sortFiles();

        for (int i = 0; i < files.size(); i++) {
            thumbIds.add(ResourceLocation.fromNamespaceAndPath("better_screenshots",
                    "gal_thumb_" + i + "_" + files.get(i).lastModified()));
            thumbTextures.add(null);
        }

        int priority = Math.min(files.size(), 8);
        for (int i = 0; i < priority; i++) {
            final int idx = i;
            Thread.ofVirtual().start(() -> loadThumbLimited(mc, idx));
        }
        for (int i = priority; i < files.size(); i++) {
            final int idx = i;
            Thread.ofVirtual().start(() -> {
                try { Thread.sleep(idx * 5L); } catch (Exception ignored) {}
                loadThumbLimited(mc, idx);
            });
        }

        recalcContentH();
        if (preserveScrollOnReload) {
            scrollOffset = scrollToRestore;
            clampScroll();
        }
        refreshActionButtons();
    }

    private void sortFiles() {
        Comparator<File> comparator = switch (sortMode) {
            case NEWEST_FIRST -> Comparator.comparingLong(File::lastModified).reversed();
            case OLDEST_FIRST -> Comparator.comparingLong(File::lastModified);
            case NAME_A_Z -> Comparator.comparing(file -> file.getName().toLowerCase(Locale.ROOT));
            case NAME_Z_A -> Comparator.comparing((File file) -> file.getName().toLowerCase(Locale.ROOT)).reversed();
        };
        files.sort(comparator);
    }

    public void refreshAfterExternalChange() {
        pendingExternalRefresh = true;
    }

    private void loadThumbLimited(Minecraft mc, int idx) {
        try {
            loadSemaphore.acquire();
            loadThumb(mc, idx);
        } catch (InterruptedException ignored) {
        } finally {
            loadSemaphore.release();
        }
    }
    private void recalcContentH() {
        int rows = (int) Math.ceil((double) files.size() / cols());
        totalContentH = rows * (tileH() + THUMB_GAP);
        clampScroll();
    }

    private void loadThumb(Minecraft mc, int idx) {
        try (InputStream is = Files.newInputStream(files.get(idx).toPath())) {
            NativeImage img   = NativeImage.read(is);
            NativeImage thumb = scaleTo(img);
            img.close();
            mc.execute(() -> {
                if (idx >= thumbTextures.size()) return;
                DynamicTexture tex =
                        new DynamicTexture(thumb);
                mc.getTextureManager().register(thumbIds.get(idx), tex);
                thumbTextures.set(idx, tex);
            });
        } catch (Exception ignored) {}
    }

    private NativeImage scaleTo(NativeImage src) {
        int targetW = thumbnailTextureWidth();
        int targetH = thumbnailTextureHeight();
        float scale = Math.min((float) targetW / src.getWidth(),
                (float) targetH / src.getHeight());
        int sw = Math.max(1, Math.round(src.getWidth()  * scale));
        int sh = Math.max(1, Math.round(src.getHeight() * scale));

        float scaleX = (float) src.getWidth()  / sw;
        float scaleY = (float) src.getHeight() / sh;
        boolean pixelated = ScreenshotConfig.get().pixelatedPreviews;

        NativeImage dst = new NativeImage(sw, sh, false);

        for (int y = 0; y < sh; y++) {
            float sourceY = (y + 0.5f) * scaleY - 0.5f;
            int sy = Math.min((int)(((y + 0.5f) * scaleY)), src.getHeight() - 1);
            for (int x = 0; x < sw; x++) {
                float sourceX = (x + 0.5f) * scaleX - 0.5f;
                int sx   = Math.min((int)(((x + 0.5f) * scaleX)), src.getWidth() - 1);
                int argb = pixelated
                        ? src.getPixelRGBA(sx, sy)
                        : sampleSmoothArgb(src, sourceX, sourceY, scaleX, scaleY);
                dst.setPixelRGBA(x, y, argb);
            }
        }
        return dst;
    }

    private static int sampleBilinearArgb(NativeImage src, float sourceX, float sourceY) {
        float x = Math.max(0f, Math.min(src.getWidth() - 1f, sourceX));
        float y = Math.max(0f, Math.min(src.getHeight() - 1f, sourceY));
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = Math.min(x0 + 1, src.getWidth() - 1);
        int y1 = Math.min(y0 + 1, src.getHeight() - 1);
        float tx = x - x0;
        float ty = y - y0;

        int c00 = src.getPixelRGBA(x0, y0);
        int c10 = src.getPixelRGBA(x1, y0);
        int c01 = src.getPixelRGBA(x0, y1);
        int c11 = src.getPixelRGBA(x1, y1);

        int a = bilinearChannel(c00, c10, c01, c11, 24, tx, ty);
        int r = bilinearChannel(c00, c10, c01, c11, 16, tx, ty);
        int g = bilinearChannel(c00, c10, c01, c11, 8, tx, ty);
        int b = bilinearChannel(c00, c10, c01, c11, 0, tx, ty);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int sampleSmoothArgb(NativeImage src, float centerX, float centerY, float scaleX, float scaleY) {
        if (scaleX <= 1.5f && scaleY <= 1.5f) {
            return sampleBilinearArgb(src, centerX, centerY);
        }

        int samplesX = Math.min(5, Math.max(2, (int) Math.ceil(scaleX / 8f)));
        int samplesY = Math.min(5, Math.max(2, (int) Math.ceil(scaleY / 8f)));
        float startX = centerX - scaleX / 2f;
        float startY = centerY - scaleY / 2f;
        int total = samplesX * samplesY;
        int aSum = 0;
        int rSum = 0;
        int gSum = 0;
        int bSum = 0;

        for (int yy = 0; yy < samplesY; yy++) {
            float sy = startY + (yy + 0.5f) * scaleY / samplesY;
            for (int xx = 0; xx < samplesX; xx++) {
                float sx = startX + (xx + 0.5f) * scaleX / samplesX;
                int argb = sampleBilinearArgb(src, sx, sy);
                aSum += (argb >> 24) & 0xFF;
                rSum += (argb >> 16) & 0xFF;
                gSum += (argb >> 8) & 0xFF;
                bSum += argb & 0xFF;
            }
        }

        int a = Math.round((float) aSum / total);
        int r = Math.round((float) rSum / total);
        int g = Math.round((float) gSum / total);
        int b = Math.round((float) bSum / total);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int bilinearChannel(int c00, int c10, int c01, int c11, int shift, float tx, float ty) {
        float v00 = (c00 >> shift) & 0xFF;
        float v10 = (c10 >> shift) & 0xFF;
        float v01 = (c01 >> shift) & 0xFF;
        float v11 = (c11 >> shift) & 0xFF;
        float top = v00 + (v10 - v00) * tx;
        float bottom = v01 + (v11 - v01) * tx;
        return Math.max(0, Math.min(255, Math.round(top + (bottom - top) * ty)));
    }

    // Layout

    private int cols() {
        int available = Math.max(MIN_THUMB_W, this.width - GRID_PAD_X * 2 - SCROLLBAR_HIT_W);
        int count = Math.max(1, (available + THUMB_GAP) / (MIN_THUMB_W + THUMB_GAP));
        return Math.max(1, Math.min(6, count));
    }

    private int thumbW() {
        int cols = cols();
        int available = Math.max(MIN_THUMB_W, this.width - GRID_PAD_X * 2 - SCROLLBAR_HIT_W);
        int width = (available - (cols - 1) * THUMB_GAP) / cols;
        return Math.max(MIN_THUMB_W, Math.min(MAX_THUMB_W, width));
    }

    private int thumbH() { return Math.max(1, thumbW() * 9 / 16); }
    private int tileH()  { return thumbH() + META_H; }
    private int gridW()      { return cols() * thumbW() + (cols() - 1) * THUMB_GAP; }
    private int gridStartX() { return (this.width - gridW()) / 2; }
    private int gridBottomY(){ return this.height - BOTTOM_PAD; }
    private int screenshotsTopPad() {
        return TOP_PAD + ScreenshotConfig.get().screenshotsFirstRowTopMargin;
    }

    private int thumbnailTextureWidth() {
        return MAX_THUMB_W * thumbnailTextureScale();
    }

    private int thumbnailTextureHeight() {
        return Math.max(1, (MAX_THUMB_W * 9 / 16) * thumbnailTextureScale());
    }

    private int thumbnailTextureScale() {
        return thumbnailTextureScale;
    }

    private int calculateThumbnailTextureScale(Minecraft mc) {
        if (ScreenshotConfig.get().pixelatedPreviews) {
            return 1;
        }
        if (mc == null || mc.getWindow() == null) {
            return 1;
        }
        return Math.max(1, Math.min(4, (int) Math.ceil(mc.getWindow().getGuiScale())));
    }

    private void clampScroll() {
        int visibleH = gridBottomY() - screenshotsTopPad();
        int maxScroll = Math.max(0, totalContentH - visibleH);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private void preserveCurrentScroll() {
        preservedScrollOffset = scrollOffset;
        preserveScrollOnReload = true;
    }

    private void restorePreservedScroll() {
        if (!preserveScrollOnReload) return;
        scrollOffset = preservedScrollOffset;
        clampScroll();
    }

    private void rememberScrollIfPreserving() {
        if (preserveScrollOnReload) {
            preservedScrollOffset = scrollOffset;
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        recalcContentH();
        restorePreservedScroll();
    }

    // Click handling

    public boolean handleClick(int button, double mouseX, double mouseY) {
        if (button != 0) return false;
        int topPad = screenshotsTopPad();

        if (isClickInsideNameEditor(mouseX, mouseY)) {
            return false;
        }

        // Scrollbar dragging (when content is taller than viewport)
        int bottomY  = gridBottomY();
        int visibleH = bottomY - topPad;
        if (totalContentH > visibleH) {
            int trackX0 = this.width - SCROLLBAR_HIT_W;
            int trackX1 = this.width;
            if (mouseX >= trackX0 && mouseX <= trackX1
                    && mouseY >= topPad && mouseY <= bottomY) {
                int tmbH   = Math.max(16, visibleH * visibleH / totalContentH);
                int maxSc  = totalContentH - visibleH;
                int travel = Math.max(1, visibleH - tmbH);
                int tmbY   = topPad + (maxSc > 0
                        ? (int)((float) scrollOffset / maxSc * travel) : 0);

                if (mouseY >= tmbY && mouseY <= tmbY + tmbH) {
                    scrollbarDragOffsetY = mouseY - tmbY;
                } else {
                    scrollbarDragOffsetY = tmbH / 2.0;
                }

                draggingScrollbar = true;
                updateScrollFromThumb(mouseY - scrollbarDragOffsetY, tmbH, visibleH, maxSc);
                return true;
            }
        }

        if (selectedIdx >= 0) {
            boolean showUploadAction = ScreenshotUploader.isUploaderEnabled();
            ScreenshotConfig config = ScreenshotConfig.get();
            boolean[] visible = {
                    config.galleryShowVisible, config.galleryCopyVisible,
                    showUploadAction && config.galleryUploadVisible,
                    config.galleryDeleteVisible
            };
            for (int i = 0; i < 4; i++) {
                if (!visible[i]) continue;
                if (mouseX >= actionBtnX[i] && mouseX <= actionBtnX[i] + ACT_BTN_W
                        && mouseY >= actionBtnY[i] && mouseY <= actionBtnY[i] + ACT_BTN_H) {
                    playActionButtonClickSound();
                    switch (i) {
                        case 0 -> openFullscreen(selectedIdx);
                        case 1 -> copyFile(selectedIdx);
                        case 2 -> uploadFile(selectedIdx);
                        case 3 -> deleteFile(selectedIdx);
                    }
                    return true;
                }
            }
        }

        int sx = gridStartX();
        int cols = cols();
        int thumbW = thumbW();
        int tileH = tileH();

        for (int i = 0; i < files.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x   = sx + col * (thumbW + THUMB_GAP);
            int y = topPad + row * (tileH + THUMB_GAP) - scrollOffset;

            if (mouseX >= x && mouseX <= x + thumbW
                    && mouseY >= y && mouseY <= y + tileH
                    && mouseY >= topPad && mouseY <= bottomY) {
                if (isEditIconHit(mouseX, mouseY, x, y + thumbH(), thumbW)) {
                    startNameEdit(i);
                    return true;
                }
                long now = System.currentTimeMillis();
                if (lastClickedThumbIdx == i
                        && lastThumbClickMs > 0
                        && now - lastThumbClickMs <= DOUBLE_CLICK_MS) {
                    selectedIdx = i;
                    refreshActionButtons();
                    lastClickedThumbIdx = -1;
                    lastThumbClickMs = -1L;
                    playActionButtonClickSound();
                    openFullscreen(i);
                    return true;
                }
                lastClickedThumbIdx = i;
                lastThumbClickMs = now;
                selectedIdx = (selectedIdx == i) ? -1 : i;
                refreshActionButtons();
                return true;
            }
        }
        return false;
    }

    private void playActionButtonClickSound() {
        if (minecraft == null || minecraft.getSoundManager() == null) return;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 2.0f));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleClick(button, mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingNameIdx >= 0 && nameEditBox != null) {
            if (keyCode == 256) {
                cancelNameEdit();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                acceptNameEdit();
                return true;
            }
            if (nameEditBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        int topPad = screenshotsTopPad();
        if (button == 0 && draggingScrollbar) {
            int bottomY  = gridBottomY();
            int visibleH = bottomY - screenshotsTopPad();
            int tmbH     = Math.max(16, visibleH * visibleH / totalContentH);
            int maxSc    = Math.max(0, totalContentH - visibleH);
            updateScrollFromThumb(mouseY - scrollbarDragOffsetY, tmbH, visibleH, maxSc);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateScrollFromThumb(double thumbTopY, int thumbH, int visibleH, int maxScroll) {
        if (maxScroll <= 0) {
            scrollOffset = 0;
            rememberScrollIfPreserving();
            return;
        }
        int topPad = screenshotsTopPad();
        int travel = Math.max(1, visibleH - thumbH);
        double clamped = Math.max(topPad, Math.min(topPad + travel, thumbTopY));
        double ratio = (clamped - topPad) / travel;
        scrollOffset = (int) Math.round(ratio * maxScroll);
        rememberScrollIfPreserving();
    }

    // Render

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, 0xFF111111);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        restorePreservedScroll();
        int topPad = screenshotsTopPad();
        this.renderBackground(context, mouseX, mouseY, delta);
        hoveredActionButton = -1;
        updateNameEditBoxBounds();

        super.render(context, mouseX, mouseY, delta);
        drawTopBar(context);
        renderTopControls(context, mouseX, mouseY, delta);
        drawTopBarSeparator(context);

        int sx      = gridStartX();
        int bottomY = gridBottomY();
        int cols    = cols();
        int thumbW  = thumbW();
        int thumbH  = thumbH();
        int tileH   = tileH();

        // Scissor net
        context.enableScissor(0, topPad - 2, this.width, bottomY);

        for (int i = 0; i < files.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x   = sx + col * (thumbW + THUMB_GAP);
            int y   = topPad + row * (tileH + THUMB_GAP) - scrollOffset;

            if (y + tileH < topPad || y > bottomY) continue;

            boolean hov = mouseX >= x && mouseX <= x + thumbW
                    && mouseY >= y && mouseY <= y + tileH
                    && mouseY >= topPad && mouseY <= bottomY;
            boolean sel = (i == selectedIdx);

            int border = sel ? 0xFFFFFFFF : hov ? 0xFFAAAAAA : 0xFF555555;
            context.fill(x - 1, y - 1, x + thumbW + 1, y + tileH + 1, border);

            if (i < thumbTextures.size() && thumbTextures.get(i) != null) {
                DynamicTexture texture = thumbTextures.get(i);
                NativeImage pixels = texture.getPixels();
                int texW = pixels != null ? pixels.getWidth() : thumbnailTextureWidth();
                int texH = pixels != null ? pixels.getHeight() : thumbnailTextureHeight();
                float imageScale = Math.min((float) thumbW / texW, (float) thumbH / texH);
                int imageW = Math.max(1, Math.round(texW * imageScale));
                int imageH = Math.max(1, Math.round(texH * imageScale));
                int imageX = x + (thumbW - imageW) / 2;
                int imageY = y + (thumbH - imageH) / 2;
                context.fill(x, y, x + thumbW, y + thumbH, 0xFF000000);
                context.blit(thumbIds.get(i), imageX, imageY, imageW, imageH,
                        0f, 0f, texW, texH, texW, texH);
            } else {
                context.fill(x, y, x + thumbW, y + thumbH, 0xFF2a2a2a);
                context.drawCenteredString(font,
                        Component.translatable("better_screenshots.gallery.loading"),
                        x + thumbW / 2, y + thumbH / 2 - 4, 0xFF555555);
            }

            // Copy animation
            if (sel) {
                int ca = ScreenshotPreviewRenderer.getCopyFlashAlpha();
                if (ca > 0) context.fill(x, y, x + thumbW, y + thumbH, (ca << 24) | 0x004499FF);
            }

            if (i < files.size()) {
                renderUploadOverlay(context, x, y, thumbW, thumbH, files.get(i));
            }

            drawTimestampBar(context, files.get(i), i, x, y + thumbH, thumbW, mouseX, mouseY);
        }

        context.disableScissor();

        if (selectedIdx >= 0 && selectedIdx < files.size()) {
            drawSelectedPanel(context);
        }

        // Scrollbar
        int visibleH = bottomY - topPad;
        if (totalContentH > visibleH) {
            int tmbH   = Math.max(16, visibleH * visibleH / totalContentH);
            int maxSc  = totalContentH - visibleH;
            int tmbY   = topPad + (maxSc > 0
                    ? (int)((float) scrollOffset / maxSc * (visibleH - tmbH)) : 0);
            int tx     = this.width - 5;
            context.fill(tx, topPad, tx + 3, topPad + visibleH, 0x33FFFFFF);
            context.fill(tx, tmbY,    tx + 3, tmbY + tmbH,      0xBBFFFFFF);
        }

        renderNameEditBox(context, mouseX, mouseY, delta);
        ActionButtonTooltips.draw(context, font, this.width, this.height, mouseX, mouseY, hoveredActionButton, false);
        renderInstantPreviewAboveGallery(context);
    }


    private void renderInstantPreviewAboveGallery(GuiGraphics context) {
        flushGuiGraphics(context);
        ScreenshotPreviewRenderer.renderAboveScreens(context);
    }

    private void flushGuiGraphics(Object context) {
        try {
            context.getClass().getMethod("flush").invoke(context);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void drawSelectedPanel(GuiGraphics context) {
        if (selectedIdx < 0 || selectedIdx >= files.size()) return;
        int topPad = screenshotsTopPad();

        int cols   = cols();
        int thumbW = thumbW();
        int thumbH = thumbH();
        int tileH  = tileH();
        int col    = selectedIdx % cols;
        int row    = selectedIdx / cols;
        int sx     = gridStartX();
        int thumbX = sx + col * (thumbW + THUMB_GAP);
        int thumbY = topPad + row * (tileH + THUMB_GAP) - scrollOffset;

        boolean thumbVisible = thumbY + thumbH > topPad && thumbY < gridBottomY();

        if (thumbVisible) {
            // Keep the action buttons clipped to the thumbnails container
            int bottomY = gridBottomY();
            context.enableScissor(0, topPad, this.width, bottomY);

            // Action buttons
            boolean showUploadAction = ScreenshotUploader.isUploaderEnabled();
            ScreenshotConfig config = ScreenshotConfig.get();
            ScreenshotConfig.ActionButtonCorner[] corners = {
                    config.galleryShowCorner, config.galleryCopyCorner,
                    config.galleryUploadCorner, config.galleryDeleteCorner
            };
            int[] order = {
                    config.galleryShowOrder, config.galleryCopyOrder,
                    config.galleryUploadOrder, config.galleryDeleteOrder
            };
            boolean[] visible = {
                    config.galleryShowVisible, config.galleryCopyVisible,
                    showUploadAction && config.galleryUploadVisible,
                    config.galleryDeleteVisible
            };
            ActionButtonLayout.arrange(
                    actionBtnX, actionBtnY, corners, order, visible, 4,
                    thumbX, thumbY, thumbW, thumbH,
                    ACT_BTN_W, ACT_BTN_H, ACT_BTN_GAP, 2);

            Minecraft mc = Minecraft.getInstance();
            double mouseX = mc.mouseHandler.xpos() * this.width / mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos() * this.height / mc.getWindow().getScreenHeight();

            ResourceLocation[] icons = { ICON_SHOW, ICON_COPY, ICON_UPLOAD, ICON_DELETE };
            ResourceLocation[] iconsH = { ICON_SHOW_H, ICON_COPY_H, ICON_UPLOAD_H, ICON_DELETE_H };

            for (int i = 0; i < 4; i++) {
                if (!visible[i]) continue;
                boolean hov = mouseX >= actionBtnX[i]
                        && mouseX <= actionBtnX[i] + ACT_BTN_W
                        && mouseY >= actionBtnY[i]
                        && mouseY <= actionBtnY[i] + ACT_BTN_H;
                if (hov) hoveredActionButton = i;

                context.blit(hov ? iconsH[i] : icons[i],
                        actionBtnX[i], actionBtnY[i], ACT_BTN_W, ACT_BTN_H,
                        0f, 0f, ACT_BTN_W, ACT_BTN_H, ACT_BTN_W, ACT_BTN_H);
            }
            context.disableScissor();
        } else {
            for (int i = 0; i < actionBtnX.length; i++) {
                actionBtnX[i] = -100;
                actionBtnY[i] = -100;
            }
        }
    }

    private void drawTopBar(GuiGraphics context) {
        if (!ScreenshotConfig.get().renderTopBar) return;
        context.fill(0, 0, this.width, TOP_PAD - 4, 0xFF181818);
    }

    private void drawTopBarSeparator(GuiGraphics context) {
        if (!ScreenshotConfig.get().renderTopBar) return;
        context.fill(0, TOP_PAD - 4, this.width, TOP_PAD - 3, 0xFF2F2F2F);
        context.fill(0, TOP_PAD - 3, this.width, TOP_PAD - 2, 0x66000000);
    }

    private void renderTopControls(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (backButton != null) backButton.render(context, mouseX, mouseY, delta);
        if (sortButton != null) sortButton.render(context, mouseX, mouseY, delta);
        if (folderButton != null) folderButton.render(context, mouseX, mouseY, delta);
    }


    private void openFullscreen(int idx) {
        preserveCurrentScroll();
        Minecraft mc = Minecraft.getInstance();
        ScreenshotFullscreenScreen screen = new ScreenshotFullscreenScreen(this);
        // Provide the full file list so the fullscreen screen can navigate prev/next
        screen.setNavigationContext(files, idx);

        Thread.ofVirtual().start(() -> {
            try {
                byte[] bytes = Files.readAllBytes(files.get(idx).toPath());
                NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
                mc.execute(() -> {
                    ScreenshotPreviewRenderer.setFullscreenTexture(img);
                    screen.markLoaded();
                });
            } catch (Exception e) { e.printStackTrace(); }
        });

        ScreenshotPreviewRenderer.captureBackground(() -> mc.setScreen(screen));
    }

    private void refreshActionButtons() {
        if (backButton == null || sortButton == null || folderButton == null) {
            rebuildControls();
            return;
        }
        sortButton.setMessage(Component.translatable(sortMode.translationKey));
    }

    private void rebuildControls() {
        clearWidgets();
        backButton = Button.builder(
                        Component.translatable("better_screenshots.gallery.back"),
                        btn -> minecraft.setScreen(parent))
                .bounds(GRID_PAD_X, CONTROLS_Y, 60, SORT_H)
                .build();
        sortButton = Button.builder(
                        Component.translatable(sortMode.translationKey),
                        btn -> {
                            sortMode = sortMode.next();
                            preserveScrollOnReload = false;
                            loadScreenshots();
                        })
                .bounds(GRID_PAD_X + 66, CONTROLS_Y, SORT_W, SORT_H)
                .build();
        folderButton = Button.builder(
                        Component.translatable("better_screenshots.gallery.open_folder"),
                        btn -> openScreenshotsFolder())
                .bounds(this.width - GRID_PAD_X - FOLDER_W, CONTROLS_Y, FOLDER_W, SORT_H)
                .build();
        nameEditBox = new EditBox(font, 0, 0, 1, META_H - 2,
                Component.translatable("better_screenshots.gallery.name"));
        nameEditBox.setMaxLength(NAME_MAX_LENGTH);
        nameEditBox.setBordered(false);
        nameEditBox.setTextColor(0xFFE0E0E0);
        nameEditBox.setSuggestion("...");
        nameEditBox.setVisible(false);
        addRenderableWidget(backButton);
        addRenderableWidget(sortButton);
        addRenderableWidget(folderButton);
        addRenderableWidget(nameEditBox);
    }

    private void openScreenshotsFolder() {
        File dir = new File(Minecraft.getInstance().gameDirectory, "screenshots");
        if (!dir.exists()) dir.mkdirs();
        Util.getPlatform().openFile(dir);
    }


    private void startNameEdit(int idx) {
        if (idx < 0 || idx >= files.size()) return;
        editingNameIdx = idx;
        if (nameEditBox == null) return;
        nameEditBox.setValue("");
        nameEditBox.setFocused(true);
        nameEditBox.setCanLoseFocus(false);
        nameEditBox.moveCursorToEnd(false);
        this.setFocused(nameEditBox);
        updateNameEditBoxBounds();
    }

    private void acceptNameEdit() {
        if (editingNameIdx < 0 || editingNameIdx >= files.size() || nameEditBox == null) {
            cancelNameEdit();
            return;
        }

        String value = nameEditBox.getValue().trim();
        if (value.isEmpty()) {
            cancelNameEdit();
            return;
        }

        renameScreenshot(editingNameIdx, value);
        cancelNameEdit();
    }

    private void renameScreenshot(int idx, String requestedName) {
        if (idx < 0 || idx >= files.size()) return;

        File source = files.get(idx);
        File parentDir = source.getParentFile();
        if (parentDir == null) return;

        String baseName = sanitizeScreenshotName(requestedName);
        if (baseName.isEmpty()) return;

        File target = uniqueScreenshotFile(parentDir, baseName, source);
        if (source.equals(target)) return;

        String oldUploadKey = uploadKey(source);
        try {
            Files.move(source.toPath(), target.toPath());
        } catch (Exception ignored) {
            return;
        }

        files.set(idx, target);

        ThumbUploadOverlay overlay = thumbUploadStates.remove(oldUploadKey);
        if (overlay != null) {
            thumbUploadStates.put(uploadKey(target), overlay);
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
        } else {
            return fileName;
        }
    }

    private boolean isDefaultScreenshotName(String fileName) {
        return fileName.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}(?:_\\d+)?\\.png");
    }

    private void cancelNameEdit() {
        editingNameIdx = -1;
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

    private boolean isClickInsideNameEditor(double mouseX, double mouseY) {
        return editingNameIdx >= 0
                && nameEditBox != null
                && nameEditBox.isVisible()
                && mouseX >= nameEditBox.getX()
                && mouseX <= nameEditBox.getX() + nameEditBox.getWidth()
                && mouseY >= nameEditBox.getY()
                && mouseY <= nameEditBox.getY() + nameEditBox.getHeight();
    }

    private boolean updateNameEditBoxBounds() {
        if (nameEditBox == null || editingNameIdx < 0 || editingNameIdx >= files.size()) {
            if (nameEditBox != null) nameEditBox.setVisible(false);
            return false;
        }

        int topPad = screenshotsTopPad();
        int cols = cols();
        int thumbW = thumbW();
        int thumbH = thumbH();
        int tileH = tileH();
        int col = editingNameIdx % cols;
        int row = editingNameIdx / cols;
        int x = gridStartX() + col * (thumbW + THUMB_GAP);
        int y = topPad + row * (tileH + THUMB_GAP) - scrollOffset + thumbH;
        boolean visible = y >= topPad && y + META_H <= gridBottomY();

        nameEditBox.setVisible(visible);
        if (!visible) return false;

        nameEditBox.setX(x + 4);
        nameEditBox.setY(y + 3);
        nameEditBox.setWidth(Math.max(1, thumbW - EDIT_ICON_W - 8));
        nameEditBox.setHeight(META_H - 4);
        return true;
    }

    private boolean isEditIconHit(double mouseX, double mouseY, int x, int metaY, int width) {
        int iconX = x + width - EDIT_ICON_W - 3;
        return mouseX >= iconX
                && mouseX <= iconX + EDIT_ICON_W
                && mouseY >= metaY + 1
                && mouseY <= metaY + META_H - 1;
    }

    private void renderNameEditBox(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (nameEditBox != null && nameEditBox.isVisible()) {
            nameEditBox.render(context, mouseX, mouseY, delta);
        }
    }

    private void copyFile(int idx) {
        if (idx < 0 || idx >= files.size()) return;
        ScreenshotPreviewRenderer.copyFileToClipboard(files.get(idx));
    }

    private void uploadFile(int idx) {
        if (idx < 0 || idx >= files.size()) return;
        File file = files.get(idx);
        String uploadId = String.valueOf(System.nanoTime());
        beginUploadOverlay(file);
        ScreenshotUploader.uploadAsync(file, new ScreenshotUploader.Listener() {
            @Override
            public void onProgress(double progress) {
                minecraft.execute(() -> updateUploadOverlay(file, progress));
            }

            @Override
            public void onSuccess(String uploadedUrl) {
                minecraft.execute(() -> {
                    ScreenshotPreviewRenderer.registerUploadedUrl(uploadId, uploadedUrl);
                    ScreenshotUploader.copyUrlToClipboard(uploadedUrl);
                    ScreenshotUploader.showUploadSuccessToast();
                    markUploadOverlaySuccess(file);
                });
            }

            @Override
            public void onError(String error) {
                minecraft.execute(() -> {
                    ScreenshotUploader.showUploadErrorToast(error);
                    markUploadOverlayError(file);
                });
            }
        });
    }

    private void deleteFile(int idx) {
        File file = files.get(idx);
        thumbUploadStates.remove(uploadKey(file));
        if (file.delete()) {
            if (idx < thumbTextures.size()) {
                DynamicTexture t = thumbTextures.get(idx);
                if (t != null) t.close();
            }
            files.remove(idx);
            thumbIds.remove(idx);
            thumbTextures.remove(idx);
            recalcContentH();
            selectedIdx = -1;
            refreshActionButtons();
        }
    }

    public boolean mouseScrolled(double mx, double my,
                                 double hAmount, double vAmount) {
        int visibleH  = gridBottomY() - screenshotsTopPad();
        int maxScroll = Math.max(0, totalContentH - visibleH);
        scrollOffset  = Math.max(0, Math.min(
                scrollOffset - (int)(vAmount * 20), maxScroll));
        rememberScrollIfPreserving();
        return true;
    }

    @Override
    public void onClose() {
        for (DynamicTexture t : thumbTextures) if (t != null) t.close();
        minecraft.setScreen(parent);
    }

    private String uploadKey(File file) {
        return file == null ? "" : file.getAbsolutePath();
    }

    private void beginUploadOverlay(File file) {
        if (file == null) return;
        ThumbUploadOverlay overlay = new ThumbUploadOverlay();
        overlay.state = ThumbUploadState.UPLOADING;
        overlay.target = 0.04f;
        overlay.displayed = 0f;
        overlay.lastFrameMs = -1L;
        overlay.clearAtMs = 0L;
        thumbUploadStates.put(uploadKey(file), overlay);
    }

    private void updateUploadOverlay(File file, double progress) {
        if (file == null) return;
        ThumbUploadOverlay overlay = thumbUploadStates.computeIfAbsent(uploadKey(file), k -> new ThumbUploadOverlay());
        overlay.state = ThumbUploadState.UPLOADING;
        overlay.target = Math.max(0.04f, Math.min(1f, (float) progress));
        overlay.clearAtMs = 0L;
    }

    private void markUploadOverlaySuccess(File file) {
        if (file == null) return;
        ThumbUploadOverlay overlay = thumbUploadStates.computeIfAbsent(uploadKey(file), k -> new ThumbUploadOverlay());
        overlay.state = ThumbUploadState.SUCCESS;
        overlay.target = 1f;
        overlay.clearAtMs = System.currentTimeMillis() + UPLOAD_STATE_HOLD_MS;
    }

    private void markUploadOverlayError(File file) {
        if (file == null) return;
        ThumbUploadOverlay overlay = thumbUploadStates.computeIfAbsent(uploadKey(file), k -> new ThumbUploadOverlay());
        overlay.state = ThumbUploadState.ERROR;
        overlay.target = 1f;
        overlay.clearAtMs = System.currentTimeMillis() + UPLOAD_STATE_HOLD_MS;
    }

    private void renderUploadOverlay(GuiGraphics context, int x, int y, int w, int h, File file) {
        ThumbUploadOverlay overlay = thumbUploadStates.get(uploadKey(file));
        if (overlay == null) return;

        long now = System.currentTimeMillis();
        if (overlay.state != ThumbUploadState.UPLOADING && now > overlay.clearAtMs) {
            thumbUploadStates.remove(uploadKey(file));
            return;
        }

        if (overlay.lastFrameMs < 0L) {
            overlay.lastFrameMs = now;
        }
        float dt = Math.max(0f, Math.min(100f, now - overlay.lastFrameMs));
        overlay.lastFrameMs = now;
        float smoothing = 1f - (float) Math.exp(-dt / 120f);
        overlay.displayed += (overlay.target - overlay.displayed) * smoothing;
        if (Math.abs(overlay.target - overlay.displayed) < 0.001f) {
            overlay.displayed = overlay.target;
        }

        int barX = x;
        int barY = y;
        int barW = w;
        int fillW;
        int color;
        switch (overlay.state) {
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
                float p = Math.max(0.04f, Math.min(1f, overlay.displayed));
                fillW = Math.max(1, (int) (barW * p));
            }
        }

        context.fill(barX, barY, barX + barW, barY + UPLOAD_BAR_H, 0x66000000);
        context.fill(barX, barY, barX + fillW, barY + UPLOAD_BAR_H, color);
    }

    private void drawTimestampBar(
            GuiGraphics context,
            File file,
            int idx,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY) {
        context.fill(x, y, x + width, y + META_H, 0xFF3A3A3A);
        context.fill(x, y, x + width, y + 1, 0xFF505050);

        boolean editing = idx == editingNameIdx && nameEditBox != null && nameEditBox.isVisible();
        String customName = displayName(file);
        String label = customName != null ? customName : formatScreenshotTime(file.lastModified());
        int labelW = width - EDIT_ICON_W - 10;
        if (font.width(label) > labelW) {
            String clipped = label;
            while (font.width(clipped + "…") > labelW && !clipped.isEmpty()) {
                clipped = clipped.substring(0, clipped.length() - 1);
            }
            label = clipped + "…";
        }

        if (!editing) {
            context.drawString(font, Component.literal(label), x + 4, y + 4, 0xFFE0E0E0);
        }
        int iconColor = isEditIconHit(mouseX, mouseY, x, y, width) ? 0xFFFFFFFF : 0xFFBDBDBD;
        context.drawString(font, Component.literal("✎"), x + width - EDIT_ICON_W - 1, y + 3, iconColor);
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
}
