package dev.gdlev.better_screenshots.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ScreenshotFullscreenScreen extends Screen {

    private final Screen parent;

    private static final long  ENTER_DURATION_MS = 350;
    private static final long  EXIT_DURATION_MS  = 500;
    private static final long  BOUNCE_UP_MS      = 120;
    private static final long  BLUR_FADE_MS      = 180;
    private static final float BOUNCE_HEIGHT     = 18f;
    private static final float EXIT_DROP         = 300f;
    private static final int   MARGIN            = 32;

    private long    openedAt     = -1;
    private long    loadingStart = System.currentTimeMillis();
    private long    closeStart   = -1;
    private boolean loaded       = false;
    private boolean closing      = false;

    private int startX, startY, startW, startH;
    private DynamicTexture expectedTexture = null;
    private boolean fromHud = false;
    private boolean useFullscreenTex = false;

    private int lastCurX, lastCurY, lastCurW, lastCurH;

    public ScreenshotFullscreenScreen(Screen parent) {
        super(Component.literal(""));
        this.parent = parent;
    }

    public void markLoaded() {
        loaded             = true;
        useFullscreenTex   = true;
        expectedTexture    = ScreenshotPreviewRenderer.getFullscreenTexture();
    }

    public void useCurrentTexture() {
        loaded          = true;
        expectedTexture = ScreenshotPreviewRenderer.getPreviewTexture();
    }

    @Override
    protected void init() {
        loadingStart = System.currentTimeMillis();
        openedAt     = -1;
        closing      = false;
        closeStart   = -1;
        if (loaded && expectedTexture != null) initStartPosition();
    }

    public void setFromHud(boolean value) { this.fromHud = value; }

    private ResourceLocation getTexId() {
        return useFullscreenTex
                ? ScreenshotPreviewRenderer.FULLSCREEN_ID
                : ScreenshotPreviewRenderer.PREVIEW_ID;
    }

    private void initStartPosition() {
        var img = expectedTexture;
        if (img == null || img.getPixels() == null) return;

        int targetW = this.width  - MARGIN * 2;
        int targetH = this.height - MARGIN * 2;

        float scale = Math.min(
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

    private float easeOutCubic(float t) { return 1f - (float) Math.pow(1f - t, 3); }
    private float easeInCubic(float t)  { return t * t * t; }

    private void startClose() {
        if (closing) return;
        closing    = true;
        closeStart = System.currentTimeMillis();
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (parent != null) {
            parent.renderBackground(context, mouseX, mouseY, delta);
            parent.render(context, mouseX, mouseY, delta);
        } else {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        boolean useAnim = ScreenshotConfig.get().animations;

        // Drawing BG
        int bgAlpha;
        if (closing && closeStart >= 0) {
            long exitElapsed = now - closeStart;
            long totalMs     = EXIT_DURATION_MS + BLUR_FADE_MS;
            if (!useAnim || exitElapsed > totalMs) {
                assert this.minecraft != null;
                this.minecraft.setScreen(parent);
                return;
            }
            float totalT  = Math.min((float) exitElapsed / totalMs, 1f);
            bgAlpha = (int)(Math.max(0f, 0.8f * (1f - totalT)) * 255);
        } else {
            float bgProgress = useAnim ? Math.min((float)(now - loadingStart) / ENTER_DURATION_MS, 1.0f) : 1.0f;
            bgAlpha = (int)(0.8f * bgProgress * 255);
        }

        context.fill(0, 0, this.width, this.height, (bgAlpha << 24));

        // Preview loader
        if (!loaded || expectedTexture == null || expectedTexture.getPixels() == null) {
            drawLoadingSpinner(context);
            return;
        }

        // Animation start seq
        if (openedAt < 0) {
            initStartPosition();
            openedAt = now;
        }

        // Calculating target dimensions
        var   img   = expectedTexture;
        int   tW    = this.width  - MARGIN * 2;
        int   tH    = this.height - MARGIN * 2;
        float sc    = Math.min(
                (float) tW / img.getPixels().getWidth(),
                (float) tH / img.getPixels().getHeight());
        int targetW = (int)(img.getPixels().getWidth()  * sc);
        int targetH = (int)(img.getPixels().getHeight() * sc);
        int targetX = (this.width  - targetW) / 2;
        int targetY = (this.height - targetH) / 2;

        // Hiding Animation seq
        if (closing && closeStart >= 0) {
            long exitElapsed = now - closeStart;
            if (exitElapsed <= EXIT_DURATION_MS) {
                float overallT    = (float) exitElapsed / EXIT_DURATION_MS;
                float imgAlpha    = Math.max(0f, 1f - overallT * 1.4f);
                int   imgAlphaInt = (int)(imgAlpha * 255);

                float offsetY;
                if (exitElapsed < BOUNCE_UP_MS) {
                    offsetY = -BOUNCE_HEIGHT * easeOutCubic((float) exitElapsed / BOUNCE_UP_MS);
                } else {
                    float tDrop = (float)(exitElapsed - BOUNCE_UP_MS) / (EXIT_DURATION_MS - BOUNCE_UP_MS);
                    offsetY = -BOUNCE_HEIGHT + EXIT_DROP * easeInCubic(tDrop);
                }

                int imgY = (int)(lastCurY + offsetY);
                context.blit(RenderPipelines.GUI_TEXTURED, getTexId(),
                        lastCurX, imgY, 0f, 0f, lastCurW, lastCurH, lastCurW, lastCurH);

                if (imgAlphaInt < 255) {
                    context.fill(lastCurX, imgY, lastCurX + lastCurW, imgY + lastCurH,
                            ((255 - imgAlphaInt) << 24));
                }
            }
            return;
        }

        // Entrance Animation seq
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
            context.drawCenteredString(font,
                    Component.translatable("better_screenshots.fullscreen.hint"),
                    this.width / 2, this.height - 16, 0x66FFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

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

    @Override
    public void mouseMoved(double mouseX, double mouseY) {}

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        if (input.key() == 256) {
            if (!loaded || expectedTexture == null || expectedTexture.getPixels() == null) {
                assert this.minecraft != null;
                this.minecraft.setScreen(parent);
            } else {
                if (!ScreenshotConfig.get().animations) {
                    assert this.minecraft != null;
                    this.minecraft.setScreen(parent);
                } else {
                    startClose();
                }
            }
            return true;
        }
        return super.keyPressed(input);
    }
}