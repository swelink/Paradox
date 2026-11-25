package cc.swelink.gui.screens;

import cc.swelink.renderer.UiTokens;
import static cc.swelink.gui.screens.ClickGuiDraw.fitLabel;
import static cc.swelink.gui.screens.ClickGuiDraw.label;
import static cc.swelink.gui.screens.ClickGuiDraw.mix;

import cc.swelink.Paradox;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.SpringAnimation;
import cc.swelink.renderer.ThemeColors;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigPanel {
    static final float ROW_HEIGHT = 28;
    private static final float EDGE = (ROW_HEIGHT - ClickGuiStyle.MODULE_FONT_SIZE * 0.73f) / 2;
    private static final float FONT = ClickGuiStyle.MODULE_FONT_SIZE;
    private static final float ACTION_FONT = UiTokens.TEXT_CAPTION;
    private static final float ACTION_GAP = 8;
    private static final float STATUS_FONT = UiTokens.TEXT_CAPTION;
    private static final float STATUS_HEIGHT = 20;
    private static final int DELETE_COLOR = 0xFFFF6B6B;
    private static final String[] ACTIONS = {"Save", "Delete"};
    private final AnimatedInput text = new AnimatedInput();
    private final SpringAnimation scrollMotion = new SpringAnimation();
    private final Map<String, SpringAnimation> hovers = new HashMap<>();
    private final FrameClock clock = new FrameClock();
    private final EditBox name;
    private List<String> configs = List.of();
    private String loaded = "";
    private String status = "";
    private long statusUntil;
    private long lastRefresh;
    private boolean error;
    private boolean focused;
    private float targetScroll;
    private float scroll;
    private float delta;
    private float x;
    private float y;
    private float width;
    private float height;
    private float createLeft;
    private final Map<String, float[]> actionBounds = new HashMap<>();
    private float shapeTop;
    private float shapeHeight;
    private float shapeRadius;

    public void shape(float top, float height, float radius) {
        shapeTop = top;
        shapeHeight = height;
        shapeRadius = radius;
    }

    private void strip(Renderer2D renderer, float top, float stripHeight, int color) {
        float bottom = Math.min(top + stripHeight, y + height);
        top = Math.max(top, y);
        if (bottom <= top) return;
        renderer.sdf.clippedRoundedRect(x, shapeTop, width, shapeHeight, shapeRadius, color, x, top, x + width, bottom);
    }

    public ConfigPanel(Minecraft client) {
        name = new EditBox(client.font, Component.literal("New config"));
        name.setMaxLength(64);
        name.setResponder(text::changed);
        refresh();
    }

    public void refresh() {
        lastRefresh = System.nanoTime();
        try {
            configs = Paradox.CONFIGS.list();
            targetScroll = Math.clamp(targetScroll, 0, maxScroll());
            scroll = Math.clamp(scroll, 0, maxScroll());
        } catch (IOException failure) {
            message(failure.getMessage(), true);
        }
    }

    public float contentHeight() {
        return ROW_HEIGHT * (1 + configs.size()) + (statusVisible() ? STATUS_HEIGHT : 0);
    }

    public void render(Renderer2D renderer, float x, float y, float width, float height,
                       float mouseX, float mouseY, int accent) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        delta = clock.nextDelta();
        if (System.nanoTime() - lastRefresh >= 2_000_000_000L) refresh();
        targetScroll = Math.clamp(targetScroll, 0, maxScroll());
        scroll = Math.clamp(scrollMotion.update(targetScroll, 20, delta), 0, maxScroll());
        drawCreateRow(renderer, mouseX, mouseY, accent);
        float listY = listTop();
        float listHeight = listHeight();
        actionBounds.clear();
        try (var clip = renderer.clips.push(x, listY, width, listHeight)) {
            boolean inList = contains(mouseX, mouseY, x, listY, width, listHeight);
            for (int i = 0; i < configs.size(); i++) {
                float rowY = listY + i * ROW_HEIGHT - scroll;
                if (rowY + ROW_HEIGHT < listY || rowY > listY + listHeight) continue;
                drawConfigRow(renderer, configs.get(i), rowY, inList ? mouseX : -10000, inList ? mouseY : -10000);
            }
        }
        if (statusVisible()) {
            label(renderer, fitLabel(renderer, status, STATUS_FONT, width - EDGE * 2), x + EDGE,
                    y + height - STATUS_HEIGHT / 2, STATUS_FONT, error ? DELETE_COLOR : ClickGuiStyle.muted());
        }
    }

    private int textColor() {
        return ClickGuiStyle.MODULE_TEXT;
    }

    private void drawCreateRow(Renderer2D renderer, float mouseX, float mouseY, int accent) {
        strip(renderer, y, ROW_HEIGHT, ClickGuiStyle.PANEL_INSET);
        String value = name.getValue().strip();
        boolean canCreate = !value.isEmpty() && !configs.contains(value);
        float labelWidth = renderer.text.measure("Create", ACTION_FONT).width();
        float labelX = x + width - EDGE - labelWidth;
        createLeft = labelX;
        float labelHover = hover("create", canCreate && contains(mouseX, mouseY, labelX - 4, y, labelWidth + 8, ROW_HEIGHT));
        int primary = ARGB.opaque(ThemeColors.primary());
        label(renderer, "Create", labelX, y + ROW_HEIGHT / 2, ACTION_FONT,
                canCreate ? mix(mix(textColor(), primary, 0.6f), primary, labelHover) : ClickGuiStyle.disabled());
        float textX = x + EDGE;
        text.draw(renderer, name, "New config", focused, textX, y + 4, labelX - textX - ACTION_GAP, ROW_HEIGHT - 8,
                accent, delta);
    }

    private void drawConfigRow(Renderer2D renderer, String config, float rowY, float mouseX, float mouseY) {
        boolean hovered = contains(mouseX, mouseY, x, rowY, width, ROW_HEIGHT);
        float hover = hover("row:" + config, hovered);
        float right = x + width - EDGE;
        for (int i = ACTIONS.length - 1; i >= 0; i--) {
            String action = ACTIONS[i];
            float labelWidth = renderer.text.measure(action, ACTION_FONT).width();
            float labelX = right - labelWidth;
            boolean over = contains(mouseX, mouseY, labelX - ACTION_GAP / 2, rowY, labelWidth + ACTION_GAP, ROW_HEIGHT);
            float amount = hover(action + ":" + config, over);
            actionBounds.put(action + ":" + config, new float[] {labelX - ACTION_GAP / 2, labelWidth + ACTION_GAP});
            int base = mix(ClickGuiStyle.disabled(), ClickGuiStyle.muted(), hover);
            int target = action.equals("Delete") ? DELETE_COLOR : textColor();
            label(renderer, action, labelX, rowY + ROW_HEIGHT / 2, ACTION_FONT, mix(base, target, amount));
            right = labelX - ACTION_GAP;
        }
        boolean active = config.equals(loaded);
        int color = active ? ARGB.opaque(ThemeColors.primary()) : mix(textColor(), ClickGuiStyle.MODULE_HOVER, hover);
        label(renderer, fitLabel(renderer, config, FONT, right - x - EDGE - 4), x + EDGE, rowY + ROW_HEIGHT / 2, FONT, color);
    }

    private float hover(String id, boolean hovered) {
        return Math.clamp(hovers.computeIfAbsent(id, ignored -> new SpringAnimation()).update(hovered ? 1 : 0, 24, delta), 0, 1);
    }

    public boolean focused() {
        return focused;
    }

    public void resetClock() {
        clock.reset();
    }

    public void unfocus() {
        focused = false;
        name.setFocused(false);
    }

    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT || !contains(mouseX, mouseY, x, y, width, height)) return false;
        if (contains(mouseX, mouseY, x, y, width, ROW_HEIGHT)) {
            if (mouseX >= createLeft - 4) {
                String value = name.getValue().strip();
                if (!value.isEmpty() && !configs.contains(value)) run("save", value);
                return true;
            }
            focused = true;
            name.setFocused(true);
            name.moveCursorToEnd(false);
            text.edited();
            return true;
        }
        unfocus();
        float listY = listTop();
        if (!contains(mouseX, mouseY, x, listY, width, listHeight())) return true;
        int index = (int) ((mouseY - listY + scroll) / ROW_HEIGHT);
        if (index < 0 || index >= configs.size()) return true;
        String config = configs.get(index);
        for (String action : ACTIONS) {
            float[] bounds = actionBounds.get(action + ":" + config);
            if (bounds != null && mouseX >= bounds[0] && mouseX < bounds[0] + bounds[1]) {
                run(action.equals("Save") ? "save" : "delete", config);
                return true;
            }
        }
        run("load", config);
        return true;
    }

    public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        if (!contains(mouseX, mouseY, x, listTop(), width, listHeight())) return false;
        targetScroll = Math.clamp(targetScroll - (float) amount * ROW_HEIGHT, 0, maxScroll());
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (!focused) return false;
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            String value = name.getValue().strip();
            if (!value.isEmpty() && !configs.contains(value)) run("save", value);
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            unfocus();
            return true;
        }
        if (name.keyPressed(event)) {
            text.edited();
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        return focused && name.charTyped(event);
    }

    private void run(String action, String config) {
        if (config.isEmpty()) {
            message("Enter a config name", true);
            return;
        }
        try {
            switch (action) {
                case "save" -> Paradox.CONFIGS.save(config);
                case "load" -> Paradox.CONFIGS.load(config);
                case "delete" -> Paradox.CONFIGS.delete(config);
                default -> throw new IllegalArgumentException("Unknown config action");
            }
            if (action.equals("delete")) {
                if (loaded.equals(config)) loaded = "";
            } else {
                loaded = config;
            }
            if (action.equals("save")) name.setValue("");
            refresh();
            message((action.equals("save") ? "Saved " : action.equals("load") ? "Loaded " : "Deleted ") + config, false);
        } catch (IOException | IllegalArgumentException failure) {
            message(failure.getMessage(), true);
        }
    }

    private void message(String text, boolean error) {
        status = text == null ? "Could not access configs" : text;
        this.error = error;
        statusUntil = System.nanoTime() + (error ? 6_000_000_000L : 3_000_000_000L);
    }

    private boolean statusVisible() {
        return !status.isEmpty() && System.nanoTime() < statusUntil;
    }

    private float listTop() {
        return y + ROW_HEIGHT;
    }

    private float listHeight() {
        return Math.max(0, height - ROW_HEIGHT - (statusVisible() ? STATUS_HEIGHT : 0));
    }

    private float maxScroll() {
        return Math.max(0, configs.size() * ROW_HEIGHT - listHeight());
    }

    private static boolean contains(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
