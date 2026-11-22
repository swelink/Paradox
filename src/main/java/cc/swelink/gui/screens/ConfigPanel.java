package cc.swelink.gui.screens;

import cc.swelink.Paradox;
import cc.swelink.renderer.AccentWave;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.SpringAnimation;
import cc.swelink.renderer.TextRenderer;
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
import java.util.Locale;
import java.util.Map;

public final class ConfigPanel {
    private static final float ROW_HEIGHT = ClickGuiStyle.ROW_HEIGHT;
    private static final float CARD_INSET = 3;
    private static final float CAP_HEIGHT = 0.73f;
    private static final float NAME_PAD = (ROW_HEIGHT - CARD_INSET * 2 - ClickGuiStyle.MODULE_FONT_SIZE * CAP_HEIGHT) / 2;
    private static final float LABEL_SIZE = 9;
    private static final float LABEL_GAP = 14;
    private static final float STATUS_SIZE = 11;
    private static final float STATUS_HEIGHT = 20;
    private static final int DELETE_COLOR = 0xFFFF6B6B;
    private static final String[] ACTIONS = {"load", "save", "delete"};
    private final AnimatedInput text = new AnimatedInput();
    private final SpringAnimation scrollMotion = new SpringAnimation();
    private final Map<String, SpringAnimation> hovers = new HashMap<>();
    private final FrameClock clock = new FrameClock();
    private final EditBox name;
    private List<String> configs = List.of();
    private String selected = "";
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
        try (var clip = renderer.clips.push(x, listY, width, listHeight)) {
            for (int i = 0; i < configs.size(); i++) {
                float rowY = listY + i * ROW_HEIGHT - scroll;
                if (rowY + ROW_HEIGHT < listY || rowY > listY + listHeight) continue;
                boolean inList = contains(mouseX, mouseY, x, listY, width, listHeight);
                drawConfigRow(renderer, configs.get(i), rowY, inList ? mouseX : -10000, inList ? mouseY : -10000);
            }
        }
        if (statusVisible()) {
            renderer.text.draw(fit(renderer, status, width - NAME_PAD * 2, STATUS_SIZE), x + CARD_INSET + NAME_PAD,
                    y + height - STATUS_HEIGHT / 2 - renderer.text.lineHeight(STATUS_SIZE) / 2, STATUS_SIZE,
                    error ? DELETE_COLOR : ClickGuiStyle.muted());
        }
    }

    private void drawCreateRow(Renderer2D renderer, float mouseX, float mouseY, int accent) {
        boolean hover = contains(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        float lift = hover("create-row", hover || focused);
        card(renderer, y, ARGB.srgbLerp(lift * 0.5f, ClickGuiStyle.card(), ClickGuiStyle.hover()));
        String value = name.getValue().strip();
        boolean canCreate = !value.isEmpty() && !configs.contains(value);
        float labelWidth = renderer.text.measure("CREATE", LABEL_SIZE).width();
        float labelX = x + width - CARD_INSET - NAME_PAD - labelWidth;
        createLeft = labelX;
        float labelHover = hover("create", canCreate && contains(mouseX, mouseY, labelX - 6, y, labelWidth + 12, ROW_HEIGHT));
        label(renderer, "CREATE", labelX, y, canCreate ? AccentWave.text(ClickGuiStyle.muted(), 0.6f + labelHover * 0.4f)
                : AccentWave.text(ClickGuiStyle.disabled(), 0));
        float textX = x + CARD_INSET + NAME_PAD;
        text.draw(renderer, name, "New config", focused, textX, y + CARD_INSET + 4,
                labelX - textX - LABEL_GAP, ROW_HEIGHT - CARD_INSET * 2 - 8, accent, delta);
    }

    private void drawConfigRow(Renderer2D renderer, String config, float rowY, float mouseX, float mouseY) {
        boolean active = config.equals(selected);
        boolean hovered = contains(mouseX, mouseY, x, rowY, width, ROW_HEIGHT);
        float hover = hover("row:" + config, hovered);
        int background = ARGB.srgbLerp(active ? 0.6f : 0, ClickGuiStyle.card(), ClickGuiStyle.cardEnabled());
        card(renderer, rowY, ARGB.srgbLerp(hover * 0.5f, background, ClickGuiStyle.hover()));
        float right = x + width - CARD_INSET - NAME_PAD;
        for (int i = ACTIONS.length - 1; i >= 0; i--) {
            String action = ACTIONS[i];
            String label = action.toUpperCase(Locale.ROOT);
            float labelWidth = renderer.text.measure(label, LABEL_SIZE).width();
            float labelX = right - labelWidth;
            float amount = hover(action + ":" + config, contains(mouseX, mouseY, labelX - LABEL_GAP / 2, rowY,
                    labelWidth + LABEL_GAP, ROW_HEIGHT));
            int base = ARGB.srgbLerp(hover, ClickGuiStyle.disabled(), ClickGuiStyle.muted());
            label(renderer, label, labelX, rowY, action.equals("delete")
                    ? AccentWave.text(ARGB.srgbLerp(amount, base, DELETE_COLOR), 0)
                    : AccentWave.text(base, amount));
            right = labelX - LABEL_GAP;
        }
        float nameX = x + CARD_INSET + NAME_PAD;
        float nameWidth = right - nameX - 4;
        float size = ClickGuiStyle.MODULE_FONT_SIZE;
        renderer.text.drawColored(fit(renderer, config, nameWidth, size), nameX,
                rowY + ROW_HEIGHT / 2 - renderer.text.lineHeight(size) / 2, size,
                AccentWave.wave(ClickGuiStyle.text(), active ? 1 : 0));
    }

    private void card(Renderer2D renderer, float rowY, int color) {
        renderer.sdf.roundedRect(x + CARD_INSET, rowY + CARD_INSET, width - CARD_INSET * 2, ROW_HEIGHT - CARD_INSET * 2,
                ClickGuiStyle.CARD_RADIUS, color);
    }

    private void label(Renderer2D renderer, String label, float labelX, float rowY, TextRenderer.ColorFunction color) {
        renderer.text.drawColored(label, labelX, renderer.text.topInBox(rowY + (ROW_HEIGHT - 14) / 2, 14, LABEL_SIZE),
                LABEL_SIZE, color);
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
            if (mouseX >= createLeft - LABEL_GAP / 2) {
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
            SpringAnimation hover = hovers.get(action + ":" + config);
            if (hover != null && hover.value() > 0.5f) {
                run(action, config);
                return true;
            }
        }
        selected = config;
        name.setValue(config);
        return true;
    }

    public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        if (!contains(mouseX, mouseY, x, listTop(), width, listHeight())) return false;
        targetScroll = Math.clamp(targetScroll - (float) amount * ROW_HEIGHT, 0, maxScroll());
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            if (focused) {
                String value = name.getValue().strip();
                if (!value.isEmpty() && !configs.contains(value)) run("save", value);
            } else if (!selected.isEmpty()) {
                run("load", selected);
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            if (!focused) return false;
            unfocus();
            return true;
        }
        if (focused && name.keyPressed(event)) {
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
            selected = action.equals("delete") ? "" : config;
            if (action.equals("delete") && name.getValue().strip().equals(config)) name.setValue("");
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

    private static String fit(Renderer2D renderer, String text, float width, float size) {
        if (renderer.text.measure(text, size).width() <= width) return text;
        int end = text.length();
        while (end > 0 && renderer.text.measure(text.substring(0, end) + "…", size).width() > width) end--;
        return text.substring(0, end) + "…";
    }
}
