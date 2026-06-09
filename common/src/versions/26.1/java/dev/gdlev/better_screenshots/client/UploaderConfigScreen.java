package dev.gdlev.better_screenshots.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public class UploaderConfigScreen extends Screen {

    private final Screen parent;

    private static final int COL_W = 180;
    private static final int BTN_H = 20;
    private static final int GAP = 26;
    private static final int SEP = 20;

    private static final int LABEL_W = 120;
    private static final int KV_GAP = 6;

    private final List<RowWidget> dynamicWidgets = new ArrayList<>();
    private final List<RowLabel> dynamicLabels = new ArrayList<>();

    private CycleButton<Boolean> chatNotifyButton;
    private CycleButton<Boolean> autoCopyButton;
    private CycleButton<Boolean> autoUploadButton;
    private CycleButton<ScreenshotConfig.UploadProvider> providerButton;
    private Button doneButton;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean draggingScrollbar = false;
    private double scrollbarDragOffsetY = 0.0;

    public UploaderConfigScreen(Screen parent) {
        super(Component.translatable("better_screenshots.config.uploader.title"));
        this.parent = parent;
    }

    private int panelH()     { return 240; }
    private int panelY()     { return (this.height - panelH()) / 2; }
    private int panelX()     { return this.width / 2 - COL_W - SEP / 2 - 12; }
    private int panelW()     { return COL_W * 2 + SEP + 24; }
    private int leftX()      { return panelX() + 12; }
    private int topY()       { return panelY() + 28; }
    private int bottomBtnY() { return panelY() + panelH() - 12 - BTN_H; }
    private int contentW()   { return panelW() - 24; }
    private int fieldX()     { return leftX() + LABEL_W; }
    private int fieldW()     { return contentW() - LABEL_W; }
    private int chatRowY()   { return topY() + 14; }
    private int autoRowY()   { return chatRowY() + GAP; }
    private int copyRowY()   { return autoRowY() + GAP; }
    private int providerY()  { return copyRowY() + GAP; }
    private int dynamicTop() { return providerY() + GAP; }
    private int dynamicRowY(int row) { return dynamicTop() + row * GAP - scrollOffset; }

    @Override
    protected void init() {
        clearWidgets();
        dynamicWidgets.clear();
        dynamicLabels.clear();

        ScreenshotConfig cfg = ScreenshotConfig.get();

        chatNotifyButton = addRenderableWidget(CycleButton.onOffBuilder(cfg.uploadChatNotification)
                .create(fieldX(), chatRowY(), fieldW(), BTN_H,
                        Component.translatable("better_screenshots.config.uploader.chat_notify"),
                        (btn, value) -> {
                            cfg.uploadChatNotification = value;
                            if (!value) {
                                cfg.uploadCopyToClipboard = true;
                            }
                            ScreenshotConfig.save();
                            refreshCopyToggleState();
                        }));

        autoUploadButton = addRenderableWidget(CycleButton.onOffBuilder(cfg.uploadAutoUpload)
                .create(fieldX(), autoRowY(), fieldW(), BTN_H,
                        Component.translatable("better_screenshots.config.uploader.auto_upload"),
                        (btn, value) -> {
                            cfg.uploadAutoUpload = value;
                            ScreenshotConfig.save();
                        }));

        autoCopyButton = addRenderableWidget(CycleButton.onOffBuilder(cfg.uploadCopyToClipboard)
                .create(fieldX(), copyRowY(), fieldW(), BTN_H,
                        Component.translatable("better_screenshots.config.uploader.auto_copy"),
                        (btn, value) -> {
                            if (!cfg.uploadChatNotification) {
                                cfg.uploadCopyToClipboard = true;
                            } else {
                                cfg.uploadCopyToClipboard = value;
                            }
                            ScreenshotConfig.save();
                            refreshCopyToggleState();
                        }));

        providerButton = addRenderableWidget(CycleButton.builder(
                        (ScreenshotConfig.UploadProvider p) -> Component.translatable(switch (p) {
                            case DISABLED -> "better_screenshots.config.uploader.provider.disabled";
                            case IMGUR -> "better_screenshots.config.uploader.provider.imgur";
                            case S3 -> "better_screenshots.config.uploader.provider.s3";
                            case CUSTOM_HTTP -> "better_screenshots.config.uploader.provider.custom";
                            case CATBOX -> "better_screenshots.config.uploader.provider.catbox";
                        }),
                        cfg.uploadProvider)
                .withValues(ScreenshotConfig.UploadProvider.values())
                .create(fieldX(), providerY(), fieldW(), BTN_H,
                        Component.translatable("better_screenshots.config.uploader.provider"),
                        (btn, value) -> {
                            cfg.uploadProvider = value;
                            ScreenshotConfig.save();
                            rebuildDynamicWidgets();
                        }));

        doneButton = addRenderableWidget(Button.builder(
                        Component.translatable("better_screenshots.config.done"),
                        btn -> minecraft.setScreen(parent))
                .bounds(leftX(), bottomBtnY(), contentW(), BTN_H)
                .build());

        refreshCopyToggleState();
        rebuildDynamicWidgets();
    }

    private void refreshCopyToggleState() {
        ScreenshotConfig cfg = ScreenshotConfig.get();
        if (!cfg.uploadChatNotification && !cfg.uploadCopyToClipboard) {
            cfg.uploadCopyToClipboard = true;
            ScreenshotConfig.save();
        }

        if (autoCopyButton != null) {
            autoCopyButton.active = cfg.uploadChatNotification;
            autoCopyButton.setAlpha(cfg.uploadChatNotification ? 1.0f : 0.45f);
            autoCopyButton.setValue(cfg.uploadCopyToClipboard);
        }
        if (chatNotifyButton != null) {
            chatNotifyButton.setValue(cfg.uploadChatNotification);
        }
    }

    private void rebuildDynamicWidgets() {
        for (RowWidget rowWidget : dynamicWidgets) {
            removeWidget(rowWidget.widget);
        }
        dynamicWidgets.clear();
        dynamicLabels.clear();

        ScreenshotConfig cfg = ScreenshotConfig.get();

        int row = 0;
        switch (cfg.uploadProvider) {
            case DISABLED -> {
                addLabel(row, "better_screenshots.config.uploader.disabled.info");
            }
            case CATBOX -> {
                addLabel(row, "better_screenshots.config.uploader.catbox.info");
            }
            case IMGUR -> {
                row = addSingleEditRow(row,
                        "better_screenshots.config.uploader.imgur.client_id",
                        cfg.imgurClientId,
                        value -> cfg.imgurClientId = value);
                addSingleEditRow(row,
                        "better_screenshots.config.uploader.imgur.access_token",
                        cfg.imgurAccessToken,
                        value -> cfg.imgurAccessToken = value);
            }
            case S3 -> {
                row = addSingleEditRow(row,
                        "better_screenshots.config.uploader.s3.endpoint",
                        cfg.s3Endpoint,
                        value -> cfg.s3Endpoint = value);
                row = addSingleEditRow(row,
                        "better_screenshots.config.uploader.s3.region",
                        cfg.s3Region,
                        value -> cfg.s3Region = value);
                row = addSingleEditRow(row,
                        "better_screenshots.config.uploader.s3.bucket",
                        cfg.s3Bucket,
                        value -> cfg.s3Bucket = value);
                row = addSingleEditRow(row,
                        "better_screenshots.config.uploader.s3.access_key",
                        cfg.s3AccessKey,
                        value -> cfg.s3AccessKey = value);
                row = addSingleEditRow(row,
                        "better_screenshots.config.uploader.s3.secret_key",
                        cfg.s3SecretKey,
                        value -> cfg.s3SecretKey = value);
                addSingleEditRow(row,
                        "better_screenshots.config.uploader.s3.path_prefix",
                        cfg.s3PathPrefix,
                        value -> cfg.s3PathPrefix = value);
            }
            case CUSTOM_HTTP -> {
                row = addSingleEditRow(row,
                        "better_screenshots.config.uploader.custom.url",
                        cfg.customUploadUrl,
                        value -> cfg.customUploadUrl = value);

                addLabel(row, "better_screenshots.config.uploader.custom.method");
                CycleButton<ScreenshotConfig.UploadMethod> methodButton = addRenderableWidget(
                        CycleButton.builder(
                                        (ScreenshotConfig.UploadMethod m) -> Component.translatable(switch (m) {
                                            case POST -> "better_screenshots.config.uploader.method.post";
                                            case PUT -> "better_screenshots.config.uploader.method.put";
                                        }),
                                        cfg.customUploadMethod)
                                .withValues(ScreenshotConfig.UploadMethod.values())
                                .create(fieldX(), dynamicRowY(row), fieldW(), BTN_H,
                                        Component.translatable("better_screenshots.config.uploader.custom.method"),
                                        (btn, value) -> {
                                            cfg.customUploadMethod = value;
                                            ScreenshotConfig.save();
                                        }));
                dynamicWidgets.add(new RowWidget(methodButton, row));
                row++;

                row = addKeyValueRow(row,
                        "better_screenshots.config.uploader.custom.cookie",
                        cfg.customCookieKey,
                        cfg.customCookieValue,
                        key -> cfg.customCookieKey = key,
                        value -> cfg.customCookieValue = value);
                row = addKeyValueRow(row,
                        "better_screenshots.config.uploader.custom.header",
                        cfg.customHeaderKey,
                        cfg.customHeaderValue,
                        key -> cfg.customHeaderKey = key,
                        value -> cfg.customHeaderValue = value);
                addKeyValueRow(row,
                        "better_screenshots.config.uploader.custom.post_field",
                        cfg.customPostKey,
                        cfg.customPostValue,
                        key -> cfg.customPostKey = key,
                        value -> cfg.customPostValue = value);
            }
        }

        int visibleH = (bottomBtnY() - 2) - dynamicTop();
        int totalRows = Math.max(1, maxRowIndex() + 1);
        int totalH = totalRows * GAP + 4;
        maxScroll = Math.max(0, totalH - visibleH);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        updateDynamicWidgetPositions();
    }

    private int maxRowIndex() {
        int max = 0;
        for (RowWidget rowWidget : dynamicWidgets) {
            max = Math.max(max, rowWidget.rowIndex);
        }
        for (RowLabel label : dynamicLabels) {
            max = Math.max(max, label.rowIndex);
        }
        return max;
    }

    private void updateDynamicWidgetPositions() {
        for (RowWidget rowWidget : dynamicWidgets) {
            rowWidget.widget.setY(dynamicRowY(rowWidget.rowIndex));
        }
    }

    private void addLabel(int row, String key) {
        dynamicLabels.add(new RowLabel(Component.translatable(key), row));
    }

    private int addSingleEditRow(int row, String labelKey, String currentValue, Consumer<String> onChange) {
        addLabel(row, labelKey);

        EditBox box = new EditBox(font, fieldX(), dynamicRowY(row), fieldW(), BTN_H, Component.translatable(labelKey));
        box.setValue(currentValue == null ? "" : currentValue);
        box.setResponder(value -> {
            onChange.accept(value);
            ScreenshotConfig.save();
        });

        dynamicWidgets.add(new RowWidget(addRenderableWidget(box), row));
        return row + 1;
    }

    private int addKeyValueRow(
            int row,
            String labelKey,
            String currentKey,
            String currentValue,
            Consumer<String> onKeyChange,
            Consumer<String> onValueChange) {
        addLabel(row, labelKey);

        int keyW = (fieldW() - KV_GAP) / 2;
        int valueW = fieldW() - keyW - KV_GAP;

        EditBox keyBox = new EditBox(font, fieldX(), dynamicRowY(row), keyW, BTN_H,
                Component.translatable("better_screenshots.config.uploader.column.key"));
        keyBox.setValue(currentKey == null ? "" : currentKey);
        keyBox.setHint(Component.translatable("better_screenshots.config.uploader.column.key"));
        keyBox.setResponder(value -> {
            onKeyChange.accept(value);
            ScreenshotConfig.save();
        });

        EditBox valueBox = new EditBox(font, fieldX() + keyW + KV_GAP, dynamicRowY(row), valueW, BTN_H,
                Component.translatable("better_screenshots.config.uploader.column.value"));
        valueBox.setValue(currentValue == null ? "" : currentValue);
        valueBox.setHint(Component.translatable("better_screenshots.config.uploader.column.value"));
        valueBox.setResponder(value -> {
            onValueChange.accept(value);
            ScreenshotConfig.save();
        });

        dynamicWidgets.add(new RowWidget(addRenderableWidget(keyBox), row));
        dynamicWidgets.add(new RowWidget(addRenderableWidget(valueBox), row));
        return row + 1;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        updateDynamicWidgetPositions();

        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();
        int lx = leftX();
        int ty = topY();
        int by = bottomBtnY();

        drawPanel(context, px, py, pw, ph);

        context.centeredText(font,
                Component.literal("✦  ").append(Component.translatable("better_screenshots.config.uploader.title")).append("  ✦"),
                this.width / 2, py + 8, 0xFFCCCCCC);
        context.fill(px + 8, py + 20, px + pw - 8, py + 21, 0xFF444444);

        context.centeredText(font,
                Component.translatable("better_screenshots.config.section.settings"),
                lx + contentW() / 2, ty + 2, 0xFF777777);

        context.text(font,
                Component.translatable("better_screenshots.config.uploader.chat_notify"),
                lx + 2, chatRowY() + 6, 0xFFB5B5B5);

        context.text(font,
                Component.translatable("better_screenshots.config.uploader.auto_upload"),
                lx + 2, autoRowY() + 6, 0xFFB5B5B5);

        context.text(font,
                Component.translatable("better_screenshots.config.uploader.auto_copy"),
                lx + 2, copyRowY() + 6, 0xFFB5B5B5);

        context.text(font,
                Component.translatable("better_screenshots.config.uploader.provider"),
                lx + 2, providerY() + 6, 0xFFB5B5B5);

        if (chatNotifyButton != null) chatNotifyButton.extractRenderState(context, mouseX, mouseY, delta);
        if (autoCopyButton != null) autoCopyButton.extractRenderState(context, mouseX, mouseY, delta);
        if (autoUploadButton != null) autoUploadButton.extractRenderState(context, mouseX, mouseY, delta);
        if (providerButton != null) providerButton.extractRenderState(context, mouseX, mouseY, delta);

        boolean showWarningTooltip = false;

        // Warning badge for enabled uploaders
        ScreenshotConfig.UploadProvider provider = ScreenshotConfig.get().uploadProvider;
        if (provider != ScreenshotConfig.UploadProvider.DISABLED) {
            int wx = fieldX() + fieldW() - 12;
            int wy = providerY() + 5;
            context.fill(wx, wy, wx + 9, wy + 9, 0xFFE38A23);
            context.text(font, Component.literal("!").withStyle(ChatFormatting.BOLD), wx + 3, wy + 1, 0xFF2C1600, false);

            showWarningTooltip = mouseX >= wx && mouseX <= wx + 9 && mouseY >= wy && mouseY <= wy + 9;
        }

        int sTop = dynamicTop() - 2;
        int sBottom = by - 2;
        context.enableScissor(lx, sTop, lx + contentW() + 10, sBottom);
        for (RowLabel label : dynamicLabels) {
            context.text(font, label.text, lx + 2, dynamicRowY(label.rowIndex) + 6, 0xFFB5B5B5);
        }
        for (RowWidget rowWidget : dynamicWidgets) {
            rowWidget.widget.extractRenderState(context, mouseX, mouseY, delta);
        }
        context.disableScissor();

        int sHeight = sBottom - sTop;
        if (maxScroll > 0) {
            int sbX = lx + contentW() + 2;
            int sbW = 2;
            int sbH = Math.max(10, sHeight * sHeight / (sHeight + maxScroll));
            int sbY = sTop + (int)((float) scrollOffset / maxScroll * (sHeight - sbH));
            context.fill(sbX, sTop, sbX + sbW, sBottom, 0x22FFFFFF);
            context.fill(sbX, sbY, sbX + sbW, sbY + sbH, 0x88FFFFFF);
        }

        if (doneButton != null) doneButton.extractRenderState(context, mouseX, mouseY, delta);

        if (showWarningTooltip) {
            int warnW = 230;
            String warning = Component.translatable("better_screenshots.config.uploader.warning").getString();
            java.util.List<String> wrapped = new java.util.ArrayList<>();
            String[] words = warning.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (font.width(next) > warnW && !line.isEmpty()) {
                    wrapped.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    if (!line.isEmpty()) line.append(" ");
                    line.append(word);
                }
            }
            if (!line.isEmpty()) wrapped.add(line.toString());

            int warnH = wrapped.size() * 10 + 6;
            int warnX = Math.min(mouseX + 10, this.width - warnW - 8);
            int warnY = Math.min(mouseY + 10, this.height - warnH - 8);
            context.fill(warnX - 2, warnY - 2, warnX + warnW + 4, warnY + warnH + 2, 0xEE111111);
            for (int i = 0; i < wrapped.size(); i++) {
                context.text(font, Component.literal(wrapped.get(i)), warnX, warnY + i * 10, 0xFFF4E8D0, false);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (maxScroll > 0 && mouseX >= leftX() && mouseX <= leftX() + contentW() + 10) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(vAmount * 16)));
            updateDynamicWidgetPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent input, boolean consumed) {
        if (input.button() == 0 && doneButton != null && doneButton.isMouseOver(input.x(), input.y())) {
            minecraft.setScreen(parent);
            return true;
        }

        if (input.button() == 0 && maxScroll > 0) {
            int lx = leftX();
            int sTop = dynamicTop() - 2;
            int sBottom = bottomBtnY() - 2;
            int sHeight = sBottom - sTop;
            int sbX = lx + contentW() + 2;
            if (input.x() >= sbX - 2 && input.x() <= sbX + 6) {
                int sbH = Math.max(10, sHeight * sHeight / (sHeight + maxScroll));
                int sbY = sTop + (int)((float) scrollOffset / maxScroll * (sHeight - sbH));
                if (input.y() >= sbY && input.y() <= sbY + sbH) {
                    scrollbarDragOffsetY = input.y() - sbY;
                } else {
                    scrollbarDragOffsetY = sbH / 2.0;
                }
                draggingScrollbar = true;
                updateScrollFromThumb(input.y() - scrollbarDragOffsetY, sbH, sHeight);
                updateDynamicWidgetPositions();
                return true;
            }
        }
        return super.mouseClicked(input, consumed);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent input, double dx, double dy) {
        if (input.button() == 0 && draggingScrollbar) {
            int sTop = dynamicTop() - 2;
            int sBottom = bottomBtnY() - 2;
            int sHeight = sBottom - sTop;
            int sbH = Math.max(10, sHeight * sHeight / (sHeight + maxScroll));
            updateScrollFromThumb(input.y() - scrollbarDragOffsetY, sbH, sHeight);
            updateDynamicWidgetPositions();
            return true;
        }
        return super.mouseDragged(input, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent input) {
        if (input.button() == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(input);
    }

    private void updateScrollFromThumb(double thumbTopY, int thumbH, int visibleH) {
        if (maxScroll <= 0) { scrollOffset = 0; return; }
        int sTop = dynamicTop() - 2;
        int travel = Math.max(1, visibleH - thumbH);
        double clamped = Math.max(sTop, Math.min(sTop + travel, thumbTopY));
        double ratio = (clamped - sTop) / travel;
        scrollOffset = (int) Math.round(ratio * maxScroll);
    }

    private void drawPanel(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        ctx.fill(x,         y,         x + w,     y + h,     0xAA000000);
        ctx.fill(x,         y,         x + w,     y + 1,     0xFF555555);
        ctx.fill(x,         y + h - 1, x + w,     y + h,     0xFF555555);
        ctx.fill(x,         y,         x + 1,     y + h,     0xFF555555);
        ctx.fill(x + w - 1, y,         x + w,     y + h,     0xFF555555);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private record RowWidget(AbstractWidget widget, int rowIndex) {}
    private record RowLabel(Component text, int rowIndex) {}
}
