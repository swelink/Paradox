package cc.swelink.utils.hud;

import cc.swelink.gui.screens.HudEditorScreen;
import cc.swelink.renderer.GuiViewport;
import cc.swelink.renderer.UiScale;
import cc.swelink.renderer.FrameClock;
import cc.swelink.systems.hud.HudElement;
import com.google.gson.JsonObject;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;

public final class HudPlacement {
    private static final float MARGIN = 4;
    private static final float EDGE_SNAP = 12;
    public static final float GRID = 16;
    private final boolean snapToEdges;
    private final float initialCenterOffset;
    private float horizontal;
    private float vertical;
    private float x;
    private float y;
    private float width;
    private float height;
    private float screenWidth;
    private float screenHeight;
    private float anchorHeight;
    private float dragX;
    private float dragY;
    private boolean dragging;
    private boolean horizontalAnchor;
    private boolean centerSnap;
    private boolean centered;
    private boolean leftAligned;
    private float anchoredEdge = Float.NaN;
    private float size = 1;
    private float targetSize = 1;
    private final FrameClock sizeClock = new FrameClock();
    private float initialBottomGap = Float.NaN;

    public HudPlacement(float horizontal, float vertical, boolean snapToEdges) {
        this(horizontal, vertical, snapToEdges, 0);
    }

    public HudPlacement(float horizontal, float vertical, boolean snapToEdges, float initialCenterOffset) {
        this.horizontal = horizontal;
        this.vertical = vertical;
        this.snapToEdges = snapToEdges;
        this.initialCenterOffset = initialCenterOffset;
    }

    public HudPlacement withCenterSnap() {
        centerSnap = true;
        return this;
    }

    public boolean centered() {
        return centerSnap && centered;
    }

    public static float scale(Minecraft client) {
        return UiScale.of(client);
    }

    public float currentScale() {
        return currentScale(Minecraft.getInstance());
    }

    public float renderScale(Minecraft client) {
        size = FrameClock.approach(size, targetSize, 18, sizeClock.nextDelta());
        return currentScale(client);
    }

    private float currentScale(Minecraft client) {
        return scale(client) * size;
    }

    public void resize(double scroll) {
        targetSize = Math.clamp(targetSize * (float) Math.pow(1.1, scroll), 0.5f, 2.5f);
    }

    public JsonObject save() {
        var data = new JsonObject();
        data.addProperty("horizontal", horizontal);
        data.addProperty("vertical", vertical);
        data.addProperty("size", targetSize);
        data.addProperty("centered", centered);
        return data;
    }

    public void restore(float horizontal, float vertical, float size, boolean centered) {
        this.horizontal = horizontal;
        this.vertical = vertical;
        this.size = this.targetSize = size;
        this.centered = centered;
        horizontalAnchor = false;
        anchoredEdge = Float.NaN;
        stopDragging();
        sizeClock.reset();
    }

    public static boolean canDrag(Minecraft client) {
        return client.gui.screen() instanceof ChatScreen || client.gui.screen() instanceof HudEditorScreen;
    }

    public static boolean gridActive(Minecraft client) {
        return client.gui.screen() instanceof HudEditorScreen;
    }

    public void update(Minecraft client, float width, float height) {
        update(client, width, height, height);
    }

    public void update(Minecraft client, float width, float height, float anchorHeight) {
        update(client, width, height, anchorHeight, false);
    }

    public void updateAnchored(Minecraft client, float width, float height, float anchorHeight) {
        update(client, width, height, anchorHeight, true);
    }

    private void update(Minecraft client, float width, float height, float anchorHeight, boolean anchored) {
        updateLayout(width, height, anchorHeight, GuiViewport.atScale(client, currentScale(client)), anchored);
        if (dragging) {
            move(client, client.mouseHandler.getScaledXPos(client.getWindow()) / currentScale(client),
                    client.mouseHandler.getScaledYPos(client.getWindow()) / currentScale(client));
        }
    }

