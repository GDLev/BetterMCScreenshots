package dev.gdlev.better_screenshots.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public class DurationSlider extends AbstractSliderButton {

    public DurationSlider(int x, int y, int width, int height, double initialValue) {
        super(x, y, width, height, Component.literal(""), initialValue);
        updateMessage();
    }

    private int toSeconds() {
        return 1 + (int) Math.round(value * 14.0);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable("better_screenshots.config.preview_duration",
                toSeconds()));
    }

    @Override
    protected void applyValue() {
        ScreenshotConfig.get().previewDurationSeconds = toSeconds();
        ScreenshotConfig.save();
    }
}