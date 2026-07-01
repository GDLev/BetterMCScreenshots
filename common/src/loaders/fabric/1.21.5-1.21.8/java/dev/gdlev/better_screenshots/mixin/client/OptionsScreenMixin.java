package dev.gdlev.better_screenshots.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gdlev.better_screenshots.client.ScreenshotConfig;
import dev.gdlev.better_screenshots.client.ScreenshotConfigScreen;
import dev.gdlev.better_screenshots.client.ScreenshotGalleryScreen;
import dev.gdlev.better_screenshots.client.PauseMenuButtonLayout;
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
        PauseMenuButtonLayout.ensureMigrated();

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

        int[] x = new int[PauseMenuButtonLayout.ACTION_COUNT];
        int[] y = new int[PauseMenuButtonLayout.ACTION_COUNT];
        boolean[] visible = PauseMenuButtonLayout.visibility();
        PauseMenuButtonLayout.arrangeCornerButtons(
                x, y, this.width, this.height, BTN_SIZE, GAP, 10);
        SpriteIconButton[] buttons = { settingsMenu, galleryButton, cameraButton };
        for (int action = 0; action < buttons.length; action++) {
            if (!visible[action]) continue;
            buttons[action].setPosition(x[action], y[action]);
            addRenderableWidget(buttons[action]);
        }
    }
}
