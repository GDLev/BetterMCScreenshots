package dev.gdlev.better_screenshots.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gdlev.better_screenshots.client.ScreenshotConfig;
import dev.gdlev.better_screenshots.client.ScreenshotConfigScreen;
import dev.gdlev.better_screenshots.client.ScreenshotGalleryScreen;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class OptionsScreenMixin extends Screen {

    @Unique
    private static final int BTN_SIZE = 20;
    @Unique
    private static final int GAP = 4;
    @Unique
    private static final int ICON_SIZE = 16;

    @Unique
    private SpriteIconButton cameraButton = null;
    @Unique
    private SpriteIconButton galleryButton = null;
    @Unique
    private SpriteIconButton settingsMenu = null;

    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        ScreenshotConfig.MenuButtonPosition pos = ScreenshotConfig.get().menuButtonPosition;
        if (pos == ScreenshotConfig.MenuButtonPosition.DISABLED) return;

        settingsMenu = SpriteIconButton.builder(
                        Component.translatable("better_screenshots.menu.settings"),
                        b -> {
                            assert this.minecraft != null;
                            this.minecraft.setScreen(new ScreenshotConfigScreen(this));
                        },
                        true)
                .size(BTN_SIZE, BTN_SIZE)
                .sprite(
                        ResourceLocation.fromNamespaceAndPath("better_screenshots", "icon/settings"),
                        20, 20)
                .build();

        galleryButton = SpriteIconButton.builder(
                        Component.translatable("better_screenshots.menu.gallery"),
                        b -> {
                            assert this.minecraft != null;
                            this.minecraft.setScreen(new ScreenshotGalleryScreen(this));
                        },
                        true)
                .size(BTN_SIZE, BTN_SIZE)
                .sprite(
                        ResourceLocation.fromNamespaceAndPath("better_screenshots", "icon/gallery"),
                        ICON_SIZE, ICON_SIZE)
                .build();

        cameraButton = SpriteIconButton.builder(
                        Component.translatable("better_screenshots.menu.screenshot"),
                        b -> {
                            assert this.minecraft != null;
                            this.minecraft.setScreen(null);

                            new Thread(() -> {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException ignored) {}

                                this.minecraft.execute(() ->
                                        RenderSystem.queueFencedTask(() ->
                                                Screenshot.grab(
                                                        this.minecraft.gameDirectory,
                                                        this.minecraft.getMainRenderTarget(),
                                                        (message) -> this.minecraft.execute(() -> {
                                                            if (this.minecraft.player != null) {
                                                                this.minecraft.gui.getChat().addMessage(message);
                                                            }
                                                        })
                                                )
                                        )
                                );
                            }).start();
                        },
                        true)
                .size(BTN_SIZE, BTN_SIZE)
                .sprite(
                        ResourceLocation.fromNamespaceAndPath("better_screenshots", "icon/camera"),
                        ICON_SIZE, ICON_SIZE)
                .build();

        int x, y;
        switch (pos) {
            case TOP_LEFT -> {
                x = 10;
                y = 10;
            }
            case TOP_RIGHT -> {
                x = this.width - 10 - 3 * BTN_SIZE - 2 * GAP;
                y = 10;
            }
            case BOTTOM_LEFT -> {
                x = 10;
                y = this.height - 30;
            }
            case BOTTOM_RIGHT -> {
                x = this.width - 10 - 3 * BTN_SIZE - 2 * GAP;
                y = this.height - 30;
            }
            default -> { return; }
        }

        settingsMenu.setPosition(x, y);
        galleryButton.setPosition(x + BTN_SIZE + GAP, y);
        cameraButton.setPosition(x + 2 * (BTN_SIZE + GAP), y);

        addRenderableWidget(cameraButton);
        addRenderableWidget(galleryButton);
        addRenderableWidget(settingsMenu);
    }
}
