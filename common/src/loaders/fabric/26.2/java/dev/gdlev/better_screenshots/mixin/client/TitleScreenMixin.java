package dev.gdlev.better_screenshots.mixin.client;

import dev.gdlev.better_screenshots.client.ScreenshotGalleryScreen;
import dev.gdlev.better_screenshots.client.MinecraftCompat;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
    private static final int ICON_SIZE = 16;

    @Unique
    private SpriteIconButton galleryButton = null;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        int y = findSmallButtonRowY();
        if (y < 0) return;

        galleryButton = SpriteIconButton.builder(
                        Component.literal("better_screenshots:title_gallery"),
                        b -> MinecraftCompat.setScreen(this.minecraft, new ScreenshotGalleryScreen(this)),
                        true)
                .size(BTN_SIZE, BTN_SIZE)
                .sprite(
                        Identifier.fromNamespaceAndPath("better_screenshots", "icon/gallery"),
                        ICON_SIZE, ICON_SIZE)
                .build();
        galleryButton.setTooltip(Tooltip.create(Component.translatable("better_screenshots.menu.gallery")));
        galleryButton.setPosition(this.width, y);

        addRenderableWidget(galleryButton);
        relayoutSmallButtonRow(y);
    }

    @Unique
    private int findSmallButtonRowY() {
        java.util.Map<Integer, Integer> rows = new java.util.HashMap<>();
        int bestY = -1;
        int bestCount = 0;

        for (var child : this.children()) {
            if (!(child instanceof AbstractWidget widget)) continue;
            if (widget.getWidth() != BTN_SIZE || widget.getHeight() != BTN_SIZE) continue;
            if (widget.getY() < this.height / 4) continue;

            int count = rows.merge(widget.getY(), 1, Integer::sum);
            if (count > bestCount) {
                bestCount = count;
                bestY = widget.getY();
            }
        }

        return bestY;
    }

    @Unique
    private void relayoutSmallButtonRow(int y) {
        java.util.List<AbstractWidget> row = this.children().stream()
                .filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .filter(widget -> widget.getY() == y && widget.getWidth() == BTN_SIZE && widget.getHeight() == BTN_SIZE)
                .sorted(java.util.Comparator.comparingInt(AbstractWidget::getX))
                .toList();

        int count = row.size();
        for (int i = 0; i < count; i++) {
            row.get(i).setPosition(getHorizontalPosition(i + 1, count, BTN_SIZE), y);
        }
    }

    @Unique
    private int getHorizontalPosition(int index, int count, int buttonWidth) {
        int rowWidth = count * buttonWidth + (count - 1) * 4;
        return this.width / 2 - rowWidth / 2 + (index - 1) * (buttonWidth + 4);
    }
}