    private void updateLayout(float width, float height, float anchorHeight, GuiViewport viewport, boolean anchored) {
        boolean retainAnchor = anchored && horizontalAnchor && Float.isFinite(anchoredEdge);
        if (!horizontalAnchor) leftAligned = horizontal < 0.5f;
        horizontalAnchor = anchored;
        this.width = width;
        this.height = height;
        this.anchorHeight = anchorHeight;
        screenWidth = viewport.width();
        screenHeight = viewport.height();
        if (vertical < 0) {
            float initial = Float.isNaN(initialBottomGap) ? screenHeight / 2 + initialCenterOffset
                    : screenHeight - height - initialBottomGap / currentScale(Minecraft.getInstance());
            vertical = verticalRange() == 0 ? 0 : Math.clamp((initial - MARGIN) / verticalRange(), 0, 1);
        }
        x = MARGIN + horizontal * availableWidth();
        if (centered() && !dragging) {
            x = MARGIN + availableWidth() / 2;
            horizontal = 0.5f;
            leftAligned = true;
        } else if (retainAnchor && !dragging) {
            float edge = MARGIN + anchoredEdge * (screenWidth - MARGIN * 2);
            x = Math.clamp(edge - (leftAligned ? 0 : width), MARGIN, MARGIN + availableWidth());
            horizontal = availableWidth() == 0 ? 0 : (x - MARGIN) / availableWidth();
        }
        if (!anchored) anchoredEdge = Float.NaN;
        else if (!retainAnchor) rememberEdge();
        y = Math.min(MARGIN + vertical * verticalRange(), MARGIN + availableHeight());
    }

    public float x() { return x; }
    public float y() { return y; }
    public float screenWidth() { return screenWidth; }
    public float screenHeight() { return screenHeight; }
    public boolean lowerHalf() { return vertical > 0.5f; }
    public boolean alignLeft() { return horizontalAnchor ? leftAligned : horizontal < 0.5f; }

    public int touchedEdges() {
        return 0;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void clearSize() {
        width = height = 0;
    }

    public HudElement.Bounds bounds() {
        float scale = currentScale(Minecraft.getInstance());
        return new HudElement.Bounds(x * scale, y * scale, width * scale, height * scale);
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !bounds().contains(event.x(), event.y())) {
            return false;
        }
        float scale = currentScale(Minecraft.getInstance());
        dragX = (float) event.x() / scale - x;
        dragY = (float) event.y() / scale - y;
        dragging = true;
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent event) {
        if (!dragging || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        var client = Minecraft.getInstance();
        move(client, event.x() / currentScale(client), event.y() / currentScale(client));
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (!dragging || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        stopDragging();
        return true;
    }

    public void stopDragging() {
        dragging = false;
    }

    public boolean isDragging() {
        return dragging;
    }

    private void move(Minecraft client, double mouseX, double mouseY) {
        float desiredX = (float) (mouseX - dragX);
        float desiredY = (float) (mouseY - dragY);
        x = snap(desiredX, MARGIN, MARGIN + availableWidth());
        if (centerSnap) {
            float center = MARGIN + availableWidth() / 2;
            centered = Math.abs(desiredX - center) <= (centered ? 10 : 6);
            if (centered) x = center;
        }
        y = snap(desiredY, MARGIN, MARGIN + availableHeight());
        horizontal = availableWidth() == 0 ? 0 : (x - MARGIN) / availableWidth();
        if (horizontalAnchor) {
            leftAligned = centered() || horizontal < 0.5f;
            rememberEdge();
        }
        vertical = y >= MARGIN + availableHeight() - 0.5f ? 1
                : verticalRange() == 0 ? 0 : Math.min(1, (y - MARGIN) / verticalRange());
    }

    private void rememberEdge() {
        anchoredEdge = screenWidth <= MARGIN * 2 ? 0
                : (x + (leftAligned ? 0 : width) - MARGIN) / (screenWidth - MARGIN * 2);
    }

    private float snap(float value, float minimum, float maximum) {
        value = Math.clamp(value, minimum, maximum);
        float reach = EDGE_SNAP / currentScale(Minecraft.getInstance());
        if (value - minimum <= reach) return minimum;
        if (maximum - value <= reach) return maximum;
        if (gridActive(Minecraft.getInstance())) {
            float cell = GRID / size;
            return Math.clamp(Math.round((value - minimum) / cell) * cell + minimum, minimum, maximum);
        }
        if (!snapToEdges) {
            return value;
        }
        float center = (minimum + maximum) / 2;
        float closest = minimum;
        if (Math.abs(value - center) < Math.abs(value - closest)) {
            closest = center;
        }
        if (Math.abs(value - maximum) < Math.abs(value - closest)) {
            closest = maximum;
        }
        return Math.abs(value - closest) <= 6 ? closest : value;
    }

    private float availableWidth() { return Math.max(0, screenWidth - width - MARGIN * 2); }
    private float availableHeight() { return Math.max(0, screenHeight - height - MARGIN * 2); }
    private float verticalRange() { return Math.max(0, screenHeight - anchorHeight - MARGIN * 2); }
}
