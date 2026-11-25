package cc.swelink.systems.modules.render;

import cc.swelink.renderer.UiTokens;
import cc.swelink.Paradox;
import cc.swelink.renderer.AccentWave;
import cc.swelink.renderer.Glass;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.HudPopAnimation;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.SpringAnimation;
import cc.swelink.renderer.TextRenderer;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.systems.hud.PlacedHudElement;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.client.ClickGuiModule;
import cc.swelink.utils.hud.HudPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

public final class KeybindHudModule extends Module implements PlacedHudElement {
    private static final float MIN_WIDTH = 150;
    private static final float MAX_WIDTH = 280;
    private static final float HEADER_HEIGHT = UiTokens.PANEL_HEADER_HEIGHT;
    private static final float ROW_HEIGHT = UiTokens.PANEL_ROW_HEIGHT;
    private static final float PADDING = UiTokens.PANEL_PADDING;
    private static final float ICON_SIZE = UiTokens.PANEL_ICON;
    private static final float ICON_GAP = UiTokens.PANEL_ICON_GAP;
    private static final float FONT_SIZE = UiTokens.TEXT_BODY;
    private static final float BIND_GAP = 14;
    private static final float MAX_BIND_WIDTH = 90;
    private static final float TITLE_SIZE = UiTokens.TEXT_TITLE;
    private static final float RADIUS = UiTokens.RADIUS_PANEL;
    private final HudPlacement placement = new HudPlacement(1, 0.6f, false).withCenterSnap();
    private final SpringAnimation visibility = new SpringAnimation();
    private final SpringAnimation widthMotion = new SpringAnimation();
    private final FrameClock clock = new FrameClock();
    private final Map<Module, BindAnimation> animations = new HashMap<>();

    public KeybindHudModule() {
        super("KeybindHud", "Lists your keybinds", Category.RENDER);
    }

