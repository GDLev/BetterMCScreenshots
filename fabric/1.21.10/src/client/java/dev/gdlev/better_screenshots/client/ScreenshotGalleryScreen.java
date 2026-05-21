package dev.gdlev.better_screenshots.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ScreenshotGalleryScreen extends Screen {

    private final Screen parent;

    private static final int COLS       = 4;
    private static final int THUMB_W    = 80;
    private static final int THUMB_H    = 45;
    private static final int THUMB_GAP  = 6;
    private static final int TOP_PAD    = 30;
    private static final int BOTTOM_PAD = 36;

    private final List<File>                     files         = new ArrayList<>();
    private final List<ResourceLocation>               thumbIds      = new ArrayList<>();
    private final List<DynamicTexture> thumbTextures = new ArrayList<>();

    private int   scrollOffset   = 0;
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

    private float marqueeOffset    = 0f;
    private long  marqueeLastMs    = -1;
    private int   marqueeIdx       = -1;
    private int   marqueePauseTimer = 60;
    private static final float MARQUEE_SPEED = 30f;
    private static final int   MARQUEE_PAUSE = 60;

    private boolean pendingExternalRefresh = false;
    private int loadSessionId = 0;
    private final Map<String, ThumbUploadOverlay> thumbUploadStates = new HashMap<>();

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
        addRenderableWidget(Button.builder(
                        Component.translatable("better_screenshots.gallery.back"),
                        btn -> minecraft.setScreen(parent))
                .bounds(8, 4, 60, 14)
                .build());

        if (pendingExternalRefresh) {
            pendingExternalRefresh = false;
            loadScreenshots();
        } else if (files.isEmpty() && thumbIds.isEmpty()) {
            loadScreenshots();
        }
    }

    private static final int MAX_CONCURRENT_LOADS = 4;
    private final java.util.concurrent.Semaphore loadSemaphore =
            new java.util.concurrent.Semaphore(MAX_CONCURRENT_LOADS);

    private void loadScreenshots() {
        loadSessionId++;
        final int currentSession = loadSessionId;

        for (DynamicTexture t : thumbTextures) ScreenshotPreviewRenderer.deferClose(t);
        thumbTextures.clear();
        thumbIds.clear();
        files.clear();
        selectedIdx  = -1;
        scrollOffset = 0;
        thumbUploadStates.clear();

        Minecraft mc = Minecraft.getInstance();
        File dir = new File(mc.gameDirectory, "screenshots");
        if (!dir.exists()) return;

        File[] found = dir.listFiles(
                f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));
        if (found == null) return;

        Arrays.sort(found, Comparator.comparingLong(File::lastModified).reversed());
        Collections.addAll(files, found);

        for (int i = 0; i < files.size(); i++) {
            thumbIds.add(ResourceLocation.fromNamespaceAndPath("better_screenshots",
                    "gal_thumb_" + i + "_" + files.get(i).lastModified()));
            thumbTextures.add(null);
        }

        int priority = Math.min(files.size(), 8);
        for (int i = 0; i < priority; i++) {
            final int idx = i;
            Thread.ofVirtual().start(() -> loadThumbLimited(mc, idx, currentSession));
        }
        for (int i = priority; i < files.size(); i++) {
            final int idx = i;
            Thread.ofVirtual().start(() -> {
                try { Thread.sleep(idx * 5L); } catch (Exception ignored) {}
                loadThumbLimited(mc, idx, currentSession);
            });
        }

        recalcContentH();
        refreshActionButtons();
    }

    public void refreshAfterExternalChange() {
        pendingExternalRefresh = true;
    }

    private void loadThumbLimited(Minecraft mc, int idx, int session) {
        try {
            loadSemaphore.acquire();
            if (session == loadSessionId) {
                loadThumb(mc, idx, session);
            }
        } catch (InterruptedException ignored) {
        } finally {
            loadSemaphore.release();
        }
    }
    private void recalcContentH() {
        int rows = (int) Math.ceil((double) files.size() / COLS);
        totalContentH = rows * (THUMB_H + THUMB_GAP);
    }

    private void loadThumb(Minecraft mc, int idx, int session) {
        try (InputStream is = Files.newInputStream(files.get(idx).toPath())) {
            NativeImage img   = NativeImage.read(is);
            NativeImage thumb = scaleTo(img);
            img.close();
            mc.execute(() -> {
                if (session != loadSessionId || idx >= thumbTextures.size()) {
                    thumb.close();
                    return;
                }
                DynamicTexture tex = new DynamicTexture(() -> "gal_" + idx, thumb);
                mc.getTextureManager().register(thumbIds.get(idx), tex);
                thumbTextures.set(idx, tex);
            });
        } catch (Exception ignored) {}
    }

    private NativeImage scaleTo(NativeImage src) {
        float scale = Math.min((float) ScreenshotGalleryScreen.THUMB_W / src.getWidth(),
                (float) ScreenshotGalleryScreen.THUMB_H / src.getHeight());
        int sw = Math.max(1, (int)(src.getWidth()  * scale));
        int sh = Math.max(1, (int)(src.getHeight() * scale));
        int ox = (ScreenshotGalleryScreen.THUMB_W - sw) / 2;
        int oy = (ScreenshotGalleryScreen.THUMB_H - sh) / 2;

        float scaleX = (float) src.getWidth()  / sw;
        float scaleY = (float) src.getHeight() / sh;

        NativeImage dst = new NativeImage(ScreenshotGalleryScreen.THUMB_W, ScreenshotGalleryScreen.THUMB_H, false);

        for (int y = 0; y < ScreenshotGalleryScreen.THUMB_H; y++)
            for (int x = 0; x < ScreenshotGalleryScreen.THUMB_W; x++)
                dst.setPixel(x, y, 0xFF000000);

        for (int y = 0; y < sh; y++) {
            int sy = Math.min((int)(y * scaleY), src.getHeight() - 1);
            for (int x = 0; x < sw; x++) {
                int sx   = Math.min((int)(x * scaleX), src.getWidth() - 1);
                int pixel = src.getPixel(sx, sy);
                dst.setPixel(ox + x, oy + y, pixel);
            }
        }
        return dst;
    }

    // Layout

    private int gridW()      { return COLS * THUMB_W + (COLS - 1) * THUMB_GAP; }
    private int gridStartX() { return (this.width - gridW()) / 2; }
    private int gridBottomY(){ return this.height - BOTTOM_PAD; }
    private int screenshotsTopPad() {
        return TOP_PAD + ScreenshotConfig.get().screenshotsFirstRowTopMargin;
    }

    // Click handling

    private void playActionButtonClickSound() {
        if (minecraft == null || minecraft.getSoundManager() == null) return;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 2.0f));
    }

    public boolean handleClick(int button, double mouseX, double mouseY) {
        if (button != 0) return false;
        int topPad = screenshotsTopPad();

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
            int visibleButtons = showUploadAction ? 4 : 3;
            for (int i = 0; i < visibleButtons; i++) {
                if (mouseX >= actionBtnX[i] && mouseX <= actionBtnX[i] + ACT_BTN_W
                        && mouseY >= actionBtnY[i] && mouseY <= actionBtnY[i] + ACT_BTN_H) {
                    if (showUploadAction) {
                        playActionButtonClickSound();
                        switch (i) {
                            case 0 -> openFullscreen(selectedIdx);
                            case 1 -> copyFile(selectedIdx);
                            case 2 -> uploadFile(selectedIdx);
                            case 3 -> deleteFile(selectedIdx);
                        }
                    } else {
                        playActionButtonClickSound();
                        switch (i) {
                            case 0 -> openFullscreen(selectedIdx);
                            case 1 -> copyFile(selectedIdx);
                            case 2 -> deleteFile(selectedIdx);
                        }
                    }
                    return true;
                }
            }
        }

        int sx = gridStartX();

        for (int i = 0; i < files.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x   = sx + col * (THUMB_W + THUMB_GAP);
            int y = topPad + row * (THUMB_H + THUMB_GAP) - scrollOffset;

            if (mouseX >= x && mouseX <= x + THUMB_W
                    && mouseY >= y && mouseY <= y + THUMB_H
                    && mouseY >= topPad && mouseY <= bottomY) {
                selectedIdx = (selectedIdx == i) ? -1 : i;
                marqueeIdx  = -1;
                refreshActionButtons();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent input, boolean consumed) {
        if (handleClick(input.button(), input.x(), input.y())) return true;
        return super.mouseClicked(input, consumed);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent input, double dx, double dy) {
        int topPad = screenshotsTopPad();
        if (input.button() == 0 && draggingScrollbar) {
            int bottomY  = gridBottomY();
            int visibleH = bottomY - topPad;
            int tmbH     = Math.max(16, visibleH * visibleH / totalContentH);
            int maxSc    = Math.max(0, totalContentH - visibleH);
            updateScrollFromThumb(input.y() - scrollbarDragOffsetY, tmbH, visibleH, maxSc);
            return true;
        }
        return super.mouseDragged(input, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent input) {
        if (input.button() == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(input);
    }

    private void updateScrollFromThumb(double thumbTopY, int thumbH, int visibleH, int maxScroll) {
        if (maxScroll <= 0) { scrollOffset = 0; return; }
        int topPad = screenshotsTopPad();
        int travel = Math.max(1, visibleH - thumbH);
        double clamped = Math.max(topPad, Math.min(topPad + travel, thumbTopY));
        double ratio = (clamped - topPad) / travel;
        scrollOffset = (int) Math.round(ratio * maxScroll);
    }

    // Render

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int topPad = screenshotsTopPad();
        context.fill(0, 0, this.width, this.height, 0xFF111111);

        context.drawCenteredString(font,
                title, this.width / 2, 6, 0xFFFFFF);

        int sx      = gridStartX();
        int bottomY = gridBottomY();

        // Scissor net
        context.enableScissor(0, topPad - 2, this.width, bottomY);

        for (int i = 0; i < files.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x   = sx + col * (THUMB_W + THUMB_GAP);
            int y   = topPad + row * (THUMB_H + THUMB_GAP) - scrollOffset;

            if (y + THUMB_H < topPad || y > bottomY) continue;

            boolean hov = mouseX >= x && mouseX <= x + THUMB_W
                    && mouseY >= y && mouseY <= y + THUMB_H
                    && mouseY >= topPad && mouseY <= bottomY;
            boolean sel = (i == selectedIdx);

            int border = sel ? 0xFFFFFFFF : hov ? 0xFFAAAAAA : 0xFF555555;
            context.fill(x - 1, y - 1, x + THUMB_W + 1, y + THUMB_H + 1, border);

            if (i < thumbTextures.size() && thumbTextures.get(i) != null) {
                context.blit(RenderPipelines.GUI_TEXTURED, 
                        thumbIds.get(i),
                        x, y, 0f, 0f, THUMB_W, THUMB_H, THUMB_W, THUMB_H);
            } else {
                context.fill(x, y, x + THUMB_W, y + THUMB_H, 0xFF2a2a2a);
                context.drawCenteredString(font,
                        Component.translatable("better_screenshots.gallery.loading"),
                        x + THUMB_W / 2, y + THUMB_H / 2 - 4, 0xFF555555);
            }

            // Copy animation
            if (sel) {
                int ca = ScreenshotPreviewRenderer.getCopyFlashAlpha();
                if (ca > 0) context.fill(x, y, x + THUMB_W, y + THUMB_H, (ca << 24) | 0x004499FF);
            }

            if (i < files.size()) {
                renderUploadOverlay(context, x, y, THUMB_W, THUMB_H, files.get(i));
            }

            // Name display on hover
            if (hov && y >= topPad && y + THUMB_H <= bottomY) {
                context.disableScissor();
                context.enableScissor(x, y, x + THUMB_W, y + THUMB_H);

                String name = files.get(i).getName().replace(".png", "");
                int nameW   = font.width(name);
                int labelH  = 12;
                int labelY  = y + THUMB_H - labelH;

                context.fill(x, labelY, x + THUMB_W, y + THUMB_H, 0xCC000000);

                if (nameW <= THUMB_W - 4) {
                    context.drawCenteredString(font,
                            Component.literal(name), x + THUMB_W / 2, labelY + 2, 0xFFFFFFFF);
                } else {
                    String clipped = name;
                    while (font.width(clipped + "…") > THUMB_W - 4
                            && !clipped.isEmpty()) {
                        clipped = clipped.substring(0, clipped.length() - 1);
                    }
                    context.drawCenteredString(font,
                            Component.literal(clipped + "…"), x + THUMB_W / 2, labelY + 2, 0xFFFFFFFF);
                }

                context.disableScissor();
                context.enableScissor(0, topPad - 2, this.width, bottomY);
            }
        }

        context.disableScissor();

        // Info panel
        if (selectedIdx >= 0 && selectedIdx < files.size()) {
            drawSelectedPanel(context);
        } else {
            context.drawCenteredString(font,
                    Component.translatable("better_screenshots.gallery.tip"),
                    this.width / 2, this.height - BOTTOM_PAD + 12, 0xFF555555);
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

        super.render(context, mouseX, mouseY, delta);

    }

    private void drawSelectedPanel(GuiGraphics context) {
        if (selectedIdx < 0 || selectedIdx >= files.size()) return;
        int topPad = screenshotsTopPad();

        int col    = selectedIdx % COLS;
        int row    = selectedIdx / COLS;
        int sx     = gridStartX();
        int thumbX = sx + col * (THUMB_W + THUMB_GAP);
        int thumbY = topPad + row * (THUMB_H + THUMB_GAP) - scrollOffset;

        boolean thumbVisible = thumbY + THUMB_H > topPad && thumbY < gridBottomY();

        if (thumbVisible) {
            // Keep the action buttons clipped to the thumbnails container
            int bottomY = gridBottomY();
            context.enableScissor(0, topPad, this.width, bottomY);

            // Action buttons
            boolean showUploadAction = ScreenshotUploader.isUploaderEnabled();
            int visibleButtons = showUploadAction ? 4 : 3;
            int totalBtnsW = visibleButtons * ACT_BTN_W + Math.max(0, visibleButtons - 1) * ACT_BTN_GAP;
            int btnsStartX = thumbX + THUMB_W - totalBtnsW - 2;
            int btnsY      = thumbY + 2;

            Minecraft mc = Minecraft.getInstance();
            double mouseX = mc.mouseHandler.xpos() * this.width / mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos() * this.height / mc.getWindow().getScreenHeight();

            ResourceLocation[] icons = showUploadAction
                    ? new ResourceLocation[] { ICON_SHOW, ICON_COPY, ICON_UPLOAD, ICON_DELETE }
                    : new ResourceLocation[] { ICON_SHOW, ICON_COPY, ICON_DELETE };
            ResourceLocation[] iconsH = showUploadAction
                    ? new ResourceLocation[] { ICON_SHOW_H, ICON_COPY_H, ICON_UPLOAD_H, ICON_DELETE_H }
                    : new ResourceLocation[] { ICON_SHOW_H, ICON_COPY_H, ICON_DELETE_H };

            for (int i = 0; i < visibleButtons; i++) {
                actionBtnX[i] = btnsStartX + i * (ACT_BTN_W + ACT_BTN_GAP);
                actionBtnY[i] = btnsY;

                boolean hov = mouseX >= actionBtnX[i]
                        && mouseX <= actionBtnX[i] + ACT_BTN_W
                        && mouseY >= actionBtnY[i]
                        && mouseY <= actionBtnY[i] + ACT_BTN_H;

                context.blit(RenderPipelines.GUI_TEXTURED, 
                                                hov ? iconsH[i] : icons[i],
                        actionBtnX[i], actionBtnY[i],
                        0f, 0f, ACT_BTN_W, ACT_BTN_H, ACT_BTN_W, ACT_BTN_H);
            }
            for (int i = visibleButtons; i < actionBtnX.length; i++) {
                actionBtnX[i] = -100;
                actionBtnY[i] = -100;
            }

            context.disableScissor();
        } else {
            for (int i = 0; i < actionBtnX.length; i++) {
                actionBtnX[i] = -100;
                actionBtnY[i] = -100;
            }
        }

        // File name info
        String fullName  = files.get(selectedIdx).getName().replace(".png", "");
        int    panelY    = this.height - BOTTOM_PAD;
        int    textAreaW = 220;
        int    textX     = this.width / 2 - textAreaW / 2;
        int    textY     = panelY + 5;
        int    fullW     = font.width(fullName);

        context.fill(0, panelY, this.width, panelY + BOTTOM_PAD, 0xCC000000);
        context.fill(0, panelY, this.width, panelY + 1,          0xFF444444);

        if (fullW <= textAreaW) {
            marqueeOffset     = 0;
            marqueePauseTimer = MARQUEE_PAUSE;
            context.drawCenteredString(font,
                    Component.literal(fullName), this.width / 2, textY, 0xFFCCCCCC);
        } else {
            if (!ScreenshotConfig.get().uiAnimationsEnabled()) {
                String clipped = fullName;
                while (font.width(clipped + "…") > textAreaW && !clipped.isEmpty()) {
                    clipped = clipped.substring(0, clipped.length() - 1);
                }
                marqueeOffset     = 0;
                marqueePauseTimer = MARQUEE_PAUSE;
                context.drawCenteredString(font,
                        Component.literal(clipped + "…"), this.width / 2, textY, 0xFFCCCCCC);
                return;
            }

            if (marqueeIdx != selectedIdx) {
                marqueeIdx        = selectedIdx;
                marqueeOffset     = 0f;
                marqueePauseTimer = MARQUEE_PAUSE;
                marqueeLastMs     = System.currentTimeMillis();
            }

            long  now  = System.currentTimeMillis();
            float dtMs = marqueeLastMs < 0 ? 0 : (now - marqueeLastMs);
            marqueeLastMs = now;

            if (marqueePauseTimer > 0) {
                marqueePauseTimer--;
            } else {
                marqueeOffset += MARQUEE_SPEED * (dtMs / 1000f);
                if (marqueeOffset > fullW - textAreaW + 10) {
                    marqueeOffset     = 0f;
                    marqueePauseTimer = MARQUEE_PAUSE;
                }
            }

            context.enableScissor(textX, textY - 1, textX + textAreaW, textY + 10);
            context.drawString(font, Component.literal(fullName),
                    textX - (int) marqueeOffset, textY, 0xFFCCCCCC);
            context.disableScissor();

            context.fill(textX,                  textY - 1, textX + 12,        textY + 10, 0xDD000000);
            context.fill(textX + textAreaW - 12, textY - 1, textX + textAreaW, textY + 10, 0xDD000000);
        }
    }


    private void openFullscreen(int idx) {
        Minecraft mc = Minecraft.getInstance();
        ScreenshotFullscreenScreen screen = new ScreenshotFullscreenScreen(this);
        // Provide the full file list so the fullscreen screen can navigate prev/next
        screen.setNavigationContext(files, idx);
        ScreenshotPreviewRenderer.captureBackground(() -> mc.setScreen(screen));

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
    }

    private void refreshActionButtons() {
        clearWidgets();
        addRenderableWidget(Button.builder(
                        Component.translatable("better_screenshots.gallery.back"),
                        btn -> minecraft.setScreen(parent))
                .bounds(8, 4, 60, 14)
                .build());
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
}
