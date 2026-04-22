package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotGalleryScreen;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Unique
    private static final int BTN_SIZE = 20;
    @Unique
    private static final int GAP = 4;
    @Unique
    private static final int ICON_SIZE = 16;

    @Unique
    private SpriteIconButton galleryButton = null;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        int i = this.height / 4 + 50;
        int y = i + 72 + 16;
        int x = this.width / 2 + 104 + BTN_SIZE + GAP;

        galleryButton = SpriteIconButton.builder(
                        Component.translatable("better_screenshots.menu.gallery"),
                        b -> this.minecraft.setScreen(new ScreenshotGalleryScreen(this)),
                        true)
                .size(BTN_SIZE, BTN_SIZE)
                .sprite(
                        ResourceLocation.fromNamespaceAndPath("better_screenshots", "icon/gallery"),
                        ICON_SIZE, ICON_SIZE)
                .build();
        galleryButton.setPosition(x, y);

        addRenderableWidget(galleryButton);
    }
}