    @Override
    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if ((!isEnabled() && visibility.value() == 0) || client.player == null || client.level == null || client.gui.hud.isHidden()) {
            visibility.reset(0);
            animations.clear();
            widthMotion.reset(0);
            placement.clearSize();
            stopDragging();
            clock.reset();
            return;
        }
        boolean chat = HudPlacement.canDrag(client);
        if (!chat || !isEnabled()) stopDragging();
        float delta = clock.nextDelta();
        var bound = new HashSet<Module>();
        for (var module : Paradox.MODULES.modules()) {
            if (!isEnabled() || module instanceof ClickGuiModule || module.bind().get() == -1) continue;
            bound.add(module);
            var animation = animations.computeIfAbsent(module, ignored -> new BindAnimation());
            animation.bind = module.bind().displayValue().toUpperCase(Locale.ROOT);
        }
        boolean showing = isEnabled() && (chat || !bound.isEmpty());
        for (var entry : animations.entrySet()) {
            if (!showing) continue;
            var animation = entry.getValue();
            animation.size.update(bound.contains(entry.getKey()) ? 1 : 0, 26, 1, delta);
            animation.enabled.update(entry.getKey().isEnabled() ? 1 : 0, 20, 1, delta);
        }
        animations.entrySet().removeIf(entry -> !bound.contains(entry.getKey()) && entry.getValue().size.value() == 0);
        float reveal = Math.clamp(visibility.update(showing ? 1 : 0, 20, 0.85f, delta), 0, 1);
        if (!showing && reveal <= 0.001f) {
            visibility.reset(0);
            animations.clear();
            widthMotion.reset(0);
            placement.clearSize();
            stopDragging();
            clock.reset();
            return;
        }
        var rows = new ArrayList<>(animations.entrySet());
        rows.sort(Comparator.comparing(entry -> entry.getKey().name(), String.CASE_INSENSITIVE_ORDER));
        float height = HEADER_HEIGHT;
        for (var row : rows) height += ROW_HEIGHT * row.getValue().size.value();
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(placement.renderScale(client));
        try (var renderer = new Renderer2D(graphics)) {
            var title = renderer.text.withFont(TextRenderer.Font.INTER_MEDIUM);
            float targetWidth = PADDING * 2 + ICON_SIZE + ICON_GAP + title.measure("Keybinds", TITLE_SIZE).width();
            for (var row : rows) {
                var animation = row.getValue();
                float bindWidth = Math.min(MAX_BIND_WIDTH, renderer.text.measure(animation.bind, FONT_SIZE).width());
                targetWidth = Math.max(targetWidth, PADDING * 2 + renderer.text.measure(row.getKey().name(), FONT_SIZE).width()
                        + BIND_GAP + bindWidth);
            }
            targetWidth = Math.clamp(targetWidth, MIN_WIDTH, MAX_WIDTH);
            if (widthMotion.value() == 0) widthMotion.reset(targetWidth);
            float width = widthMotion.update(targetWidth, 26, 1, delta);
            placement.updateAnchored(client, width, height, HEADER_HEIGHT);
            float x = placement.x();
            float y = placement.y();
            renderer.opacity(reveal);
            HudPopAnimation.pop(pose, reveal, x, y, width, height, placement.touchedEdges());
            int edges = placement.touchedEdges();
            renderer.blur.frostedGlassWithHeader(renderer, Glass.HUD, x, y, width, height, RADIUS, ThemeColors.hudPanel(), HEADER_HEIGHT, ThemeColors.hudHeader(),
                    edges);
            try (var panelClip = renderer.clips.push(x, y, width, height)) {
                renderer.textures.icon(Paradox.id("icons/hud/keybind.png"), x + PADDING,
                        y + (HEADER_HEIGHT - ICON_SIZE) / 2, ICON_SIZE, AccentWave.text());
                title.draw("Keybinds", x + PADDING + ICON_SIZE + ICON_GAP,
                        y + (HEADER_HEIGHT - title.lineHeight(TITLE_SIZE)) / 2, TITLE_SIZE, ThemeColors.text());
                float rowY = y + HEADER_HEIGHT;
                for (var entry : rows) {
                    var module = entry.getKey();
                    var animation = entry.getValue();
                    float amount = animation.size.value();
                    float rowHeight = ROW_HEIGHT * amount;
                    float opacity = FrameClock.smoothStep(Math.clamp((amount - 0.2f) / 0.8f, 0, 1));
                    float contentY = rowY + (1 - opacity) * 3;
                    try (var rowOpacity = renderer.pushOpacity(opacity);
                         var clip = renderer.clips.push(x, rowY, width, rowHeight)) {
                        float bindSize = FONT_SIZE * Math.min(1,
                                MAX_BIND_WIDTH / Math.max(1, renderer.text.measure(animation.bind, FONT_SIZE).width()));
                        float bindWidth = renderer.text.measure(animation.bind, bindSize).width();
                        float nameWidth = width - PADDING * 2 - BIND_GAP - bindWidth;
                        float targetSize = FONT_SIZE * Math.min(1,
                                nameWidth / Math.max(1, renderer.text.measure(module.name(), FONT_SIZE).width()));
                        float nameSize = animation.textSize.update(targetSize, 20, 1, delta);
                        float nameX = x + PADDING;
                        try (var nameClip = renderer.clips.push(nameX, rowY, nameWidth, rowHeight)) {
                            renderer.text.centeredColoredInBox(module.name(), nameX, contentY,
                                    renderer.text.measure(module.name(), nameSize).width(), ROW_HEIGHT, nameSize,
                                    AccentWave.text(ThemeColors.text(), animation.enabled.value()));
                        }
                        renderer.text.centeredInBox(animation.bind, x + width - PADDING - bindWidth,
                                contentY, bindWidth, ROW_HEIGHT, bindSize, 0xFFB8B7BE);
                    }
                    rowY += rowHeight;
                }
            }
        } finally {
            pose.popMatrix();
        }
    }

    @Override
    public float cornerRadius() {
        return RADIUS;
    }

    @Override
    public HudPlacement placement() {
        return placement;
    }

    @Override
    public boolean visibleForDragging() {
        return isEnabled() && visibility.value() > 0.001f;
    }

    @Override
    protected void onEnable() {
        visibility.reset(0);
        clock.reset();
        animations.clear();
        widthMotion.reset(0);
    }

    @Override
    protected void onDisable() {
        stopDragging();
    }

    private static final class BindAnimation {
        private final SpringAnimation size = new SpringAnimation();
        private final SpringAnimation enabled = new SpringAnimation();
        private final SpringAnimation textSize = new SpringAnimation(FONT_SIZE);
        private String bind = "";
    }
}
