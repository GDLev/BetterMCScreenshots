package dev.gdlev.better_screenshots.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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
    private static final Identifier ICON_COPY     = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/copy.png");
    private static final Identifier ICON_COPY_H   = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/copy_hover.png");
    private static final Identifier ICON_DELETE   = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/close.png");
    private static final Identifier ICON_DELETE_H = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/close_hover.png");

    private static final int ACT_BTN_W   = 12;
    private static final int ACT_BTN_H   = 15;
    private static final int ACT_BTN_GAP = 0; // Brak przerwy

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
    private Identifier     navOldTexId  = null;
    private DynamicTexture navOldTex    = null;

    // Arrow fade-in start timestamp
    private long imageFullyShownAt = -1;

    // Set by navigation to suppress the slide-up entrance animation
    private boolean skipEntranceAnim = false;

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

    private Identifier getTexId() {
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
        skipEntranceAnim  = false;
        if (loaded && expectedTexture != null) initStartPosition();
    }

    public void setFromHud(boolean value) { this.fromHud = value; }

    private void initStartPosition() {
        var img = expectedTexture;
        if (img == null || img.getPixels() == null) return;

        int   targetW = this.width  - MARGIN * 2;
        int   targetH = this.height - MARGIN * 2;
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
        closing    = true;
        closeStart = System.currentTimeMillis();
    }

    // ── Navigation & File Management ──────────────────────────────────────────

    private boolean hasPrev() { return currentFileIndex > 0; }
    private boolean hasNext() {
        return currentFileIndex >= 0 && currentFileIndex < screenshotFiles.size() - 1;
    }

    private void navigateTo(int direction) {
        if (navLoading) return;
        int newIndex = currentFileIndex + direction;
        if (newIndex < 0 || newIndex >= screenshotFiles.size()) return;

        boolean useAnim = ScreenshotConfig.get().uiAnimationsEnabled();
        if (useAnim) {
            navOldTexId  = getTexId();
            navOldTex    = expectedTexture;
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
                });
            } catch (Exception e) {
                e.printStackTrace();
                mc.execute(() -> { navLoading = false; navAnimStart = -1; });
            }
        });
    }

    private void deleteCurrent() {
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
                });
            } catch (Exception e) {
                e.printStackTrace();
                mc.execute(() -> { navLoading = false; });
            }
        });
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private void drawHintText(GuiGraphicsExtractor context) {
        context.centeredText(font,
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

    private void drawActionButtons(GuiGraphicsExtractor context,
                                   int imgX, int imgY, int imgW, int imgH,
                                   double mouseX, double mouseY) {
        if (screenshotFiles.isEmpty() || currentFileIndex < 0) return;
        float alpha = arrowAlpha();
        if (alpha <= 0f) return;

        // Łączna szerokość to suma szerokości obu przycisków
        int totalBtnsW = 2 * ACT_BTN_W;
        int btnsStartX = imgX + imgW - totalBtnsW - 8; // 8px marginesu od krawędzi zdjęcia
        int btnsY      = imgY + 8;

        // Ikona Copy
        int copyX = btnsStartX;
        boolean copyHov = mouseX >= copyX && mouseX <= copyX + ACT_BTN_W
                && mouseY >= btnsY && mouseY <= btnsY + ACT_BTN_H;

        context.blit(RenderPipelines.GUI_TEXTURED, copyHov ? ICON_COPY_H : ICON_COPY,
                copyX, btnsY, 0f, 0f, ACT_BTN_W, ACT_BTN_H, ACT_BTN_W, ACT_BTN_H);

        // Ikona Delete (od razu za Copy, bez przerwy)
        int delX = btnsStartX + ACT_BTN_W;
        boolean delHov = mouseX >= delX && mouseX <= delX + ACT_BTN_W
                && mouseY >= btnsY && mouseY <= btnsY + ACT_BTN_H;

        context.blit(RenderPipelines.GUI_TEXTURED, delHov ? ICON_DELETE_H : ICON_DELETE,
                delX, btnsY, 0f, 0f, ACT_BTN_W, ACT_BTN_H, ACT_BTN_W, ACT_BTN_H);
    }

    private void drawArrows(GuiGraphicsExtractor context,
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

    private void drawArrowShape(GuiGraphicsExtractor context,
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
    public void extractBackground(@NonNull GuiGraphicsExtractor context,
                                  int mouseX, int mouseY, float delta) {
        if (parent != null) {
            parent.extractBackground(context, mouseX, mouseY, delta);
            parent.extractRenderState(context, mouseX, mouseY, delta);
        } else {
            // Render the captured game background when opened from chat
            context.blit(RenderPipelines.GUI_TEXTURED,
                    ScreenshotPreviewRenderer.BACKGROUND_ID,
                    0, 0, 0f, 0f, this.width, this.height, this.width, this.height);
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor context,
                                   int mouseX, int mouseY, float delta) {
        long    now     = System.currentTimeMillis();
        boolean useAnim = ScreenshotConfig.get().uiAnimationsEnabled();

        // ── Background dim ────────────────────────────────────────────────────
        int bgAlpha;
        if (closing && closeStart >= 0) {
            long  exitElapsed = now - closeStart;
            long  totalMs     = EXIT_DURATION_MS + BLUR_FADE_MS;
            if (!useAnim || exitElapsed > totalMs) {
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

            DynamicTexture refTex = navOldTex != null
                    ? navOldTex : expectedTexture;
            if (refTex == null) {
                drawLoadingSpinner(context);
                return;
            }
            int   tW      = this.width  - MARGIN * 2;
            int   tH      = this.height - MARGIN * 2;
            float sc      = Math.min(
                    (float) tW / refTex.getPixels().getWidth(),
                    (float) tH / refTex.getPixels().getHeight());
            int   targetW = (int)(refTex.getPixels().getWidth()  * sc);
            int   targetH = (int)(refTex.getPixels().getHeight() * sc);
            int   targetX = (this.width  - targetW) / 2;
            int   targetY = (this.height - targetH) / 2;

            int oldOffsetX = (int)(-navDirection * slideAmt * easedT);
            int oldAlphaI  = (int)((1f - easedT) * 255);
            if (navOldTexId != null && navOldTex != null) {
                context.blit(RenderPipelines.GUI_TEXTURED, navOldTexId,
                        targetX + oldOffsetX, targetY,
                        0f, 0f, targetW, targetH, targetW, targetH,
                        (oldAlphaI << 24) | 0x00FFFFFF);
            }

            if (loaded && expectedTexture != null) {
                int newOffsetX = (int)(navDirection * slideAmt * (1f - easedT));
                int newAlphaI  = (int)(easedT * 255);
                context.blit(RenderPipelines.GUI_TEXTURED, getTexId(),
                        targetX + newOffsetX, targetY,
                        0f, 0f, targetW, targetH, targetW, targetH,
                        (newAlphaI << 24) | 0x00FFFFFF);
            }

            if (!useAnim || navT >= 1f) {
                navAnimStart      = -1;
                navOldTexId       = null;
                navOldTex         = null;
                imageFullyShownAt = now;
                openedAt          = now;
            }

            drawHintText(context);
            super.extractRenderState(context, mouseX, mouseY, delta);
            return;
        }

        // ── No transition: normal loading or not-yet-loaded ───────────────────
        if (!loaded || expectedTexture == null) {
            drawLoadingSpinner(context);
            return;
        }

        // ── Compute target rect ───────────────────────────────────────────────
        var   img     = expectedTexture;
        int   tW      = this.width  - MARGIN * 2;
        int   tH      = this.height - MARGIN * 2;
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
                context.blit(RenderPipelines.GUI_TEXTURED, getTexId(),
                        lastCurX, imgY, 0f, 0f, lastCurW, lastCurH, lastCurW, lastCurH,
                        (imgAlphaI << 24) | 0x00FFFFFF);
            }
            return;
        }

        // ── First-open entrance animation ─────────────────────────────────────
        if (skipEntranceAnim) {
            if (openedAt < 0) openedAt = now;
            lastCurX = targetX; lastCurY = targetY;
            lastCurW = targetW; lastCurH = targetH;
            if (imageFullyShownAt < 0) imageFullyShownAt = now;

            context.blit(RenderPipelines.GUI_TEXTURED, getTexId(),
                    targetX, targetY, 0f, 0f, targetW, targetH, targetW, targetH);
            drawArrows(context, targetX, targetY, targetW, targetH, mouseX, mouseY);
            drawActionButtons(context, targetX, targetY, targetW, targetH, mouseX, mouseY);
            drawHintText(context);

            super.extractRenderState(context, mouseX, mouseY, delta);
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

        context.blit(RenderPipelines.GUI_TEXTURED, getTexId(),
                curX, curY, 0f, 0f, curW, curH, curW, curH);

        if (rawProgress >= 1.0f) {
            if (imageFullyShownAt < 0) imageFullyShownAt = now;
            drawArrows(context, targetX, targetY, targetW, targetH, mouseX, mouseY);
            drawActionButtons(context, targetX, targetY, targetW, targetH, mouseX, mouseY);
            drawHintText(context);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    // ── Loading spinner ───────────────────────────────────────────────────────

    private void drawLoadingSpinner(GuiGraphicsExtractor context) {
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
        context.centeredText(font,
                Component.translatable("better_screenshots.fullscreen.loading"),
                cx, cy + radius + 8, 0x88FFFFFF);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public void mouseMoved(double mouseX, double mouseY) {}

    public boolean handleNavClick(double mouseX, double mouseY) {
        if (!loaded || expectedTexture == null || expectedTexture.getPixels() == null) return false;
        if (closing || navLoading) return false;
        if (screenshotFiles.isEmpty() || currentFileIndex < 0) return false;
        if (imageFullyShownAt < 0) return false;

        var img = expectedTexture;
        if (img == null || img.getPixels() == null) return false;

        int   tW     = this.width  - MARGIN * 2;
        int   tH     = this.height - MARGIN * 2;
        float sc     = Math.min(
                (float) tW / img.getPixels().getWidth(),
                (float) tH / img.getPixels().getHeight());
        int targetW  = (int)(img.getPixels().getWidth()  * sc);
        int targetH  = (int)(img.getPixels().getHeight() * sc);
        int targetX  = (this.width  - targetW) / 2;
        int targetY  = (this.height - targetH) / 2;

        // 1. Sprawdzanie przycisków akcji
        int totalBtnsW = 2 * ACT_BTN_W + ACT_BTN_GAP;
        int btnsStartX = targetX + targetW - totalBtnsW - 12;
        int btnsY      = targetY + 12;

        int copyX = btnsStartX;
        if (mouseX >= copyX && mouseX <= copyX + ACT_BTN_W
                && mouseY >= btnsY && mouseY <= btnsY + ACT_BTN_H) {
            ScreenshotPreviewRenderer.copyFileToClipboard(screenshotFiles.get(currentFileIndex));
            return true;
        }

        int delX = btnsStartX + ACT_BTN_W + ACT_BTN_GAP;
        if (mouseX >= delX && mouseX <= delX + ACT_BTN_W
                && mouseY >= btnsY && mouseY <= btnsY + ACT_BTN_H) {
            deleteCurrent();
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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        int key = input.key();

        if (!closing && loaded && expectedTexture != null
                && screenshotFiles.size() > 1 && currentFileIndex >= 0) {
            if (key == 263 && hasPrev()) { navigateTo(-1); return true; } // ←
            if (key == 262 && hasNext()) { navigateTo(+1); return true; } // →
        }

        if (key == 256) { // ESC
            if (!loaded || expectedTexture == null || expectedTexture.getPixels() == null) {
                this.minecraft.setScreen(parent);
            } else if (!ScreenshotConfig.get().uiAnimationsEnabled()) {
                this.minecraft.setScreen(parent);
            } else {
                startClose();
            }
            return true;
        }
        return super.keyPressed(input);
    }
}
