package cc.swelink.systems.modules.render;

import cc.swelink.renderer.UiTokens;
import cc.swelink.Paradox;
import cc.swelink.renderer.DigitAnimation;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.Glass;
import cc.swelink.renderer.HudPopAnimation;
import cc.swelink.renderer.AccentWave;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.SpringAnimation;
import cc.swelink.renderer.TextRenderer;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.renderer.PixelSnap;
import cc.swelink.systems.hud.PlacedHudElement;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.hud.HudPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class EffectsModule extends Module implements PlacedHudElement {
    private static final float MIN_WIDTH = 150;
    private static final float MAX_WIDTH = 300;
    private static final float PADDING = UiTokens.PANEL_PADDING;
    private static final float LIST_PADDING = 4;
    private static final float HEADER_HEIGHT = UiTokens.PANEL_HEADER_HEIGHT;
    private static final float ROW_HEIGHT = UiTokens.PANEL_ROW_HEIGHT;
    private static final float ICON_SIZE = UiTokens.PANEL_ICON;
    private static final float ICON_GAP = UiTokens.PANEL_ICON_GAP;
    private static final float TIMER_GAP = 12;
    private static final float FONT_SIZE = UiTokens.TEXT_BODY;
    private static final float TITLE_SIZE = UiTokens.TEXT_TITLE;
    private static final float CORNER_RADIUS = UiTokens.RADIUS_PANEL;
    private static final int MUTED_TEXT = 0xFFB8B7BE;
    private final HudPlacement placement = new HudPlacement(1, 0.35f, false).withCenterSnap();
    private final SpringAnimation visibility = new SpringAnimation();
    private final SpringAnimation widthMotion = new SpringAnimation();
    private final FrameClock clock = new FrameClock();
    private final Map<String, EffectAnimation> effectAnimations = new HashMap<>();

    public EffectsModule() {
        super("EffectsHud", "Shows your potion effects", Category.RENDER);
    }

    @Override
    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if (!isEnabled() && visibility.value() == 0) {
            placement.clearSize();
            stopDragging();
            clock.reset();
            return;
        }
        if (client.player == null || client.level == null || client.gui.hud.isHidden()) {
            placement.clearSize();
            stopDragging();
            effectAnimations.clear();
            visibility.reset(0);
            widthMotion.reset(0);
            clock.reset();
            return;
        }
        boolean chat = HudPlacement.canDrag(client);
        if (!chat || !isEnabled()) stopDragging();
        float delta = clock.nextDelta();

        var active = new HashSet<String>();
        float tickRate = client.level.tickRateManager().tickrate();
        for (var effect : client.player.getActiveEffects()) {
            if (!isEnabled()) break;
            String id = effect.getEffect().getRegisteredName();
            active.add(id);
            var animation = effectAnimations.computeIfAbsent(id, ignored -> new EffectAnimation());
            animation.row = new EffectRow(effect, effectName(effect),
                    MobEffectUtil.formatDuration(effect, 1, tickRate).getString());
        }
        boolean showing = isEnabled() && (chat || !active.isEmpty());
        for (var entry : effectAnimations.entrySet()) {
            if (!showing) continue;
            var animation = entry.getValue();
            animation.update(active.contains(entry.getKey()), delta);
        }
        effectAnimations.entrySet().removeIf(entry -> !active.contains(entry.getKey())
                && entry.getValue().progress == 0 && entry.getValue().space() == 0);
        float reveal = Math.clamp(visibility.update(showing ? 1 : 0, 20, 0.85f, delta), 0, 1);
        if (!showing && reveal <= 0.001f) {
            visibility.reset(0);
            widthMotion.reset(0);
            effectAnimations.clear();
            placement.clearSize();
            stopDragging();
            clock.reset();
            return;
        }
        var rows = new ArrayList<>(effectAnimations.values());
        rows.sort(Comparator.comparing((EffectAnimation animation) -> animation.row.name())
                .thenComparing(animation -> animation.row.effect().getEffect().getRegisteredName()));

        var pose = graphics.pose();
        pose.pushMatrix();
        float scale = placement.renderScale(client);
        pose.scale(scale);
        try (var renderer = new Renderer2D(graphics)) {
            var title = renderer.text.withFont(TextRenderer.Font.INTER_MEDIUM);
            float digitWidth = 0;
            for (int digit = 0; digit <= 9; digit++) {
                digitWidth = Math.max(digitWidth, renderer.text.measure(Integer.toString(digit), FONT_SIZE).width());
            }
            float timerColumnWidth = digitWidth * 6 + renderer.text.measure(":", FONT_SIZE).width() * 2;
            float targetWidth = PADDING * 2 + ICON_SIZE + ICON_GAP + title.measure("Effects", TITLE_SIZE).width();
            for (var animation : rows) {
                targetWidth = Math.max(targetWidth, PADDING * 2 + ICON_SIZE + ICON_GAP
                        + renderer.text.measure(animation.row.name(), FONT_SIZE).width() + TIMER_GAP + timerColumnWidth);
            }
            targetWidth = Math.clamp(targetWidth, MIN_WIDTH, MAX_WIDTH);
            if (widthMotion.value() == 0) widthMotion.reset(targetWidth);
            float width = widthMotion.update(targetWidth, 26, 1, delta);
            float visibleRows = 0;
            for (var animation : rows) {
                visibleRows += animation.space();
            }
            float listPadding = LIST_PADDING * Math.min(1, visibleRows);
            float height = HEADER_HEIGHT + visibleRows * ROW_HEIGHT + listPadding * 2;
            placement.updateAnchored(client, width, height, HEADER_HEIGHT);
            float x = placement.x();
            float y = placement.y();
            renderer.opacity(reveal);
            HudPopAnimation.pop(pose, reveal, x, y, width, height, placement.touchedEdges());
            int edges = placement.touchedEdges();
            renderer.blur.frostedGlassWithHeader(renderer, Glass.HUD, x, y, width, height, CORNER_RADIUS,
                    ThemeColors.hudPanel(), HEADER_HEIGHT, ThemeColors.hudHeader(), edges);
            try (var panelClip = renderer.clips.push(x, y, width, height)) {
                renderer.textures.icon(Paradox.id("icons/hud/effect.png"), x + PADDING,
                        y + (HEADER_HEIGHT - ICON_SIZE) / 2, ICON_SIZE, AccentWave.text());
                title.draw("Effects", x + PADDING + ICON_SIZE + ICON_GAP,
                        y + (HEADER_HEIGHT - title.lineHeight(TITLE_SIZE)) / 2, TITLE_SIZE, ThemeColors.text());

                float rowY = y + HEADER_HEIGHT + listPadding;
                for (var animation : rows) {
                    var row = animation.row;
                    float rowHeight = ROW_HEIGHT * animation.space();
                    float opacity = animation.opacity();
                    float contentY = rowY + (1 - opacity) * 3;
                    try (var rowOpacity = renderer.pushOpacity(opacity);
                         var rowClip = renderer.clips.push(x, rowY, width, rowHeight)) {
                        float iconX = x + PADDING;
                        float iconY = contentY + (ROW_HEIGHT - ICON_SIZE) / 2;
                        if (reveal >= 0.999f) {
                            PixelSnap.push(graphics, iconX, iconY, ICON_SIZE);
                        } else {
                            pose.pushMatrix();
                            pose.translate(iconX + ICON_SIZE / 2, iconY + ICON_SIZE / 2);
                            pose.scale(PixelSnap.ratio(scale, ICON_SIZE));
                            pose.translate(-ICON_SIZE / 2, -ICON_SIZE / 2);
                        }
                        try {
                            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(row.effect().getEffect()),
                                    0, 0, (int) ICON_SIZE, (int) ICON_SIZE, Math.round(opacity * reveal * 255) << 24 | 0xFFFFFF);
                        } finally {
                            pose.popMatrix();
                        }
                        float nameWidth = width - PADDING * 2 - ICON_SIZE - ICON_GAP - TIMER_GAP - timerColumnWidth;
                        float targetNameSize = FONT_SIZE * Math.min(1, Math.max(1, nameWidth)
                                / Math.max(1, renderer.text.measure(row.name(), FONT_SIZE).width()));
                        float nameSize = animation.textSize.update(targetNameSize, 20, 1, delta);
                        float nameTextWidth = renderer.text.measure(row.name(), nameSize).width();
                        float nameX = x + PADDING + ICON_SIZE + ICON_GAP;
                        try (var nameClip = renderer.clips.push(nameX, rowY, Math.max(1, nameWidth), rowHeight)) {
                            renderer.text.centeredInBox(row.name(), nameX,
                                    contentY, nameTextWidth, ROW_HEIGHT, nameSize, ThemeColors.text());
                        }
                        float timerRight = x + width - PADDING;
                        try (var timerClip = renderer.clips.push(timerRight - timerColumnWidth, rowY, timerColumnWidth, rowHeight)) {
                            animation.timer.update(renderer, row.timer(), delta);
                            animation.timer.drawRight(renderer, timerRight,
                                    renderer.text.topInBox(contentY, ROW_HEIGHT, FONT_SIZE), (glyphX, glyphY) -> MUTED_TEXT);
                        }
                    }
                    rowY += rowHeight;
                }
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static String effectName(MobEffectInstance effect) {
        String name = effect.getEffect().value().getDisplayName().getString();
        if (effect.getAmplifier() > 0) {
            int level = effect.getAmplifier() + 1;
            name += " " + (level <= 10 ? Component.translatable("enchantment.level." + level).getString() : level);
        }
        return name;
    }

    @Override
    public float cornerRadius() {
        return CORNER_RADIUS;
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
        widthMotion.reset(0);
        clock.reset();
        effectAnimations.clear();
    }

    @Override
    protected void onDisable() {
        stopDragging();
    }

    private record EffectRow(MobEffectInstance effect, String name, String timer) {
    }

    private static final class EffectAnimation {
        private static final float DURATION = 0.22f;
        private final SpringAnimation textSize = new SpringAnimation(FONT_SIZE);
        private final SpringAnimation size = new SpringAnimation();
        private final DigitAnimation timer = new DigitAnimation(FONT_SIZE);
        private float progress;
        private EffectRow row;

        private void update(boolean active, float delta) {
            progress = Math.clamp(progress + (active ? delta : -delta) / DURATION, 0, 1);
            size.update(active ? 1 : 0, 26, 1, delta);
        }

        private float space() {
            return Math.clamp(size.value(), 0, 1);
        }

        private float opacity() {
            return FrameClock.smoothStep(Math.clamp((progress - 0.25f) / 0.75f, 0, 1));
        }
    }
}
