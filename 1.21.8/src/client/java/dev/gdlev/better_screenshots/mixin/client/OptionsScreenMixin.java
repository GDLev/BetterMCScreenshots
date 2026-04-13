package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotConfigScreen;
import dev.gdlev.better_screenshots.client.ScreenshotGalleryScreen;
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

    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        cameraButton = SpriteIconButton.builder(
                        Component.literal("Screenshot Settings"),
                        b -> {
                            assert this.minecraft != null;
                            this.minecraft.setScreen(new ScreenshotConfigScreen(this));
                        },
                        true)
                .size(BTN_SIZE, BTN_SIZE)
                .sprite(
                        ResourceLocation.fromNamespaceAndPath("better_screenshots", "icon/camera"),
                        ICON_SIZE, ICON_SIZE)
                .build();
        cameraButton.setPosition(10, 10);

        galleryButton = SpriteIconButton.builder(
                        Component.literal("Screenshot Gallery"),
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
        galleryButton.setPosition(10 + BTN_SIZE + GAP, 10);

        addRenderableWidget(cameraButton);
        addRenderableWidget(galleryButton);
    }
}