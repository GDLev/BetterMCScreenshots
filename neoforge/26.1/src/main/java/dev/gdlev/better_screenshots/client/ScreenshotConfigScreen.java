package dev.gdlev.better_screenshots.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ScreenshotConfigScreen extends Screen {

    private final Screen parent;

    private static final int COL_W = 180;
    private static final int BTN_H = 20;
    private static final int GAP   = 26;
    private static final int SEP   = 20;

    private final List<Identifier>     thumbIds      = new ArrayList<>();
    private final List<DynamicTexture> thumbTextures = new ArrayList<>();
    private final List<File>           thumbFiles    = new ArrayList<>();
    private int screenshotCount = 0;

    private int scrollOffset = 0;
    private int maxScroll    = 0;
    private final List<AbstractWidget> settingsWidgets = new ArrayList<>();
    private Button doneBtn;
    private Button galleryBtn;

    private boolean draggingScrollbar = false;
    private double  scrollbarDragOffsetY = 0.0;

    private static final Identifier ICON_SHOW    = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/show.png");
    private static final Identifier ICON_SHOW_H  = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/show_hover.png");
    private static final Identifier ICON_COPY    = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/copy.png");
    private static final Identifier ICON_COPY_H  = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/copy_hover.png");
    private static final Identifier ICON_DELETE  = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/close.png");
    private static final Identifier ICON_DELETE_H= Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/close_hover.png");

    private static final int ACT_BTN_W   = 8;
    private static final int ACT_BTN_H   = 10;
    private static final int ACT_BTN_GAP = 0;

    private int selectedThumbIdx = -1;

    private boolean pendingExternalRefresh = false;

    private final int[] actionBtnX = new int[3];
    private final int[] actionBtnY = new int[3];

    public ScreenshotConfigScreen(Screen parent) {
        super(Component.translatable("better_screenshots.title"));
        this.parent = parent;
    }

    private int thumbW()     { return (COL_W - 6) / 2; }
    private int thumbH()     { return (int)(thumbW() * 9f / 16f); }
    private int thumbsH()    { return thumbH() * 2 + 4; }
    private int panelH()     { return 240; }
    private int panelY()     { return (this.height - panelH()) / 2; }
    private int panelX()     { return this.width / 2 - COL_W - SEP / 2 - 12; }
    private int panelW()     { return COL_W * 2 + SEP + 24; }
    private int leftX()      { return panelX() + 12; }
    private int rightX()     { return leftX() + COL_W + SEP; }
    private int topY()       { return panelY() + 28; }
    private int bottomBtnY() { return panelY() + panelH() - 12 - BTN_H; }

    @Override
    protected void init() {
        if (pendingExternalRefresh) {
            pendingExternalRefresh = false;
            loadThumbnails();
        } else if (thumbFiles.isEmpty()) {
            loadThumbnails();
        }

        settingsWidgets.clear();
        clearWidgets();

        int lx = leftX();
        int ty = topY();
        int by = bottomBtnY();

        // Settings

        settingsWidgets.add(addRenderableWidget(CycleButton.builder(
                        (ScreenshotConfig.Corner c) -> Component.translatable(switch (c) {
                            case BOTTOM_RIGHT -> "better_screenshots.config.corner.bottom_right";
                            case BOTTOM_LEFT  -> "better_screenshots.config.corner.bottom_left";
                            case TOP_RIGHT    -> "better_screenshots.config.corner.top_right";
                            case TOP_LEFT     -> "better_screenshots.config.corner.top_left";
                        }),
                        ScreenshotConfig.get().corner)
                .withValues(ScreenshotConfig.Corner.values())
                .create(lx, ty + 14, COL_W, BTN_H,
                        Component.translatable("better_screenshots.config.corner"),
                        (btn, val) -> { ScreenshotConfig.get().corner = val; ScreenshotConfig.save(); })));

        settingsWidgets.add(addRenderableWidget(CycleButton.builder(
                        (ScreenshotConfig.AnimationsMode val) -> Component.translatable(switch (val) {
                            case ON      -> "better_screenshots.config.animations.on";
                            case OFF     -> "better_screenshots.config.animations.off";
                            case REDUCED -> "better_screenshots.config.animations.reduced";
                        }),
                        ScreenshotConfig.get().animationsMode)
                .withValues(ScreenshotConfig.AnimationsMode.values())
                .create(lx, ty + 14 + GAP, COL_W, BTN_H,
                        Component.translatable("better_screenshots.config.animations"),
                        (btn, val) -> {
                            ScreenshotConfig.get().animationsMode = val;
                            ScreenshotConfig.get().animations = val == ScreenshotConfig.AnimationsMode.ON;
                            ScreenshotConfig.save();
                        })));

        settingsWidgets.add(addRenderableWidget(CycleButton.builder(
                        (ScreenshotConfig.ShutterSound s) -> Component.translatable(switch (s) {
                            case NONE    -> "better_screenshots.config.sound.none";
                            case SOFT    -> "better_screenshots.config.sound.soft";
                            case CLASSIC -> "better_screenshots.config.sound.classic";
                        }),
                        ScreenshotConfig.get().shutterSound)
                .withValues(ScreenshotConfig.ShutterSound.values())
                .create(lx, ty + 14 + GAP * 2, COL_W, BTN_H,
                        Component.translatable("better_screenshots.config.sound"),
                        (btn, val) -> { ScreenshotConfig.get().shutterSound = val; ScreenshotConfig.save(); })));

        settingsWidgets.add(addRenderableWidget(CycleButton.builder(
                        (ScreenshotConfig.FlashMode m) -> Component.translatable(switch (m) {
                            case PREVIEW -> "better_screenshots.config.flash.preview";
                            case SCREEN  -> "better_screenshots.config.flash.screen";
                        }),
                        ScreenshotConfig.get().flashMode)
                .withValues(ScreenshotConfig.FlashMode.values())
                .create(lx, ty + 14 + GAP * 3, COL_W, BTN_H,
                        Component.translatable("better_screenshots.config.flash"),
                        (btn, val) -> { ScreenshotConfig.get().flashMode = val; ScreenshotConfig.save(); })));

        settingsWidgets.add(addRenderableWidget(CycleButton.builder(
                        (ScreenshotConfig.ChatNotification n) -> Component.translatable(switch (n) {
                            case MODERN  -> "better_screenshots.config.chat.modern";
                            case DEFAULT -> "better_screenshots.config.chat.default";
                            case DISABLED -> "better_screenshots.config.chat.disabled";
                        }),
                        ScreenshotConfig.get().chatNotification)
                .withValues(ScreenshotConfig.ChatNotification.values())
                .create(lx, ty + 14 + GAP * 4, COL_W, BTN_H,
                        Component.translatable("better_screenshots.config.chat"),
                        (btn, val) -> { ScreenshotConfig.get().chatNotification = val; ScreenshotConfig.save(); })));

        // Preview time Slider
        double initialDuration = (ScreenshotConfig.get().previewDurationSeconds - 1.0) / 14.0;
        settingsWidgets.add(addRenderableWidget(new DurationSlider(
                lx, ty + 14 + GAP * 5, COL_W, BTN_H, initialDuration)));

        // Menu Button Position
        settingsWidgets.add(addRenderableWidget(CycleButton.builder(
                        (ScreenshotConfig.MenuButtonPosition p) -> Component.translatable(switch (p) {
                            case TOP_RIGHT    -> "better_screenshots.config.menu_button.top_right";
                            case TOP_LEFT     -> "better_screenshots.config.menu_button.top_left";
                            case BOTTOM_RIGHT -> "better_screenshots.config.menu_button.bottom_right";
                            case BOTTOM_LEFT  -> "better_screenshots.config.menu_button.bottom_left";
                            case DISABLED     -> "better_screenshots.config.menu_button.disabled";
                        }),
                        ScreenshotConfig.get().menuButtonPosition)
                .withValues(ScreenshotConfig.MenuButtonPosition.values())
                .create(lx, ty + 14 + GAP * 6, COL_W, BTN_H,
                        Component.translatable("better_screenshots.config.menu_button"),
                        (btn, val) -> { ScreenshotConfig.get().menuButtonPosition = val; ScreenshotConfig.save(); })));

        // Back
        doneBtn = addRenderableWidget(Button.builder(
                        Component.translatable("better_screenshots.config.done"),
                        btn -> minecraft.setScreen(parent))
                .bounds(lx, by, COL_W, BTN_H)
                .build());

        // Gallery
        galleryBtn = addRenderableWidget(Button.builder(
                        Component.translatable("better_screenshots.config.open_gallery"),
                        btn -> minecraft.setScreen(new ScreenshotGalleryScreen(this)))
                .bounds(rightX(), by, COL_W, BTN_H)
                .build());

        int visibleH = by - 2 - (ty + 12);
        int totalH   = 14 + settingsWidgets.size() * GAP + 4;
        maxScroll = Math.max(0, totalH - visibleH);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private void updateWidgetPositions() {
        int ty = topY();
        for (int i = 0; i < settingsWidgets.size(); i++) {
            AbstractWidget w = settingsWidgets.get(i);
            w.setY(ty + 14 + i * GAP - scrollOffset);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        updateWidgetPositions();

        int tw = thumbW();
        int th = thumbH();
        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();
        int lx = leftX();
        int rx = rightX();
        int ty = topY();
        int by = bottomBtnY();

        drawPanel(context, px, py, pw, ph);

        context.centeredText(font,
                Component.literal("✦  ").append(Component.translatable("better_screenshots.title")).append("  ✦"),
                this.width / 2, py + 8, 0xFFCCCCCC);

        context.fill(px + 8, py + 20, px + pw - 8, py + 21, 0xFF444444);

        int sepX = lx + COL_W + SEP / 2;
        context.fill(sepX, py + 22, sepX + 1, py + ph - 4, 0xFF333333);

        context.centeredText(font,
                Component.translatable("better_screenshots.config.section.settings"),
                lx + COL_W / 2, ty + 2, 0xFF777777);
        context.centeredText(font,
                Component.translatable("better_screenshots.config.section.screenshots"),
                rx + COL_W / 2, ty + 2, 0xFF777777);

        int thumbsTopY = ty + 14;

        // Action buttons reset
        Arrays.fill(actionBtnX, -100);
        Arrays.fill(actionBtnY, -100);

        for (int i = 0; i < 4; i++) {
            int col = i % 2;
            int row = i / 2;
            int tx  = rx + col * (tw + 6);
            int tty = thumbsTopY + row * (th + 4);

            boolean hov = i < thumbFiles.size() && thumbFiles.get(i) != null
                    && mouseX >= tx && mouseX <= tx + tw
                    && mouseY >= tty && mouseY <= tty + th;
            boolean sel = (i == selectedThumbIdx);
            int border  = sel ? 0xFFFFFFFF : hov ? 0xFFAAAAAA : 0xFF444444;
            context.fill(tx - 1, tty - 1, tx + tw + 1, tty + th + 1, border);

            if (i < thumbTextures.size() && thumbTextures.get(i) != null) {
                context.blit(RenderPipelines.GUI_TEXTURED, thumbIds.get(i),
                        tx, tty, 0f, 0f, tw, th, tw, th);
            } else if (i < thumbIds.size()) {
                context.fill(tx, tty, tx + tw, tty + th, 0xFF1a1a1a);
                context.centeredText(font,
                        Component.translatable("better_screenshots.gallery.loading"),
                        tx + tw / 2, tty + th / 2 - 4, 0xFF555555);
            } else {
                context.fill(tx, tty, tx + tw, tty + th, 0xFF161616);
            }

            // Nametag on hover
            if (hov) {
                context.enableScissor(tx, tty, tx + tw, tty + th);
                String name = thumbFiles.get(i).getName().replace(".png", "");
                int nameW   = font.width(name);
                int labelH  = 12;
                int labelY  = tty + th - labelH;
                context.fill(tx, labelY, tx + tw, tty + th, 0xCC000000);
                if (nameW <= tw - 4) {
                    context.centeredText(font,
                            Component.literal(name), tx + tw / 2, labelY + 2, 0xFFFFFFFF);
                } else {
                    String clipped = name;
                    while (font.width(clipped + "…") > tw - 4 && !clipped.isEmpty())
                        clipped = clipped.substring(0, clipped.length() - 1);
                    context.centeredText(font,
                            Component.literal(clipped + "…"), tx + tw / 2, labelY + 2, 0xFFFFFFFF);
                }
                context.disableScissor();
            }

            // Copy animation
            if (sel) {
                int ca = ScreenshotPreviewRenderer.getCopyFlashAlpha();
                if (ca > 0) context.fill(tx, tty, tx + tw, tty + th, (ca << 24) | 0x004499FF);
            }

            // Action buttons
            if (sel && i < thumbFiles.size() && thumbFiles.get(i) != null) {
                int totalBtnsW = 3 * ACT_BTN_W + 2 * ACT_BTN_GAP;
                int btnsStartX = tx + tw - totalBtnsW - 2;
                int btnsY      = tty + 2;

                Identifier[] icons  = { ICON_SHOW,   ICON_COPY,   ICON_DELETE   };
                Identifier[] iconsH = { ICON_SHOW_H, ICON_COPY_H, ICON_DELETE_H };

                for (int b = 0; b < 3; b++) {
                    actionBtnX[b] = btnsStartX + b * (ACT_BTN_W + ACT_BTN_GAP);
                    actionBtnY[b] = btnsY;

                    boolean btnHov = mouseX >= actionBtnX[b]
                            && mouseX <= actionBtnX[b] + ACT_BTN_W
                            && mouseY >= actionBtnY[b]
                            && mouseY <= actionBtnY[b] + ACT_BTN_H;

                    context.blit(
                            RenderPipelines.GUI_TEXTURED,
                            btnHov ? iconsH[b] : icons[b],
                            actionBtnX[b], actionBtnY[b],
                            0f, 0f, ACT_BTN_W, ACT_BTN_H, ACT_BTN_W, ACT_BTN_H);
                }
            }
        }

        Component countText = screenshotCount == 0
                ? Component.translatable("better_screenshots.gallery.no_screenshots")
                : screenshotCount == 1
                ? Component.translatable("better_screenshots.gallery.count.one")
                : Component.translatable("better_screenshots.gallery.count.many",
                String.valueOf(screenshotCount));
        context.centeredText(font,
                countText, rx + COL_W / 2, by - 10, 0xFF666666);

        // Settings with scroll and scissor
        int sTop = ty + 12;
        int sBottom = by - 2;
        int sHeight = sBottom - sTop;
        context.enableScissor(lx, sTop, lx + COL_W + 10, sBottom);
        for (int i = 0; i < settingsWidgets.size(); i++) {
            AbstractWidget w = settingsWidgets.get(i);
            w.extractRenderState(context, mouseX, mouseY, delta);
        }
        context.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int sbX = lx + COL_W + 2;
            int sbW = 2;
            int sbH = Math.max(10, sHeight * sHeight / (sHeight + maxScroll));
            int sbY = sTop + (int)((float) scrollOffset / maxScroll * (sHeight - sbH));
            context.fill(sbX, sTop, sbX + sbW, sBottom, 0x22FFFFFF);
            context.fill(sbX, sbY, sbX + sbW, sbY + sbH, 0x88FFFFFF);
        }

        // Fixed buttons
        if (doneBtn != null) doneBtn.extractRenderState(context, mouseX, mouseY, delta);
        if (galleryBtn != null) galleryBtn.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (maxScroll > 0 && mouseX >= leftX() && mouseX <= leftX() + COL_W + 10) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(vAmount * 16)));
            updateWidgetPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent input, boolean consumed) {
        updateWidgetPositions();

        double mouseX = input.x();
        double mouseY = input.y();
        int button = input.button();

        if (doneBtn != null && doneBtn.mouseClicked(input, consumed)) {
            return true;
        }
        if (galleryBtn != null && galleryBtn.mouseClicked(input, consumed)) {
            return true;
        }

        if (handleClick(button, mouseX, mouseY)) return true;

        if (button == 0 && maxScroll > 0) {
            int lx = leftX();
            int sTop = topY() + 12;
            int sBottom = bottomBtnY() - 2;
            int sHeight = sBottom - sTop;
            int sbX = lx + COL_W + 2;

            if (mouseX >= sbX - 2 && mouseX <= sbX + 6) {
                int sbH = Math.max(10, sHeight * sHeight / (sHeight + maxScroll));
                int sbY = sTop + (int)((float) scrollOffset / maxScroll * (sHeight - sbH));

                if (mouseY >= sbY && mouseY <= sbY + sbH) {
                    scrollbarDragOffsetY = mouseY - sbY;
                } else {
                    scrollbarDragOffsetY = sbH / 2.0;
                }

                draggingScrollbar = true;
                updateScrollFromThumb(mouseY - scrollbarDragOffsetY, sbH, sHeight);
                updateWidgetPositions();
                return true;
            }
        }

        if (mouseX >= leftX() && mouseX <= leftX() + COL_W
                && mouseY < bottomBtnY()) {

            int sTop = topY() + 12;
            int sBottom = bottomBtnY() - 2;

            if (mouseY >= sTop && mouseY <= sBottom) {
                for (AbstractWidget w : settingsWidgets) {
                    if (!w.visible) continue;

                    if (w.mouseClicked(input, consumed)) {
                        return true;
                    }
                }
            }

            return false;
        }

        return super.mouseClicked(input, consumed);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent input, double dx, double dy) {
        if (input.button() == 0 && draggingScrollbar) {
            int sTop = topY() + 12;
            int sBottom = bottomBtnY() - 2;
            int sHeight = sBottom - sTop;
            int sbH = Math.max(10, sHeight * sHeight / (sHeight + maxScroll));
            updateScrollFromThumb(input.y() - scrollbarDragOffsetY, sbH, sHeight);
            updateWidgetPositions();
            return true;
        }
        return super.mouseDragged(input, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent input) {
        if (input.button() == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(input);
    }

    private void updateScrollFromThumb(double thumbTopY, int thumbH, int visibleH) {
        if (maxScroll <= 0) { scrollOffset = 0; return; }
        int sTop = topY() + 12;
        int travel = Math.max(1, visibleH - thumbH);
        double clamped = Math.max(sTop, Math.min(sTop + travel, thumbTopY));
        double ratio = (clamped - sTop) / travel;
        scrollOffset = (int) Math.round(ratio * maxScroll);
    }

    public boolean handleClick(int button, double mouseX, double mouseY) {
        if (button != 0) return false;

        // Show action buttons when selected
        if (selectedThumbIdx >= 0) {
            for (int i = 0; i < 3; i++) {
                if (mouseX >= actionBtnX[i] && mouseX <= actionBtnX[i] + ACT_BTN_W
                        && mouseY >= actionBtnY[i] && mouseY <= actionBtnY[i] + ACT_BTN_H) {
                    switch (i) {
                        case 0 -> openFullscreen(selectedThumbIdx);
                        case 1 -> copyFile(selectedThumbIdx);
                        case 2 -> deleteFile(selectedThumbIdx);
                    }
                    return true;
                }
            }
        }

        int tw = thumbW();
        int th = thumbH();
        int rx = rightX();
        int thumbsTopY = topY() + 14;

        for (int i = 0; i < Math.min(4, thumbFiles.size()); i++) {
            int col = i % 2;
            int row = i / 2;
            int tx  = rx + col * (tw + 6);
            int tty = thumbsTopY + row * (th + 4);

            if (mouseX >= tx && mouseX <= tx + tw
                    && mouseY >= tty && mouseY <= tty + th) {
                selectedThumbIdx = (selectedThumbIdx == i) ? -1 : i;
                return true;
            }
        }
        return false;
    }

    // Actions

    private void openFullscreen(int idx) {
        if (idx < 0 || idx >= thumbFiles.size()) return;
        Minecraft mc = Minecraft.getInstance();
        ScreenshotFullscreenScreen screen = new ScreenshotFullscreenScreen(this);
        ScreenshotPreviewRenderer.captureBackground(() -> mc.setScreen(screen));

        Thread.ofVirtual().start(() -> {
            try {
                byte[] bytes = Files.readAllBytes(thumbFiles.get(idx).toPath());
                NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
                mc.execute(() -> {
                    ScreenshotPreviewRenderer.setFullscreenTexture(img);
                    screen.markLoaded();
                });
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void copyFile(int idx) {
        if (idx < 0 || idx >= thumbFiles.size()) return;
        ScreenshotPreviewRenderer.copyFileToClipboard(thumbFiles.get(idx));
    }

    private void deleteFile(int idx) {
        if (idx < 0 || idx >= thumbFiles.size()) return;
        File file = thumbFiles.get(idx);
        if (file.delete()) {
            screenshotCount = Math.max(0, screenshotCount - 1);
            selectedThumbIdx = -1;
            loadThumbnails();
        }
    }

    // thumbnail loading

    private void loadThumbnails() {
        for (DynamicTexture t : thumbTextures) ScreenshotPreviewRenderer.deferClose(t);
        thumbTextures.clear();
        thumbIds.clear();
        thumbFiles.clear();
        screenshotCount = 0;
        selectedThumbIdx = -1;

        File dir = new File(Minecraft.getInstance().gameDirectory, "screenshots");
        if (!dir.exists()) return;

        File[] files = dir.listFiles(
                f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));
        if (files == null || files.length == 0) return;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        screenshotCount = files.length;

        int count = Math.min(files.length, 4);
        for (int i = 0; i < count; i++) {
            thumbIds.add(Identifier.fromNamespaceAndPath("better_screenshots",
                    "cfg_thumb_" + i + "_" + files[i].lastModified()));
            thumbTextures.add(null);
            thumbFiles.add(files[i]);
        }

        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < count; i++) {
            final int  idx  = i;
            final File file = files[i];
            Thread.ofVirtual().start(() -> {
                try (InputStream is = Files.newInputStream(file.toPath())) {
                    NativeImage img   = NativeImage.read(is);
                    NativeImage thumb = scaleTo(img, thumbW(), thumbH());
                    img.close();
                    mc.execute(() -> {
                        if (idx >= thumbTextures.size()) return;
                        DynamicTexture tex =
                                new DynamicTexture(() -> "cfg_thumb_" + idx, thumb);
                        mc.getTextureManager().register(thumbIds.get(idx), tex);
                        thumbTextures.set(idx, tex);
                    });
                } catch (Exception ignored) {}
            });
        }
    }

    public void refreshAfterExternalChange() {
        pendingExternalRefresh = true;
    }

    private NativeImage scaleTo(NativeImage src, int tw, int th) {
        float scale = Math.min((float) tw / src.getWidth(),
                (float) th / src.getHeight());
        int sw = (int)(src.getWidth()  * scale);
        int sh = (int)(src.getHeight() * scale);
        int ox = (tw - sw) / 2;
        int oy = (th - sh) / 2;

        NativeImage dst = new NativeImage(tw, th, false);
        for (int y = 0; y < th; y++)
            for (int x = 0; x < tw; x++)
                dst.setPixelABGR(x, y, 0xFF000000);
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int sx   = Math.min((int)((float) x / sw * src.getWidth()),  src.getWidth()  - 1);
                int sy   = Math.min((int)((float) y / sh * src.getHeight()), src.getHeight() - 1);
                int argb = src.getPixel(sx, sy);
                int a    = (argb >> 24) & 0xFF;
                int r    = (argb >> 16) & 0xFF;
                int g    = (argb >>  8) & 0xFF;
                int b    =  argb        & 0xFF;
                dst.setPixelABGR(ox + x, oy + y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return dst;
    }

    private void drawPanel(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        ctx.fill(x,         y,         x + w,     y + h,     0xAA000000);
        ctx.fill(x,         y,         x + w,     y + 1,     0xFF555555);
        ctx.fill(x,         y + h - 1, x + w,     y + h,     0xFF555555);
        ctx.fill(x,         y,         x + 1,     y + h,     0xFF555555);
        ctx.fill(x + w - 1, y,         x + w,     y + h,     0xFF555555);
    }

    @Override
    public void onClose() {
        for (DynamicTexture t : thumbTextures) ScreenshotPreviewRenderer.deferClose(t);
        minecraft.setScreen(parent);
    }
}
