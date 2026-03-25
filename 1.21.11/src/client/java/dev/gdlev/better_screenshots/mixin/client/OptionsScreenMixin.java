package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotConfigScreen;
import dev.gdlev.better_screenshots.client.ScreenshotGalleryScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class OptionsScreenMixin extends Screen {
    @Unique
    private static final Identifier ICON_CAMERA    = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/camera.png");
    @Unique
    private static final Identifier ICON_GALLERY   = Identifier.fromNamespaceAndPath("better_screenshots", "textures/gui/gallery.png");

    @Unique
    private static final int BTN_SIZE = 20;
    @Unique
    private static final int GAP      = 4;

    @Unique
    private Button cameraButton  = null;
    @Unique
    private Button galleryButton = null;

    protected OptionsScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        int x = 10;
        int y = 10;

        cameraButton = Button.builder(
                        Component.literal(""),
                        b -> this.minecraft.setScreen(
                                new ScreenshotConfigScreen(this)))
                .bounds(x, y, BTN_SIZE, BTN_SIZE)
                .build();

        galleryButton = Button.builder(
                        Component.literal(""),
                        b -> this.minecraft.setScreen(
                                new ScreenshotGalleryScreen(this)))
                .bounds(x + BTN_SIZE + GAP, y, BTN_SIZE, BTN_SIZE)
                .build();

        addRenderableWidget(cameraButton);
        addRenderableWidget(galleryButton);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics context, int mouseX, int mouseY,
                          float delta, CallbackInfo ci) {
        drawIcon(context, mouseX, mouseY, cameraButton,  ICON_CAMERA, ICON_CAMERA);
        drawIcon(context, mouseX, mouseY, galleryButton, ICON_GALLERY, ICON_GALLERY);
    }

    @Unique
    private void drawIcon(GuiGraphics context, int mouseX, int mouseY,
                          Button btn, Identifier icon, Identifier iconHover) {
        if (btn == null) return;
        boolean hov = mouseX >= btn.getX() && mouseX <= btn.getX() + BTN_SIZE
                && mouseY >= btn.getY() && mouseY <= btn.getY() + BTN_SIZE;
        int iconSize = BTN_SIZE - 4;
        int iconX    = btn.getX() + 2;
        int iconY    = btn.getY() + 2;
        context.blit(
                RenderPipelines.GUI_TEXTURED,
                hov ? iconHover : icon,
                iconX, iconY, 0f, 0f,
                iconSize, iconSize, iconSize, iconSize);
    }
}