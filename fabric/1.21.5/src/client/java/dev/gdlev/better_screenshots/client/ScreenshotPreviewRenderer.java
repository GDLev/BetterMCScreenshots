package dev.gdlev.better_screenshots.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;

public class ScreenshotPreviewRenderer {

    private static DynamicTexture previewTexture;
    public static final ResourceLocation PREVIEW_ID =
            ResourceLocation.fromNamespaceAndPath("better_screenshots", "screenshot_preview");

    private static DynamicTexture fullscreenTexture;
    public static final ResourceLocation FULLSCREEN_ID =
            ResourceLocation.fromNamespaceAndPath("better_screenshots", "screenshot_fullscreen");

    private static DynamicTexture oldFullscreenTexture;
    public static final ResourceLocation OLD_FULLSCREEN_ID =
            ResourceLocation.fromNamespaceAndPath("better_screenshots", "screenshot_fullscreen_old");

    public static DynamicTexture getFullscreenTexture() { return fullscreenTexture; }
    public static DynamicTexture getOldFullscreenTexture() { return oldFullscreenTexture; }

    private static DynamicTexture backgroundTexture;
    public static final ResourceLocation BACKGROUND_ID =
            ResourceLocation.fromNamespaceAndPath("better_screenshots", "screenshot_background");

    private static final java.util.Queue<DynamicTexture> pendingClose =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    public static void flushPendingClose() {
        DynamicTexture t;
        while ((t = pendingClose.poll()) != null) {
            t.close();
        }
    }

    public static void deferClose(DynamicTexture tex) {
        if (tex == null) return;
        pendingClose.add(tex);
    }

