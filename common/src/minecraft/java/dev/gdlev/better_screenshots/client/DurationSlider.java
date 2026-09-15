package dev.gdlev.better_screenshots.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public class DurationSlider extends AbstractSliderButton {

    public DurationSlider(int x, int y, int width, int height, double initialValue) {
        super(x, y, width, height, Component.literal(""), initialValue);
        updateMessage();
    }

    private int toSeconds() {
        return (int) Math.round(value * 15.0);
    }

    @Override
    protected void updateMessage() {
        int seconds = toSeconds();
        if (seconds == 0) {
            setMessage(Component.translatable("better_screenshots.config.preview_duration.off"));
        } else {
            setMessage(Component.translatable("better_screenshots.config.preview_duration", seconds));
        }
    }

    @Override
    protected void applyValue() {
        ScreenshotConfig.get().previewDurationSeconds = toSeconds();
        ScreenshotConfig.save();
    }
}