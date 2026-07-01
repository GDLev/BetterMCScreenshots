package dev.gdlev.better_screenshots.client;

import dev.gdlev.better_screenshots.common.ScreenshotConfigData.ActionButtonCorner;
import dev.gdlev.better_screenshots.common.ScreenshotConfigData.PauseButtonAnchor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ActionButtonConfigScreen extends Screen {

    private enum PreviewMode {
        MINI_PREVIEW("better_screenshots.config.actions.mini_preview"),
        GALLERY_THUMBNAIL("better_screenshots.config.actions.gallery_thumbnail"),
        CONFIG_MENU("better_screenshots.config.actions.config_menu"),
        FULLSCREEN_PREVIEW("better_screenshots.config.actions.fullscreen_preview"),
        PAUSE_MENU("better_screenshots.config.actions.pause_menu");

        private final String translationKey;

        PreviewMode(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private static final ResourceLocation ICON_SHOW =
            ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/show.png");
    private static final ResourceLocation ICON_SHOW_H =
            ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/show_hover.png");
    private static final ResourceLocation ICON_COPY =
            ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/copy.png");
    private static final ResourceLocation ICON_COPY_H =
            ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/copy_hover.png");
    private static final ResourceLocation ICON_UPLOAD =
            ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/upload.png");
    private static final ResourceLocation ICON_UPLOAD_H =
            ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/upload_hover.png");
    private static final ResourceLocation ICON_DELETE =
            ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/delete.png");
    private static final ResourceLocation ICON_DELETE_H =
            ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/delete_hover.png");
    private static final ResourceLocation ICON_CLOSE =
            ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/close.png");
    private static final ResourceLocation ICON_CLOSE_H =
            ResourceLocation.fromNamespaceAndPath("better_screenshots", "textures/gui/close_hover.png");
    private static final ResourceLocation ICON_PAUSE_SETTINGS =
            ResourceLocation.fromNamespaceAndPath(
                    "better_screenshots", "textures/gui/sprites/icon/settings.png");
    private static final ResourceLocation ICON_PAUSE_GALLERY =
            ResourceLocation.fromNamespaceAndPath(
                    "better_screenshots", "textures/gui/sprites/icon/gallery.png");
    private static final ResourceLocation ICON_PAUSE_SCREENSHOT =
            ResourceLocation.fromNamespaceAndPath(
                    "better_screenshots", "textures/gui/sprites/icon/camera.png");

    private static final int PANEL_MAX_W = 404;
    private static final int PANEL_MAX_H = 240;
    private static final int SIDEBAR_W = 124;
    private static final int TRAY_W = 72;
    private static final int BUTTON_W = 12;
    private static final int BUTTON_H = 15;
    private static final int BUTTON_GAP = 1;
    private static final int PAUSE_BUTTON_MAX_SIZE = 18;
    private static final int PAUSE_BUTTON_MAX_GAP = 4;
    private static final int PREVIEW_MARGIN = 8;

    private final Screen parent;
    private final Button[] modeButtons = new Button[PreviewMode.values().length];
    private final int[] actionX = new int[4];
    private final int[] actionY = new int[4];
    private final int[] targetX = new int[4];
    private final int[] targetY = new int[4];
    private final int[] trayActionX = new int[4];
    private final int[] trayActionY = new int[4];
    private final double[] animatedX = {
            Double.NaN, Double.NaN, Double.NaN, Double.NaN
    };
    private final double[] animatedY = {
            Double.NaN, Double.NaN, Double.NaN, Double.NaN
    };

    private PreviewMode mode = PreviewMode.MINI_PREVIEW;
    private Button doneButton;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;
    private int draggingAction = -1;
    private int hoveredAction = -1;
    private double dragMouseX;
    private double dragMouseY;
    private ActionButtonCorner dragCorner;
    private PauseButtonAnchor dragPauseAnchor;
    private int dragInsertIndex = -1;
    private double animatedPauseVanillaX = Double.NaN;

    public ActionButtonConfigScreen(Screen parent) {
        super(Component.translatable("better_screenshots.config.actions.title"));
        this.parent = parent;
    }

    private int panelW() {
        return Math.max(320, Math.min(PANEL_MAX_W, width - 24));
    }

    private int panelH() {
        return Math.max(210, Math.min(PANEL_MAX_H, height - 24));
    }

    private int panelX() {
        return (width - panelW()) / 2;
    }

    private int panelY() {
        return (height - panelH()) / 2;
    }

    private int trayX() {
        return panelX() + panelW() - TRAY_W - 9;
    }

    private int trayY() {
        return panelY() + 64;
    }

    private int trayH() {
        return panelH() - 84;
    }

    @Override
    protected void init() {
        clearWidgets();
        PauseMenuButtonLayout.ensureMigrated();
        int px = panelX();
        int py = panelY();
        int tabX = px + 10;
        int tabY = py + 36;
        int tabW = SIDEBAR_W - 20;

        for (int i = 0; i < PreviewMode.values().length; i++) {
            PreviewMode buttonMode = PreviewMode.values()[i];
            modeButtons[i] = addRenderableWidget(Button.builder(
                            Component.translatable(buttonMode.translationKey),
                            button -> setMode(buttonMode))
                    .bounds(tabX, tabY + i * 26, tabW, 20)
                    .build());
        }

        doneButton = addRenderableWidget(Button.builder(
                        Component.translatable("better_screenshots.config.done"),
                        button -> minecraft.setScreen(parent))
                .bounds(tabX, py + panelH() - 32, tabW, 20)
                .build());

        updateModeButtons();
        updatePreviewBounds();
    }

    private void setMode(PreviewMode newMode) {
        mode = newMode;
        draggingAction = -1;
        dragCorner = null;
        dragPauseAnchor = null;
        dragInsertIndex = -1;
        Arrays.fill(animatedX, Double.NaN);
        Arrays.fill(animatedY, Double.NaN);
        animatedPauseVanillaX = Double.NaN;
        updateModeButtons();
        updatePreviewBounds();
    }

    private void updateModeButtons() {
        for (int i = 0; i < modeButtons.length; i++) {
            if (modeButtons[i] != null) {
                modeButtons[i].active = PreviewMode.values()[i] != mode;
            }
        }
    }

    private void updatePreviewBounds() {
        int areaX = panelX() + SIDEBAR_W + 10;
        int areaY = panelY() + 64;
        int areaW = Math.max(120, trayX() - areaX - 9);
        int areaH = panelH() - 84;
        int maxW = Math.max(110, areaW);
        int maxH = Math.max(70, areaH - 18);
        previewW = maxW;
        previewH = Math.max(1, Math.round(previewW * 9f / 16f));
        if (previewH > maxH) {
            previewH = maxH;
            previewW = Math.max(1, Math.round(previewH * 16f / 9f));
        }
        previewX = areaX + (areaW - previewW) / 2;
        previewY = areaY + (areaH - previewH) / 2;
    }

    private int actionCount() {
        return mode == PreviewMode.PAUSE_MENU
                ? PauseMenuButtonLayout.ACTION_COUNT
                : 4;
    }

    @Override
    public void render(
            GuiGraphics context,
            int mouseX,
            int mouseY,
            float delta) {
        updatePreviewBounds();
        drawPanel(context, panelX(), panelY(), panelW(), panelH());

        context.drawCenteredString(font,
                Component.translatable("better_screenshots.config.actions.title"),
                panelX() + panelW() / 2, panelY() + 9, 0xFFCCCCCC);
        context.fill(panelX() + 8, panelY() + 21,
                panelX() + panelW() - 8, panelY() + 22, 0xFF444444);
        context.fill(panelX() + SIDEBAR_W, panelY() + 24,
                panelX() + SIDEBAR_W + 1, panelY() + panelH() - 8, 0xFF333333);

        context.drawCenteredString(font,
                Component.translatable(mode.translationKey),
                previewX + previewW / 2, panelY() + 27, 0xFF888888);
        drawEditorHint(context);

        updateDragTarget(dragMouseX, dragMouseY);
        drawPlaceholder(context);
        buildPreviewTargets();
        animatePreviewActions();
        layoutTrayActions();
        drawCornerArrows(context);
        drawTray(context);
        drawActions(context, mouseX, mouseY);

        for (Button modeButton : modeButtons) {
            if (modeButton != null) modeButton.render(context, mouseX, mouseY, delta);
        }
        if (doneButton != null) doneButton.render(context, mouseX, mouseY, delta);

        if (hoveredAction >= 0 && draggingAction < 0) {
            drawActionTooltip(context, hoveredAction, mouseX, mouseY);
        }
    }

    private void drawEditorHint(GuiGraphics context) {
        int x = panelX() + SIDEBAR_W + 10;
        int y = panelY() + 38;
        int width = panelX() + panelW() - 9 - x;
        int height = 22;
        context.fill(x, y, x + width, y + height, 0xCC171717);
        context.fill(x, y, x + width, y + 1, 0xFF454545);
        context.fill(x, y + height - 1, x + width, y + height, 0xFF454545);
        context.fill(x, y, x + 1, y + height, 0xFF454545);
        context.fill(x + width - 1, y, x + width, y + height, 0xFF454545);
        context.drawCenteredString(font,
                Component.translatable("better_screenshots.config.actions.hint.drag"),
                x + width / 2, y + 3, 0xFFB0B0B0);
        context.drawCenteredString(font,
                Component.translatable("better_screenshots.config.actions.hint.remove"),
                x + width / 2, y + 12, 0xFF777777);
    }

    private void drawPlaceholder(GuiGraphics context) {
        context.fill(previewX - 2, previewY - 2,
                previewX + previewW + 2, previewY + previewH + 2, 0xFF555555);
        context.fill(previewX - 1, previewY - 1,
                previewX + previewW + 1, previewY + previewH + 1, 0xFF101010);
        context.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF363636);
    }

    private void drawPauseMenuReplica(GuiGraphics context) {
        int wideW = pauseMenuWidth();
        int wideX = previewX + (previewW - wideW) / 2;
        int rowH = pauseRowHeight();
        int gap = pauseButtonGap();
        int rowStep = rowH + gap;
        int halfW = (wideW - gap) / 2;
        int rightX = wideX + halfW + gap;
        int rowY = pauseMenuTop();

        drawDisabledReplicaButton(context, wideX, rowY, wideW, rowH);
        drawDisabledReplicaButton(context, wideX, rowY + rowStep, halfW, rowH);
        drawDisabledReplicaButton(context, rightX, rowY + rowStep, halfW, rowH);

        int quickY = pauseCenterY();
        int vanillaX = animatedPauseVanillaRowX();
        for (int i = 0; i < 4; i++) {
            drawDisabledReplicaButton(context,
                    vanillaX + i * (pauseButtonSize() + gap),
                    quickY, pauseButtonSize(), pauseButtonSize());
        }

        drawDisabledReplicaButton(context, wideX, rowY + rowStep * 3, halfW, rowH);
        drawDisabledReplicaButton(context, rightX, rowY + rowStep * 3, halfW, rowH);
        drawDisabledReplicaButton(context, wideX, rowY + rowStep * 4, wideW, rowH);
    }

    private void drawDisabledReplicaButton(
            GuiGraphics context,
            int x,
            int y,
            int width,
            int height) {
        context.fill(x, y, x + width, y + height, 0xFF555555);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF444444);
    }

    private int pauseMenuWidth() {
        return Math.max(1, Math.min(204, previewW - pauseLayoutInset() * 2));
    }

    private int pauseButtonGap() {
        return Math.max(1, Math.min(PAUSE_BUTTON_MAX_GAP, previewH / 40));
    }

    private int pauseButtonSize() {
        return PAUSE_BUTTON_MAX_SIZE;
    }

    private int pauseRowHeight() {
        int fixedSpace = (PREVIEW_MARGIN + 2) * 2
                + pauseButtonGap() * 4;
        return Math.max(4,
                Math.min(PAUSE_BUTTON_MAX_SIZE, (previewH - fixedSpace) / 7));
    }

    private int pauseLayoutInset() {
        return PREVIEW_MARGIN + pauseButtonSize() + 2;
    }

    private int pauseMenuTop() {
        int contentH = pauseRowHeight() * 5 + pauseButtonGap() * 4;
        int inset = pauseLayoutInset();
        int innerH = Math.max(contentH, previewH - inset * 2);
        return previewY + inset + Math.max(0, (innerH - contentH) / 2);
    }

    private int pauseCenterY() {
        return pauseMenuTop() + (pauseRowHeight() + pauseButtonGap()) * 2;
    }

    private int pauseVanillaRowWidth() {
        return 4 * pauseButtonSize() + 3 * pauseButtonGap();
    }

    private int pauseVanillaTargetX() {
        PauseButtonAnchor[] anchors = PauseMenuButtonLayout.anchors();
        boolean[] visible = PauseMenuButtonLayout.visibility();
        if (draggingAction >= 0 && draggingAction < actionCount()) {
            visible[draggingAction] = dragPauseAnchor != null;
            if (dragPauseAnchor != null) {
                anchors[draggingAction] = dragPauseAnchor;
            }
        }

        int modCount = actionsAtPause(
                PauseButtonAnchor.CENTER,
                anchors,
                PauseMenuButtonLayout.orders(),
                visible,
                -1).size();
        int modW = modCount * pauseButtonSize()
                + Math.max(0, modCount - 1) * pauseButtonGap();
        int totalW = pauseVanillaRowWidth()
                + (modCount > 0 ? pauseButtonGap() + modW : 0);
        return previewX + (previewW - totalW) / 2;
    }

    private int animatedPauseVanillaRowX() {
        int target = pauseVanillaTargetX();
        if (Double.isNaN(animatedPauseVanillaX)) {
            animatedPauseVanillaX = target;
        } else {
            animatedPauseVanillaX += (target - animatedPauseVanillaX) * 0.32;
        }
        return (int) Math.round(animatedPauseVanillaX);
    }

    private int previewHForActions() {
        return previewH;
    }

    private void buildPreviewTargets() {
        if (mode == PreviewMode.PAUSE_MENU) {
            buildPauseMenuTargets();
            return;
        }
        ActionButtonCorner[] corners = cornersForMode();
        int[] order = ordersForMode();
        boolean[] visible = visibilityForMode();

        if (draggingAction >= 0) {
            if (dragCorner == null) {
                visible[draggingAction] = false;
            } else {
                visible[draggingAction] = true;
                corners[draggingAction] = dragCorner;
                applyTemporaryInsertion(corners, order, visible);
            }
        }

        ActionButtonLayout.arrange(
                targetX, targetY, corners, order, visible, 4,
                previewX, previewY, previewW, previewHForActions(),
                BUTTON_W, BUTTON_H, BUTTON_GAP, PREVIEW_MARGIN);
    }

    private void buildPauseMenuTargets() {
        Arrays.fill(targetX, -100);
        Arrays.fill(targetY, -100);
        PauseButtonAnchor[] anchors = PauseMenuButtonLayout.anchors();
        int[] order = PauseMenuButtonLayout.orders();
        boolean[] visible = PauseMenuButtonLayout.visibility();

        if (draggingAction >= 0) {
            if (dragPauseAnchor == null) {
                visible[draggingAction] = false;
            } else {
                visible[draggingAction] = true;
                anchors[draggingAction] = dragPauseAnchor;
                applyTemporaryPauseInsertion(anchors, order, visible);
            }
        }

        ActionButtonCorner[] corners = new ActionButtonCorner[actionCount()];
        boolean[] cornerVisible = new boolean[actionCount()];
        for (int i = 0; i < actionCount(); i++) {
            corners[i] = cornerForPauseAnchor(anchors[i]);
            cornerVisible[i] = visible[i] && anchors[i] != PauseButtonAnchor.CENTER;
        }
        ActionButtonLayout.arrange(
                targetX, targetY, corners, order, cornerVisible, actionCount(),
                previewX, previewY, previewW, previewH,
                pauseButtonSize(), pauseButtonSize(),
                pauseButtonGap(), PREVIEW_MARGIN);

        List<Integer> centerActions = actionsAtPause(
                PauseButtonAnchor.CENTER, anchors, order, visible, -1);
        int modW = centerActions.size() * pauseButtonSize()
                + Math.max(0, centerActions.size() - 1) * pauseButtonGap();
        int totalW = pauseVanillaRowWidth()
                + (centerActions.isEmpty() ? 0 : pauseButtonGap() + modW);
        int vanillaX = previewX + (previewW - totalW) / 2;
        int startX = vanillaX + pauseVanillaRowWidth()
                + (centerActions.isEmpty() ? 0 : pauseButtonGap());
        for (int slot = 0; slot < centerActions.size(); slot++) {
            int action = centerActions.get(slot);
            targetX[action] = startX
                    + slot * (pauseButtonSize() + pauseButtonGap());
            targetY[action] = pauseCenterY();
        }
    }

    private void applyTemporaryPauseInsertion(
            PauseButtonAnchor[] anchors,
            int[] order,
            boolean[] visible) {
        List<Integer> actions = actionsAtPause(
                dragPauseAnchor, anchors, order, visible, draggingAction);
        int insert = Math.max(0, Math.min(dragInsertIndex, actions.size()));
        actions.add(insert, draggingAction);
        for (int i = 0; i < actions.size(); i++) {
            order[actions.get(i)] = i;
        }
    }

    private void applyTemporaryInsertion(
            ActionButtonCorner[] corners,
            int[] order,
            boolean[] visible) {
        List<Integer> actions = actionsInCorner(
                dragCorner, corners, order, visible, draggingAction);
        int insert = Math.max(0, Math.min(dragInsertIndex, actions.size()));
        actions.add(insert, draggingAction);
        for (int i = 0; i < actions.size(); i++) {
            order[actions.get(i)] = i;
        }
    }

    private void animatePreviewActions() {
        boolean[] visible = visibilityForMode();
        for (int i = 0; i < actionCount(); i++) {
            boolean targetVisible = targetX[i] > -50
                    && (draggingAction != i
                    || (mode == PreviewMode.PAUSE_MENU
                    ? dragPauseAnchor != null : dragCorner != null));
            if (!targetVisible) {
                actionX[i] = -100;
                actionY[i] = -100;
                if (!visible[i] || draggingAction == i) {
                    animatedX[i] = Double.NaN;
                    animatedY[i] = Double.NaN;
                }
                continue;
            }
            if (Double.isNaN(animatedX[i])) {
                animatedX[i] = targetX[i];
                animatedY[i] = targetY[i];
            } else {
                animatedX[i] += (targetX[i] - animatedX[i]) * 0.32;
                animatedY[i] += (targetY[i] - animatedY[i]) * 0.32;
            }
            actionX[i] = (int) Math.round(animatedX[i]);
            actionY[i] = (int) Math.round(animatedY[i]);
        }
    }

    private void layoutTrayActions() {
        Arrays.fill(trayActionX, -100);
        Arrays.fill(trayActionY, -100);
        boolean[] visible = visibilityForMode();
        int buttonW = currentButtonWidth();
        int buttonH = currentButtonHeight();
        int slot = 0;
        for (int i = 0; i < actionCount(); i++) {
            if (visible[i] || draggingAction == i) continue;
            trayActionX[i] = trayX() + (TRAY_W - buttonW) / 2;
            trayActionY[i] = trayY() + 9 + slot * (buttonH + 6);
            slot++;
        }
    }

    private void drawTray(GuiGraphics context) {
        int background = draggingAction >= 0 ? 0xAA4A1717 : 0xAA171717;
        int border = draggingAction >= 0 ? 0xFFD45A5A : 0xFF454545;
        context.fill(trayX(), trayY(), trayX() + TRAY_W, trayY() + trayH(), background);
        context.fill(trayX(), trayY(), trayX() + TRAY_W, trayY() + 1, border);
        context.fill(trayX(), trayY() + trayH() - 1,
                trayX() + TRAY_W, trayY() + trayH(), border);
        context.fill(trayX(), trayY(), trayX() + 1, trayY() + trayH(), border);
        context.fill(trayX() + TRAY_W - 1, trayY(),
                trayX() + TRAY_W, trayY() + trayH(), border);

        if (draggingAction >= 0) {
            Component trash = Component.literal("🗑");
            int trashWidth = font.width(trash);
            float scale = 3f;
            context.pose().pushPose();
            context.pose().translate(
                    trayX() + TRAY_W / 2f,
                    trayY() + trayH() / 2f - font.lineHeight * scale / 2f, 0);
            context.pose().scale(scale, scale, 1);
            context.drawString(font, trash, -trashWidth / 2, 0, 0xFFD45A5A, false);
            context.pose().popPose();
        } else if (!hasHiddenActions()) {
            drawEmptyTrayText(context);
        }
    }

    private boolean hasHiddenActions() {
        boolean[] visible = visibilityForMode();
        for (int i = 0; i < actionCount(); i++) {
            if (!visible[i]) return true;
        }
        return false;
    }

    private void drawEmptyTrayText(GuiGraphics context) {
        String text = Component.translatable(
                "better_screenshots.config.actions.remove_hint").getString();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int maxWidth = TRAY_W - 10;
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(candidate) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (!line.isEmpty()) line.append(" ");
                line.append(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());

        int lineHeight = font.lineHeight + 2;
        int startY = trayY() + (trayH() - lines.size() * lineHeight) / 2;
        for (int i = 0; i < lines.size(); i++) {
            context.drawCenteredString(font, Component.literal(lines.get(i)),
                    trayX() + TRAY_W / 2, startY + i * lineHeight, 0xFF8A8A8A);
        }
    }

    private void drawCornerArrows(GuiGraphics context) {
        if (draggingAction < 0) return;
        for (ActionButtonCorner corner : ActionButtonCorner.values()) {
            if (mode == PreviewMode.CONFIG_MENU
                    && (corner == ActionButtonCorner.BOTTOM_LEFT
                    || corner == ActionButtonCorner.BOTTOM_RIGHT)) {
                continue;
            }
            int color = corner == dragCorner
                    || mode == PreviewMode.PAUSE_MENU
                    && cornerForPauseAnchor(dragPauseAnchor) == corner
                    ? 0xFFFFFFFF : 0x77999999;
            drawCornerArrow(context, corner, color);
        }
    }

    private void drawCornerArrow(
            GuiGraphics context,
            ActionButtonCorner corner,
            int color) {
        boolean left = corner == ActionButtonCorner.TOP_LEFT
                || corner == ActionButtonCorner.BOTTOM_LEFT;
        boolean top = corner == ActionButtonCorner.TOP_LEFT
                || corner == ActionButtonCorner.TOP_RIGHT;
        int tipX = left ? previewX + 3 : previewX + previewW - 4;
        int tipY = top ? previewY + 3 : previewY + previewHForActions() - 4;
        int stepX = left ? 1 : -1;
        int stepY = top ? 1 : -1;

        for (int i = 0; i < 6; i++) {
            int x = tipX + stepX * i;
            int y = tipY + stepY * i;
            context.fill(x, y, x + 1, y + 1, color);
        }
        for (int i = 0; i < 4; i++) {
            context.fill(tipX + stepX * i, tipY,
                    tipX + stepX * i + 1, tipY + 1, color);
            context.fill(tipX, tipY + stepY * i,
                    tipX + 1, tipY + stepY * i + 1, color);
        }
    }

    private void drawActions(GuiGraphics context, int mouseX, int mouseY) {
        hoveredAction = -1;
        ResourceLocation[] icons = iconsForMode(false);
        ResourceLocation[] hoverIcons = iconsForMode(true);
        boolean[] visible = visibilityForMode();
        int buttonW = currentButtonWidth();
        int buttonH = currentButtonHeight();

        for (int i = 0; i < actionCount(); i++) {
            if (i == draggingAction) continue;
            if (draggingAction >= 0 && !visible[i]) continue;
            int x = visible[i] ? actionX[i] : trayActionX[i];
            int y = visible[i] ? actionY[i] : trayActionY[i];
            if (x < -50) continue;
            boolean hovered = mouseX >= x && mouseX <= x + buttonW
                    && mouseY >= y && mouseY <= y + buttonH;
            if (hovered) hoveredAction = i;
            drawActionIcon(context, hovered ? hoverIcons[i] : icons[i],
                    x, y, buttonW, buttonH, hovered);
        }

        if (draggingAction >= 0 && draggingAction < actionCount()) {
            drawActionIcon(context, hoverIcons[draggingAction],
                    (int) Math.round(dragMouseX - buttonW / 2.0),
                    (int) Math.round(dragMouseY - buttonH / 2.0),
                    buttonW, buttonH, true);
        }
    }

    private void drawActionIcon(
            GuiGraphics context,
            ResourceLocation icon,
            int x,
            int y,
            int width,
            int height,
            boolean hovered) {
        if (mode != PreviewMode.PAUSE_MENU) {
            context.blit(icon, x, y, width, height,
                    0f, 0f, width, height, width, height);
            return;
        }

        context.fill(x, y, x + width, y + height,
                hovered ? 0xFFB0B0B0 : 0xFF777777);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                hovered ? 0xFF555555 : 0xFF444444);
        int inset = Math.max(1, width / 9);
        int iconW = Math.max(1, width - inset * 2);
        int iconH = Math.max(1, height - inset * 2);
        context.blit(icon, x + inset, y + inset, iconW, iconH,
                0f, 0f, iconW, iconH, iconW, iconH);
    }

    private int currentButtonWidth() {
        return mode == PreviewMode.PAUSE_MENU ? pauseButtonSize() : BUTTON_W;
    }

    private int currentButtonHeight() {
        return mode == PreviewMode.PAUSE_MENU ? pauseButtonSize() : BUTTON_H;
    }

    private void drawActionTooltip(
            GuiGraphics context,
            int action,
            int mouseX,
            int mouseY) {
        Component label = actionLabel(action);
        int labelW = font.width(label) + 8;
        int labelX = Math.max(panelX() + SIDEBAR_W + 4,
                Math.min(mouseX + 8, panelX() + panelW() - labelW - 6));
        int labelY = Math.max(panelY() + 26,
                Math.min(mouseY + 8, panelY() + panelH() - 18));
        context.fill(labelX - 2, labelY - 2, labelX + labelW, labelY + 11, 0xEE111111);
        context.drawString(font, label, labelX + 2, labelY, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            buildPreviewTargets();
            animatePreviewActions();
            layoutTrayActions();
            boolean[] visible = visibilityForMode();
            int buttonW = currentButtonWidth();
            int buttonH = currentButtonHeight();
            for (int i = 0; i < actionCount(); i++) {
                int x = visible[i] ? actionX[i] : trayActionX[i];
                int y = visible[i] ? actionY[i] : trayActionY[i];
                if (mouseX >= x && mouseX <= x + buttonW
                        && mouseY >= y && mouseY <= y + buttonH) {
                    draggingAction = i;
                    dragMouseX = mouseX;
                    dragMouseY = mouseY;
                    updateDragTarget(dragMouseX, dragMouseY);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && draggingAction >= 0) {
            dragMouseX = mouseX;
            dragMouseY = mouseY;
            updateDragTarget(dragMouseX, dragMouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingAction >= 0) {
            dragMouseX = mouseX;
            dragMouseY = mouseY;
            updateDragTarget(dragMouseX, dragMouseY);
            if (isInsidePreview(dragMouseX, dragMouseY)
                    && (mode == PreviewMode.PAUSE_MENU
                    ? dragPauseAnchor != null : dragCorner != null)) {
                if (mode == PreviewMode.PAUSE_MENU) {
                    applyPauseDrop(draggingAction, dragPauseAnchor, dragInsertIndex);
                } else {
                    applyPreviewDrop(draggingAction, dragCorner, dragInsertIndex);
                }
            } else if (isInsideTray(dragMouseX, dragMouseY)) {
                setVisible(draggingAction, false);
                normalizeAllOrders();
            }
            draggingAction = -1;
            dragCorner = null;
            dragPauseAnchor = null;
            dragInsertIndex = -1;
            Arrays.fill(animatedX, Double.NaN);
            Arrays.fill(animatedY, Double.NaN);
            ScreenshotConfig.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateDragTarget(double mouseX, double mouseY) {
        if (draggingAction < 0 || !isInsidePreview(mouseX, mouseY)) {
            dragCorner = null;
            dragPauseAnchor = null;
            dragInsertIndex = -1;
            return;
        }
        if (mode == PreviewMode.PAUSE_MENU) {
            dragPauseAnchor = pauseAnchorForCorner(ActionButtonLayout.nearestCorner(
                    mouseX, mouseY, previewX, previewY, previewW, previewH));
            dragCorner = null;
            dragInsertIndex = pauseInsertionIndex(mouseX, dragPauseAnchor);
            return;
        }
        dragPauseAnchor = null;
        dragCorner = mode == PreviewMode.CONFIG_MENU
                ? topCornerForMouse(mouseX)
                : ActionButtonLayout.nearestCorner(
                        mouseX, mouseY, previewX, previewY,
                        previewW, previewHForActions());
        dragInsertIndex = insertionIndex(mouseX, dragCorner);
    }

    private ActionButtonCorner topCornerForMouse(double mouseX) {
        return mouseX < previewX + previewW / 2.0
                ? ActionButtonCorner.TOP_LEFT
                : ActionButtonCorner.TOP_RIGHT;
    }

    private int pauseInsertionIndex(double mouseX, PauseButtonAnchor anchor) {
        PauseButtonAnchor[] anchors = PauseMenuButtonLayout.anchors();
        int[] order = PauseMenuButtonLayout.orders();
        boolean[] visible = PauseMenuButtonLayout.visibility();
        List<Integer> actions = actionsAtPause(
                anchor, anchors, order, visible, draggingAction);
        int totalCount = actions.size() + 1;
        int totalW = totalCount * pauseButtonSize()
                + Math.max(0, totalCount - 1) * pauseButtonGap();
        int startX;
        if (anchor == PauseButtonAnchor.CENTER) {
            int combinedW = pauseVanillaRowWidth() + pauseButtonGap() + totalW;
            startX = previewX + (previewW - combinedW) / 2
                    + pauseVanillaRowWidth() + pauseButtonGap();
        } else {
            boolean left = anchor == PauseButtonAnchor.TOP_LEFT
                    || anchor == PauseButtonAnchor.BOTTOM_LEFT;
            startX = left
                    ? previewX + PREVIEW_MARGIN
                    : previewX + previewW - PREVIEW_MARGIN - totalW;
        }
        int slot = (int) Math.floor(
                (mouseX - startX
                        + (pauseButtonSize() + pauseButtonGap()) / 2.0)
                        / (pauseButtonSize() + pauseButtonGap()));
        return Math.max(0, Math.min(actions.size(), slot));
    }

    private int insertionIndex(double mouseX, ActionButtonCorner corner) {
        ActionButtonCorner[] corners = cornersForMode();
        int[] order = ordersForMode();
        boolean[] visible = visibilityForMode();
        List<Integer> actions = actionsInCorner(corner, corners, order, visible, draggingAction);
        int totalCount = actions.size() + 1;
        int totalW = totalCount * BUTTON_W + Math.max(0, totalCount - 1) * BUTTON_GAP;
        boolean left = corner == ActionButtonCorner.TOP_LEFT
                || corner == ActionButtonCorner.BOTTOM_LEFT;
        int startX = left
                ? previewX + PREVIEW_MARGIN
                : previewX + previewW - PREVIEW_MARGIN - totalW;
        int slot = (int) Math.floor(
                (mouseX - startX + (BUTTON_W + BUTTON_GAP) / 2.0)
                        / (BUTTON_W + BUTTON_GAP));
        return Math.max(0, Math.min(actions.size(), slot));
    }

    private void applyPreviewDrop(
            int action,
            ActionButtonCorner corner,
            int insertIndex) {
        setVisible(action, true);
        setCorner(action, corner);
        ActionButtonCorner[] corners = cornersForMode();
        int[] order = ordersForMode();
        boolean[] visible = visibilityForMode();
        List<Integer> actions = actionsInCorner(corner, corners, order, visible, action);
        actions.add(Math.max(0, Math.min(insertIndex, actions.size())), action);
        for (int i = 0; i < actions.size(); i++) {
            setOrder(actions.get(i), i);
        }
        normalizeAllOrdersExcept(corner);
    }

    private void applyPauseDrop(
            int action,
            PauseButtonAnchor anchor,
            int insertIndex) {
        setVisible(action, true);
        setPauseAnchor(action, anchor);
        PauseButtonAnchor[] anchors = PauseMenuButtonLayout.anchors();
        int[] order = PauseMenuButtonLayout.orders();
        boolean[] visible = PauseMenuButtonLayout.visibility();
        List<Integer> actions = actionsAtPause(anchor, anchors, order, visible, action);
        actions.add(Math.max(0, Math.min(insertIndex, actions.size())), action);
        for (int i = 0; i < actions.size(); i++) {
            setOrder(actions.get(i), i);
        }
        normalizePauseOrdersExcept(anchor);
    }

    private List<Integer> actionsInCorner(
            ActionButtonCorner corner,
            ActionButtonCorner[] corners,
            int[] order,
            boolean[] visible,
            int excludedAction) {
        List<Integer> actions = new ArrayList<>();
        for (int i = 0; i < actionCount(); i++) {
            if (i != excludedAction && visible[i]
                    && safe(corners[i], defaultCorner(i)) == corner) {
                actions.add(i);
            }
        }
        actions.sort(Comparator
                .comparingInt((Integer action) -> order[action])
                .thenComparingInt(Integer::intValue));
        return actions;
    }

    private List<Integer> actionsAtPause(
            PauseButtonAnchor anchor,
            PauseButtonAnchor[] anchors,
            int[] order,
            boolean[] visible,
            int excludedAction) {
        List<Integer> actions = new ArrayList<>();
        for (int i = 0; i < PauseMenuButtonLayout.ACTION_COUNT; i++) {
            if (i != excludedAction && visible[i] && anchors[i] == anchor) {
                actions.add(i);
            }
        }
        actions.sort(Comparator
                .comparingInt((Integer action) -> order[action])
                .thenComparingInt(Integer::intValue));
        return actions;
    }

    private void normalizeAllOrders() {
        if (mode == PreviewMode.PAUSE_MENU) {
            normalizePauseOrdersExcept(null);
            return;
        }
        normalizeAllOrdersExcept(null);
    }

    private void normalizeAllOrdersExcept(ActionButtonCorner excludedCorner) {
        ActionButtonCorner[] corners = cornersForMode();
        int[] order = ordersForMode();
        boolean[] visible = visibilityForMode();
        for (ActionButtonCorner corner : ActionButtonCorner.values()) {
            if (corner == excludedCorner) continue;
            List<Integer> actions = actionsInCorner(corner, corners, order, visible, -1);
            for (int i = 0; i < actions.size(); i++) {
                setOrder(actions.get(i), i);
            }
        }
    }

    private void normalizePauseOrdersExcept(PauseButtonAnchor excludedAnchor) {
        PauseButtonAnchor[] anchors = PauseMenuButtonLayout.anchors();
        int[] order = PauseMenuButtonLayout.orders();
        boolean[] visible = PauseMenuButtonLayout.visibility();
        for (PauseButtonAnchor anchor : PauseButtonAnchor.values()) {
            if (anchor == excludedAnchor) continue;
            List<Integer> actions = actionsAtPause(anchor, anchors, order, visible, -1);
            for (int i = 0; i < actions.size(); i++) {
                setOrder(actions.get(i), i);
            }
        }
    }

    private ActionButtonCorner cornerForPauseAnchor(PauseButtonAnchor anchor) {
        if (anchor == null || anchor == PauseButtonAnchor.CENTER) return null;
        return switch (anchor) {
            case TOP_LEFT -> ActionButtonCorner.TOP_LEFT;
            case TOP_RIGHT -> ActionButtonCorner.TOP_RIGHT;
            case BOTTOM_LEFT -> ActionButtonCorner.BOTTOM_LEFT;
            case BOTTOM_RIGHT -> ActionButtonCorner.BOTTOM_RIGHT;
            case CENTER -> null;
        };
    }

    private PauseButtonAnchor pauseAnchorForCorner(ActionButtonCorner corner) {
        return switch (corner) {
            case TOP_LEFT -> PauseButtonAnchor.TOP_LEFT;
            case TOP_RIGHT -> PauseButtonAnchor.TOP_RIGHT;
            case BOTTOM_LEFT -> PauseButtonAnchor.BOTTOM_LEFT;
            case BOTTOM_RIGHT -> PauseButtonAnchor.BOTTOM_RIGHT;
        };
    }

    private boolean isInsidePreview(double x, double y) {
        return x >= previewX && x <= previewX + previewW
                && y >= previewY && y <= previewY + previewHForActions();
    }

    private boolean isInsideTray(double x, double y) {
        return x >= trayX() && x <= trayX() + TRAY_W
                && y >= trayY() && y <= trayY() + trayH();
    }

    private ActionButtonCorner[] cornersForMode() {
        ScreenshotConfig config = ScreenshotConfig.get();
        return switch (mode) {
            case MINI_PREVIEW -> new ActionButtonCorner[] {
                    safe(config.miniPreviewShowCorner, ActionButtonCorner.TOP_RIGHT),
                    safe(config.miniPreviewCopyCorner, ActionButtonCorner.TOP_RIGHT),
                    safe(config.miniPreviewUploadCorner, ActionButtonCorner.TOP_RIGHT),
                    safe(config.miniPreviewDeleteCorner, ActionButtonCorner.TOP_RIGHT)
            };
            case GALLERY_THUMBNAIL -> new ActionButtonCorner[] {
                    safe(config.galleryShowCorner, ActionButtonCorner.TOP_RIGHT),
                    safe(config.galleryCopyCorner, ActionButtonCorner.TOP_RIGHT),
                    safe(config.galleryUploadCorner, ActionButtonCorner.TOP_RIGHT),
                    safe(config.galleryDeleteCorner, ActionButtonCorner.TOP_RIGHT)
            };
            case CONFIG_MENU -> new ActionButtonCorner[] {
                    topCorner(config.configMenuShowCorner),
                    topCorner(config.configMenuCopyCorner),
                    topCorner(config.configMenuUploadCorner),
                    topCorner(config.configMenuDeleteCorner)
            };
            case FULLSCREEN_PREVIEW -> new ActionButtonCorner[] {
                    safe(config.fullscreenCloseCorner, ActionButtonCorner.TOP_LEFT),
                    safe(config.fullscreenCopyCorner, ActionButtonCorner.TOP_RIGHT),
                    safe(config.fullscreenUploadCorner, ActionButtonCorner.TOP_RIGHT),
                    safe(config.fullscreenDeleteCorner, ActionButtonCorner.TOP_RIGHT)
            };
            case PAUSE_MENU -> new ActionButtonCorner[0];
        };
    }

    private int[] ordersForMode() {
        ScreenshotConfig config = ScreenshotConfig.get();
        return switch (mode) {
            case MINI_PREVIEW -> new int[] {
                    config.miniPreviewShowOrder,
                    config.miniPreviewCopyOrder,
                    config.miniPreviewUploadOrder,
                    config.miniPreviewDeleteOrder
            };
            case GALLERY_THUMBNAIL -> new int[] {
                    config.galleryShowOrder,
                    config.galleryCopyOrder,
                    config.galleryUploadOrder,
                    config.galleryDeleteOrder
            };
            case CONFIG_MENU -> new int[] {
                    config.configMenuShowOrder,
                    config.configMenuCopyOrder,
                    config.configMenuUploadOrder,
                    config.configMenuDeleteOrder
            };
            case FULLSCREEN_PREVIEW -> new int[] {
                    config.fullscreenCloseOrder,
                    config.fullscreenCopyOrder,
                    config.fullscreenUploadOrder,
                    config.fullscreenDeleteOrder
            };
            case PAUSE_MENU -> PauseMenuButtonLayout.orders();
        };
    }

    private boolean[] visibilityForMode() {
        ScreenshotConfig config = ScreenshotConfig.get();
        return switch (mode) {
            case MINI_PREVIEW -> new boolean[] {
                    config.miniPreviewShowVisible,
                    config.miniPreviewCopyVisible,
                    config.miniPreviewUploadVisible,
                    config.miniPreviewDeleteVisible
            };
            case GALLERY_THUMBNAIL -> new boolean[] {
                    config.galleryShowVisible,
                    config.galleryCopyVisible,
                    config.galleryUploadVisible,
                    config.galleryDeleteVisible
            };
            case CONFIG_MENU -> new boolean[] {
                    config.configMenuShowVisible,
                    config.configMenuCopyVisible,
                    config.configMenuUploadVisible,
                    config.configMenuDeleteVisible
            };
            case FULLSCREEN_PREVIEW -> new boolean[] {
                    config.fullscreenCloseVisible,
                    config.fullscreenCopyVisible,
                    config.fullscreenUploadVisible,
                    config.fullscreenDeleteVisible
            };
            case PAUSE_MENU -> PauseMenuButtonLayout.visibility();
        };
    }

    private void setCorner(int action, ActionButtonCorner corner) {
        ScreenshotConfig config = ScreenshotConfig.get();
        if (mode == PreviewMode.CONFIG_MENU) {
            corner = topCorner(corner);
        }
        switch (mode) {
            case MINI_PREVIEW -> {
                if (action == 0) config.miniPreviewShowCorner = corner;
                if (action == 1) config.miniPreviewCopyCorner = corner;
                if (action == 2) config.miniPreviewUploadCorner = corner;
                if (action == 3) config.miniPreviewDeleteCorner = corner;
            }
            case GALLERY_THUMBNAIL -> {
                if (action == 0) config.galleryShowCorner = corner;
                if (action == 1) config.galleryCopyCorner = corner;
                if (action == 2) config.galleryUploadCorner = corner;
                if (action == 3) config.galleryDeleteCorner = corner;
            }
            case CONFIG_MENU -> {
                if (action == 0) config.configMenuShowCorner = corner;
                if (action == 1) config.configMenuCopyCorner = corner;
                if (action == 2) config.configMenuUploadCorner = corner;
                if (action == 3) config.configMenuDeleteCorner = corner;
            }
            case FULLSCREEN_PREVIEW -> {
                if (action == 0) config.fullscreenCloseCorner = corner;
                if (action == 1) config.fullscreenCopyCorner = corner;
                if (action == 2) config.fullscreenUploadCorner = corner;
                if (action == 3) config.fullscreenDeleteCorner = corner;
            }
            case PAUSE_MENU -> {
            }
        }
    }

    private void setPauseAnchor(int action, PauseButtonAnchor anchor) {
        ScreenshotConfig config = ScreenshotConfig.get();
        ActionButtonCorner corner = PauseMenuButtonLayout.fromAnchor(anchor);
        if (action == PauseMenuButtonLayout.SETTINGS) config.pause26_1SettingsCorner = corner;
        if (action == PauseMenuButtonLayout.GALLERY) config.pause26_1GalleryCorner = corner;
        if (action == PauseMenuButtonLayout.SCREENSHOT) config.pause26_1ScreenshotCorner = corner;
    }

    private void setVisible(int action, boolean visible) {
        ScreenshotConfig config = ScreenshotConfig.get();
        switch (mode) {
            case MINI_PREVIEW -> {
                if (action == 0) config.miniPreviewShowVisible = visible;
                if (action == 1) config.miniPreviewCopyVisible = visible;
                if (action == 2) config.miniPreviewUploadVisible = visible;
                if (action == 3) config.miniPreviewDeleteVisible = visible;
            }
            case GALLERY_THUMBNAIL -> {
                if (action == 0) config.galleryShowVisible = visible;
                if (action == 1) config.galleryCopyVisible = visible;
                if (action == 2) config.galleryUploadVisible = visible;
                if (action == 3) config.galleryDeleteVisible = visible;
            }
            case CONFIG_MENU -> {
                if (action == 0) config.configMenuShowVisible = visible;
                if (action == 1) config.configMenuCopyVisible = visible;
                if (action == 2) config.configMenuUploadVisible = visible;
                if (action == 3) config.configMenuDeleteVisible = visible;
            }
            case FULLSCREEN_PREVIEW -> {
                if (action == 0) config.fullscreenCloseVisible = visible;
                if (action == 1) config.fullscreenCopyVisible = visible;
                if (action == 2) config.fullscreenUploadVisible = visible;
                if (action == 3) config.fullscreenDeleteVisible = visible;
            }
            case PAUSE_MENU -> {
                if (action == PauseMenuButtonLayout.SETTINGS) config.pause26_1SettingsVisible = visible;
                if (action == PauseMenuButtonLayout.GALLERY) config.pause26_1GalleryVisible = visible;
                if (action == PauseMenuButtonLayout.SCREENSHOT) config.pause26_1ScreenshotVisible = visible;
            }
        }
    }

    private void setOrder(int action, int order) {
        ScreenshotConfig config = ScreenshotConfig.get();
        switch (mode) {
            case MINI_PREVIEW -> {
                if (action == 0) config.miniPreviewShowOrder = order;
                if (action == 1) config.miniPreviewCopyOrder = order;
                if (action == 2) config.miniPreviewUploadOrder = order;
                if (action == 3) config.miniPreviewDeleteOrder = order;
            }
            case GALLERY_THUMBNAIL -> {
                if (action == 0) config.galleryShowOrder = order;
                if (action == 1) config.galleryCopyOrder = order;
                if (action == 2) config.galleryUploadOrder = order;
                if (action == 3) config.galleryDeleteOrder = order;
            }
            case CONFIG_MENU -> {
                if (action == 0) config.configMenuShowOrder = order;
                if (action == 1) config.configMenuCopyOrder = order;
                if (action == 2) config.configMenuUploadOrder = order;
                if (action == 3) config.configMenuDeleteOrder = order;
            }
            case FULLSCREEN_PREVIEW -> {
                if (action == 0) config.fullscreenCloseOrder = order;
                if (action == 1) config.fullscreenCopyOrder = order;
                if (action == 2) config.fullscreenUploadOrder = order;
                if (action == 3) config.fullscreenDeleteOrder = order;
            }
            case PAUSE_MENU -> {
                if (action == PauseMenuButtonLayout.SETTINGS) config.pause26_1SettingsOrder = order;
                if (action == PauseMenuButtonLayout.GALLERY) config.pause26_1GalleryOrder = order;
                if (action == PauseMenuButtonLayout.SCREENSHOT) config.pause26_1ScreenshotOrder = order;
            }
        }
    }

    private ActionButtonCorner defaultCorner(int action) {
        return mode == PreviewMode.FULLSCREEN_PREVIEW && action == 0
                ? ActionButtonCorner.TOP_LEFT
                : ActionButtonCorner.TOP_RIGHT;
    }

    private ResourceLocation[] iconsForMode(boolean hovered) {
        if (mode == PreviewMode.PAUSE_MENU) {
            return new ResourceLocation[] {
                    ICON_PAUSE_SETTINGS,
                    ICON_PAUSE_GALLERY,
                    ICON_PAUSE_SCREENSHOT,
                    ICON_PAUSE_SCREENSHOT
            };
        }
        ResourceLocation first = mode == PreviewMode.FULLSCREEN_PREVIEW
                ? (hovered ? ICON_CLOSE_H : ICON_CLOSE)
                : (hovered ? ICON_SHOW_H : ICON_SHOW);
        return new ResourceLocation[] {
                first,
                hovered ? ICON_COPY_H : ICON_COPY,
                hovered ? ICON_UPLOAD_H : ICON_UPLOAD,
                hovered ? ICON_DELETE_H : ICON_DELETE
        };
    }

    private Component actionLabel(int action) {
        if (mode == PreviewMode.PAUSE_MENU) {
            return Component.translatable(switch (action) {
                case PauseMenuButtonLayout.SETTINGS -> "better_screenshots.menu.settings";
                case PauseMenuButtonLayout.GALLERY -> "better_screenshots.menu.gallery";
                default -> "better_screenshots.menu.screenshot";
            });
        }
        if (action == 0) {
            return Component.translatable(mode == PreviewMode.FULLSCREEN_PREVIEW
                    ? "better_screenshots.config.actions.action.close"
                    : "better_screenshots.config.actions.action.show");
        }
        return Component.translatable(switch (action) {
            case 1 -> "better_screenshots.config.actions.action.copy";
            case 2 -> "better_screenshots.config.actions.action.upload";
            default -> "better_screenshots.config.actions.action.delete";
        });
    }

    private ActionButtonCorner safe(
            ActionButtonCorner corner,
            ActionButtonCorner fallback) {
        return corner == null ? fallback : corner;
    }

    private ActionButtonCorner topCorner(ActionButtonCorner corner) {
        return corner == ActionButtonCorner.TOP_LEFT
                || corner == ActionButtonCorner.BOTTOM_LEFT
                ? ActionButtonCorner.TOP_LEFT
                : ActionButtonCorner.TOP_RIGHT;
    }

    private void drawPanel(GuiGraphics context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, 0xEE080808);
        context.fill(x, y, x + w, y + 1, 0xFF555555);
        context.fill(x, y + h - 1, x + w, y + h, 0xFF555555);
        context.fill(x, y, x + 1, y + h, 0xFF555555);
        context.fill(x + w - 1, y, x + w, y + h, 0xFF555555);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
