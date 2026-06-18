package dev.gdlev.better_screenshots.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gdlev.better_screenshots.client.ScreenshotConfig;
import dev.gdlev.better_screenshots.client.ScreenshotConfigScreen;
import dev.gdlev.better_screenshots.client.ScreenshotGalleryScreen;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Redirect(
            method = "createPauseMenu",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/layouts/LinearLayout;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;",
                    ordinal = 3
            )
    )
    private LayoutElement addModButtonsToQuickActions(LinearLayout row, LayoutElement vanillaButton) {
        LayoutElement added = row.addChild(vanillaButton);
        ScreenshotConfig.MenuButtonPosition pos = ScreenshotConfig.get().menuButtonPosition;
        if (pos != ScreenshotConfig.MenuButtonPosition.CENTER) return added;

        createModButtons();
        row.addChild(settingsMenu);
        row.addChild(galleryButton);
        row.addChild(cameraButton);
        return added;
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        ScreenshotConfig.MenuButtonPosition pos = ScreenshotConfig.get().menuButtonPosition;
        if (pos == ScreenshotConfig.MenuButtonPosition.DISABLED || pos == ScreenshotConfig.MenuButtonPosition.CENTER) return;

        createModButtons();

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

    @Unique
    private void createModButtons() {
        settingsMenu = SpriteIconButton.builder(
                        Component.translatable("better_screenshots.menu.settings"),
                        b -> dev.gdlev.better_screenshots.client.MinecraftCompat.setScreen(this.minecraft, new ScreenshotConfigScreen(this)),
                        true)
                .size(BTN_SIZE, BTN_SIZE)
                .sprite(
                        Identifier.fromNamespaceAndPath("better_screenshots", "icon/settings"),
                        20, 20)
                .withTootip()
                .build();

        galleryButton = SpriteIconButton.builder(
                        Component.translatable("better_screenshots.menu.gallery"),
                        b -> dev.gdlev.better_screenshots.client.MinecraftCompat.setScreen(this.minecraft, new ScreenshotGalleryScreen(this)),
                        true)
                .size(BTN_SIZE, BTN_SIZE)
                .sprite(
                        Identifier.fromNamespaceAndPath("better_screenshots", "icon/gallery"),
                        ICON_SIZE, ICON_SIZE)
                .withTootip()
                .build();

        cameraButton = SpriteIconButton.builder(
                        Component.translatable("better_screenshots.menu.screenshot"),
                        b -> {
                            dev.gdlev.better_screenshots.client.MinecraftCompat.setScreen(this.minecraft, null);

                            new Thread(() -> {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException ignored) {}

                                this.minecraft.execute(() ->
                                        RenderSystem.queueFencedTask(() ->
                                                Screenshot.grab(
                                                        this.minecraft.gameDirectory,
                                                        dev.gdlev.better_screenshots.client.MinecraftCompat.mainRenderTarget(this.minecraft),
                                                        (message) -> this.minecraft.execute(() -> {
                                                            if (this.minecraft.player != null) {
                                                                this.minecraft.player.sendSystemMessage(message);
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
                        Identifier.fromNamespaceAndPath("better_screenshots", "icon/camera"),
                        ICON_SIZE, ICON_SIZE)
                .withTootip()
                .build();
    }
}