    public static void captureBackground(Runnable onReady) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> mc.execute(() -> {
            deferClose(backgroundTexture);
            backgroundTexture = new DynamicTexture(() -> "screenshot_background", image);
            mc.getTextureManager().register(BACKGROUND_ID, backgroundTexture);
            if (onReady != null) onReady.run();
        }));
    }

    public static void setFullscreenTexture(NativeImage image) {
        Minecraft mc = Minecraft.getInstance();
        deferClose(fullscreenTexture);
        fullscreenTexture = new DynamicTexture(() -> "screenshot_fullscreen", image);
        mc.getTextureManager().register(FULLSCREEN_ID, fullscreenTexture);
    }

    public static void setOldFullscreenTexture(NativeImage image) {
        Minecraft mc = Minecraft.getInstance();
        deferClose(oldFullscreenTexture);
        oldFullscreenTexture = new DynamicTexture(() -> "screenshot_fullscreen_old", image);
        mc.getTextureManager().register(OLD_FULLSCREEN_ID, oldFullscreenTexture);
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

    private static final ResourceLocation ICON_SHOW    = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/show.png");
    private static final ResourceLocation ICON_SHOW_H  = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/show_hover.png");
    private static final ResourceLocation ICON_COPY    = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/copy.png");
    private static final ResourceLocation ICON_COPY_H  = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/copy_hover.png");
    private static final ResourceLocation ICON_CLOSE   = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/close.png");
    private static final ResourceLocation ICON_CLOSE_H = ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/close_hover.png");

    private static int       hoveredButton = -1;
    private static final int[] btnX        = new int[3];
    private static final int[] btnY        = new int[3];

    public static DynamicTexture getPreviewTexture() { return previewTexture; }

    private static final ConcurrentHashMap<String, File> pendingFiles
            = new ConcurrentHashMap<>();

    public static void registerFile(String id, File file) {
        pendingFiles.put(id, file);
    }

    public static void loadAndPreview(String id, ScreenshotFullscreenScreen screen) {
        File file = pendingFiles.get(id);
        if (file == null || !file.exists()) return;
        Minecraft mc = Minecraft.getInstance();
        Thread.ofVirtual().start(() -> {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                mc.execute(() -> {
                    setFullscreenTexture(img);
                    screen.markLoaded();
                });
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    public static void copyFile(String id) {
        File file = pendingFiles.get(id);
        if (file == null || !file.exists()) return;
        copyFileToClipboard(file);
    }

    public static void copyFileToClipboard(File file) {
        if (file == null || !file.exists()) return;
        Minecraft mc = Minecraft.getInstance();
        Thread.ofVirtual().start(() -> {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                mc.execute(() -> {
                    try {
                        File tmp = File.createTempFile("better_screenshots_", ".png");
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
        Minecraft.getInstance().execute(() -> {
            deferClose(previewTexture);
            previewTexture = new DynamicTexture(() -> "screenshot_preview", image);
            Minecraft.getInstance().getTextureManager()
                    .register(PREVIEW_ID, previewTexture);
            showFrom       = System.currentTimeMillis();
            showUntil      = showFrom + (ScreenshotConfig.get().previewDurationSeconds * 1000L);
            flashStart     = ScreenshotConfig.get().previewAnimationsEnabled() ? showFrom : -1;
            closeStart     = -1;
            copyFlashStart = -1;
            hoveredButton  = -1;
        });
    }

    public static void close() {
        if (closeStart == -1) closeStart = System.currentTimeMillis();
    }

    private static float easeOutCubic(float t) { return 1f - (float) Math.pow(1f - t, 3); }
    private static float easeInCubic(float t)  { return t * t * t; }
    private static float easeOutQuad(float t)  { return 1f - (1f - t) * (1f - t); }

    public static void render(GuiGraphics context) {
        long now = System.currentTimeMillis();
        if (showFrom == -1) return;
        if (previewTexture == null || previewTexture.getPixels() == null) return;

        Minecraft mc = Minecraft.getInstance();
        ScreenshotConfig cfg = ScreenshotConfig.get();

        int screenW    = mc.getWindow().getGuiScaledWidth();
        int screenH    = mc.getWindow().getGuiScaledHeight();
        int baseWidth  = screenW / 4;
        int baseHeight = (baseWidth * previewTexture.getPixels().getHeight())
                / previewTexture.getPixels().getWidth();
        int margin     = 10;

        int baseX = switch (cfg.corner) {
            case BOTTOM_RIGHT, TOP_RIGHT -> screenW - baseWidth - margin;
            case BOTTOM_LEFT,  TOP_LEFT  -> margin;
        };
        int baseY = switch (cfg.corner) {
            case BOTTOM_RIGHT, BOTTOM_LEFT -> screenH - baseHeight - margin;
            case TOP_RIGHT,    TOP_LEFT    -> margin;
        };

        float alpha   = 1f;
        float scale   = 1f;
        float offsetY = 0f;
        float offsetX = 0f;

        long elapsed   = now - showFrom;
        long remaining = showUntil - now;

        if (closeStart != -1) {
            long ce = now - closeStart;
            if (!cfg.previewAnimationsEnabled() || ce > CLOSE_DURATION_MS) {
                showUntil = -1; showFrom = -1; closeStart = -1;
                flashStart = -1; copyFlashStart = -1;
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

        // Frame
        context.fill(drawX - 1, drawY - 1,
                drawX + drawWidth + 1, drawY + drawHeight + 1,
                (alphaInt << 24));

        // Image
        int argb = ((int)(alpha * 255) << 24) | 0xFFFFFF;
        context.blit(
                RenderType::guiTextured,
                PREVIEW_ID,
                drawX, drawY,
                0f, 0f,
                drawWidth, drawHeight,
                drawWidth, drawHeight,
                drawWidth, drawHeight,
                argb
        );

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

        // Action buttons
        int totalBtnsW = 3 * BTN_W + 2 * BTN_GAP;
        int btnsStartX = drawX + drawWidth - totalBtnsW - 2;
        int btnsY      = drawY + 2;

        double mouseX = mc.mouseHandler.xpos() / mc.getWindow().getGuiScale();
        double mouseY = mc.mouseHandler.ypos() / mc.getWindow().getGuiScale();
        hoveredButton = -1;

        for (int i = 0; i < 3; i++) {
            btnX[i] = btnsStartX + i * (BTN_W + BTN_GAP);
            btnY[i] = btnsY;
            if (mouseX >= btnX[i] && mouseX <= btnX[i] + BTN_W
                    && mouseY >= btnY[i] && mouseY <= btnY[i] + BTN_H) {
                hoveredButton = i;
            }
        }

        ResourceLocation[] icons = {
                hoveredButton == 0 ? ICON_SHOW_H  : ICON_SHOW,
                hoveredButton == 1 ? ICON_COPY_H  : ICON_COPY,
                hoveredButton == 2 ? ICON_CLOSE_H : ICON_CLOSE,
        };
        for (int i = 0; i < 3; i++) {
            context.blit(RenderType::guiTextured, icons[i],
                    btnX[i], btnY[i], 0f, 0f,
                    BTN_W, BTN_H, BTN_W, BTN_H, 0xFFFFFFFF);
        }
    }

    public static boolean handleClick(double mouseX, double mouseY) {
        if (showFrom == -1) return false;
        if (closeStart != -1) return false;
        if (showUntil != -1 && System.currentTimeMillis() > showUntil) return false;

        for (int i = 0; i < 3; i++) {
            if (btnX[i] == 0 && btnY[i] == 0) continue;
            if (mouseX >= btnX[i] && mouseX <= btnX[i] + BTN_W
                    && mouseY >= btnY[i] && mouseY <= btnY[i] + BTN_H) {
                switch (i) {
                    case 0 -> openFullscreen();
                    case 1 -> copyToClipboard();
                    case 2 -> close();
                }
                return true;
            }
        }
        return false;
    }

    private static void copyToClipboard() {
        if (previewTexture == null || previewTexture.getPixels() == null) return;
        try {
            NativeImage img = previewTexture.getPixels();
            File tmp = File.createTempFile("better_screenshots_", ".png");
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

                File dir = new File(mc.gameDirectory, "screenshots");
                File[] found = dir.listFiles(
                        f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));
                File currentFile = null;
                if (found != null && found.length > 0) {
                    java.util.Arrays.sort(found,
                            java.util.Comparator.comparingLong(File::lastModified).reversed());
                    currentFile = found[0];
                }
                screen.initNavigationFromScreenshotsDir(currentFile);

                captureBackground(() -> mc.setScreen(screen));
            });
        }
    }
}
