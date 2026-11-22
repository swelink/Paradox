package cc.swelink.systems.modules.render;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.ModuleToggleEvent;
import cc.swelink.renderer.AccentWave;
import cc.swelink.renderer.DigitAnimation;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.Glass;
import cc.swelink.renderer.HudBackdrop;
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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DynamicIslandModule extends Module implements PlacedHudElement {
    private static final float HEIGHT = 30;
    private static final float EXPANDED_HEIGHT = 50;
    private static final float RADIUS = 15;
    private static final float PADDING = 12;
    private static final float FONT_SIZE = 15;
    private static final float DETAIL_GAP = 12;
    private static final float TIMED_WIDTH = 150;
    private static final float BAR_INSET = 8;
    private static final float BAR_RADIUS = RADIUS - BAR_INSET;
    private static final float BAR_HEIGHT = BAR_RADIUS * 2;
    private static final float BAR_TOP = EXPANDED_HEIGHT - BAR_INSET - BAR_HEIGHT;
    private static final int BAR_TRACK = 0xFF38383D;
    private static final float COUNTER_SPEED = 1000;
    private static final long FINISHED_LINGER = 150_000_000L;
    private static final long DURATION = 1_000_000_000L;
    private static final String BRAND_TEXT = "Paradox";
    private static final Identifier LOGO = Paradox.id("icons/paradox-logo.png");
    private static final float LOGO_U0 = 92 / 1024f;
    private static final float LOGO_V0 = 253 / 1024f;
    private static final float LOGO_U1 = 932 / 1024f;
    private static final float LOGO_V1 = 771 / 1024f;
    private static final float LOGO_ASPECT = 840 / 518f;
    private static final float LOGO_GAP = 6;
    private static final float LOGO_HEIGHT = 11;
    private final HudPlacement placement = new HudPlacement(0.5f, 0.016f, false).withCenterSnap();
    private final HudPopAnimation enablePop = new HudPopAnimation(true);
    private final FrameClock clock = new FrameClock();
    private final SpringAnimation width = new SpringAnimation();
    private final SpringAnimation height = new SpringAnimation();
    private final List<Notice> notices = new ArrayList<>();
    private final Map<String, Notice> current = new HashMap<>();
    private final List<Layer> layers = new ArrayList<>();
    private boolean muted;

    public DynamicIslandModule() {
        super("DynamicIsland", "Paradox island with notifications", Category.RENDER);
        placement.restore(0.5f, 0.016f, 1, true);
    }

    public void toggled(Module module, boolean enabled) {
        if (muted || !isEnabled() || module == this || module instanceof ClickGuiModule) return;
        var notice = post("module:" + module.name(), module.name());
        notice.detail = enabled ? "On" : "Off";
        notice.target = enabled ? 1 : 0;
        notice.accent = false;
        notice.timed = false;
        notice.countdown = -1;
    }


    public void configLoaded(String name) {
        if (!isEnabled()) return;
        var notice = post("config", "Config");
        notice.detail = "Loaded " + name;
        notice.accent = true;
    }

    public void friend(String name, boolean added) {
        if (!isEnabled()) return;
        var notice = post("friend:" + name, name);
        notice.detail = added ? "Added as a friend" : "Removed from friends";
        notice.target = added ? 1 : 0;
        notice.accent = false;
    }

    public void mute(boolean muted) {
        this.muted = muted;
    }

    private void trackDelays() {
        if (muted || !isEnabled()) return;
        long now = System.nanoTime();
        for (var module : Paradox.MODULES.modules()) {
            String key = "module:" + module.name();
            long remaining = module.delayRemaining();
            var notice = current.get(key);
            if (remaining <= 0) {
                if (notice != null && notice.timed) {
                    notice.countdown = 0;
                    if (!notice.finished) {
                        notice.finished = true;
                        notice.expires = Math.min(notice.expires, now + FINISHED_LINGER);
                    }
                }
                continue;
            }
            if (notice == null) {
                notice = post(key, module.name());
                notice.detail = "On";
                notice.target = 1;
                notice.accent = false;
            }
            long limit = module.delayLimit();
            notice.finished = false;
            notice.timed = true;
            notice.unit = "ms";
            notice.countdownLimit = limit;
            notice.countdown = Math.clamp(remaining, 0, limit);
            notice.expires = now + DURATION;
        }
    }

    private Notice post(String key, String title) {
        var old = current.get(key);
        if (old != null) notices.remove(old);
        var notice = new Notice();
        current.put(key, notice);
        notices.add(notice);
        notice.title = title;
        notice.expires = System.nanoTime() + DURATION;
        return notice;
    }

    private static ClickGuiModule gui() {
        return Paradox.MODULES.get(ClickGuiModule.class);
    }

    @Override
    protected boolean announcesToggle() {
        return false;
    }

    @Override
    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if (!super.isEnabled()) setEnabled(true);
        draw(client, graphics);
    }

    private void draw(Minecraft client, GuiGraphicsExtractor graphics) {
        if ((!isEnabled() && !enablePop.exiting()) || client.player == null || client.level == null || client.gui.hud.isHidden()) {
            reset();
            return;
        }
        float delta = clock.nextDelta();
        trackDelays();
        long now = System.nanoTime();
        notices.removeIf(notice -> now - notice.expires >= 0);
        current.values().removeIf(notice -> !notices.contains(notice));
        Notice target = notices.isEmpty() ? null : notices.getLast();
        showLayer(target);
        boolean clearing = layers.stream().anyMatch(layer -> layer.leaving);
        for (var layer : layers) {
            layer.fade.update(layer.leaving || layer.notice == null && clearing ? 0 : 1, 44, 1, delta);
        }
        Notice sizing = target;
        if (target == null) {
            for (var layer : layers) if (layer.leaving && layer.notice != null) sizing = layer.notice;
        }
        layers.removeIf(layer -> layer.leaving && layer.fade.value() <= 0.01f);

        var pose = graphics.pose();
        pose.pushMatrix();
        float scale = placement.renderScale(client);
        pose.scale(scale);
        try (var renderer = new Renderer2D(graphics)) {
            TextRenderer title = renderer.text.withFont(TextRenderer.Font.INTER_MEDIUM);
            float logoWidth = LOGO_HEIGHT * LOGO_ASPECT;
            float contentLeft = PADDING + logoWidth + LOGO_GAP;
            float digitWidth = 0;
            for (int digit = 0; digit <= 9; digit++) {
                digitWidth = Math.max(digitWidth, renderer.text.measure(Integer.toString(digit), FONT_SIZE).width());
            }
            for (var layer : layers) {
                var notice = layer.notice;
                if (notice == null) continue;
                if (!notice.shown) {
                    notice.state.reset(Math.max(0, notice.target));
                    notice.shown = true;
                }
                if (notice.target >= 0) notice.state.update(notice.target, 30, 1, delta);
                if (notice.timed) {
                    notice.digits.update(renderer, Math.max(0, notice.countdown) + notice.unit, delta);
                    float fill = notice.countdownLimit <= 0 ? 0
                            : Math.clamp(notice.countdown / (float) notice.countdownLimit, 0, 1);
                    if (!notice.filled) {
                        notice.progress.reset(fill);
                        notice.filled = true;
                    }
                    notice.progress.update(fill, 45, 1, delta);
                } else {
                    notice.digits.reset();
                    notice.progress.reset(0);
                    notice.filled = false;
                }
            }
            float brandWidth = title.measure(BRAND_TEXT, FONT_SIZE).width();
            float targetWidth;
            float targetHeight = HEIGHT;
            if (sizing == null) {
                targetWidth = contentLeft + brandWidth + PADDING;
            } else if (sizing.timed) {
                float counter = Long.toString(sizing.countdownLimit).length() * digitWidth
                        + renderer.text.measure(sizing.unit, FONT_SIZE).width();
                targetWidth = Math.max(TIMED_WIDTH,
                        contentLeft + title.measure(sizing.title, FONT_SIZE).width() + DETAIL_GAP + counter + PADDING);
                targetHeight = EXPANDED_HEIGHT;
            } else {
                float detail = renderer.text.measure(sizing.detail, FONT_SIZE).width();
                targetWidth = contentLeft + title.measure(sizing.title, FONT_SIZE).width() + DETAIL_GAP + detail + PADDING;
            }
            if (width.value() <= 0) {
                width.reset(targetWidth);
                height.reset(targetHeight);
            }
            float panelWidth = Math.max(HEIGHT, width.update(targetWidth, 32, 1, delta));
            float panelHeight = Math.max(HEIGHT, height.update(targetHeight, 32, 1, delta));
            placement.updateAnchored(client, panelWidth, panelHeight, HEIGHT);
            float x = placement.x();
            float y = placement.y();
            int edges = placement.touchedEdges();
            enablePop.apply(renderer, x, y, panelWidth, panelHeight, edges);
            float radius = RADIUS;
            float base = enablePop.value();
            if (gui().islandShadows()) {
                HudBackdrop.draw(renderer, List.of(HudBackdrop.docked(x, y, panelWidth, panelHeight, radius, edges)));
            }
            renderer.blur.frostedGlass(renderer, Glass.HUD, x, y, panelWidth, panelHeight, radius, ThemeColors.hudPanel());
            renderer.opacity(base);

            float titleTop = title.topInBox(y, HEIGHT, FONT_SIZE);
            float textTop = renderer.text.topInBox(y, HEIGHT, FONT_SIZE);
            float baseline = titleTop + title.ascent(FONT_SIZE);
            float logoX = x + PADDING;
            float logoY = baseline - LOGO_HEIGHT;
            renderer.textures.iconRegion(LOGO, logoX, logoY, logoWidth, LOGO_HEIGHT,
                    LOGO_U0, LOGO_V0, LOGO_U1, LOGO_V1, ThemeColors.primary());

            float left = x + contentLeft;
            float right = x + panelWidth - PADDING;
            try (var clip = renderer.clips.push(left - 1, y, Math.max(0, right - left + 2), panelHeight)) {
                for (var layer : layers) {
                    float opacity = Math.clamp(layer.fade.value(), 0, 1);
                    if (opacity <= 0.001f) continue;
                    float shift = (1 - opacity) * (layer.leaving ? -5 : 5);
                    renderer.opacity(opacity * base);
                    var notice = layer.notice;
                    if (notice == null) {
                        title.draw(BRAND_TEXT, left, titleTop + shift, FONT_SIZE, ThemeColors.text());
                        continue;
                    }
                    title.draw(notice.title, left, titleTop + shift, FONT_SIZE, ThemeColors.text());
                    if (notice.timed) {
                        notice.digits.drawRight(renderer, right, textTop + shift, (glyphX, glyphY) -> ThemeColors.text());
                    } else {
                        int color = ThemeColors.primary();
                        float detailWidth = renderer.text.measure(notice.detail, FONT_SIZE).width();
                        renderer.text.drawColored(notice.detail, right - detailWidth, textTop + shift, FONT_SIZE,
                                AccentWave.text(color, notice.accent ? 1 : 0));
                    }
                }
            }
            float expanded = Math.clamp((panelHeight - HEIGHT) / (EXPANDED_HEIGHT - HEIGHT), 0, 1);
            if (expanded > 0.001f) {
                float trackX = x + BAR_INSET;
                float trackWidth = panelWidth - BAR_INSET * 2;
                for (var layer : layers) {
                    var notice = layer.notice;
                    float opacity = Math.clamp(layer.fade.value(), 0, 1) * expanded;
                    if (notice == null || !notice.timed || opacity <= 0.001f) continue;
                    renderer.opacity(opacity * base);
                    float barY = y + BAR_TOP + (1 - expanded) * -4;
                    renderer.sdf.roundedRect(trackX, barY, trackWidth, BAR_HEIGHT, BAR_RADIUS, BAR_TRACK);
                    float progress = Math.clamp(notice.progress.value(), 0, 1);
                    float fill = trackWidth * progress;
                    if (fill > 0.5f) {
                        renderer.sdf.clippedRoundedRect(trackX, barY, trackWidth, BAR_HEIGHT, BAR_RADIUS, ThemeColors.primary(),
                                trackX - 1, barY - 1, trackX + fill + (progress >= 1 ? 1 : 0), barY + BAR_HEIGHT + 1);
                    }
                }
            }
            renderer.opacity(base);
        } finally {
            pose.popMatrix();
        }
    }

    private void showLayer(Notice target) {
        if (layers.isEmpty()) {
            var layer = new Layer(target);
            layer.fade.reset(1);
            layers.add(layer);
            return;
        }
        var top = layers.getLast();
        if (top.notice == target && !top.leaving) return;
        Layer existing = null;
        for (var layer : layers) {
            if (layer.notice == target) existing = layer;
            layer.leaving = true;
        }
        if (existing == null) existing = new Layer(target);
        layers.remove(existing);
        existing.leaving = false;
        layers.add(existing);
    }

    private void reset() {
        notices.clear();
        current.clear();
        layers.clear();
        width.reset(0);
        height.reset(0);
        placement.clearSize();
        stopDragging();
        clock.reset();
    }

    @Override
    public boolean visibleForDragging() {
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event) {
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event) {
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return false;
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
    protected void onEnable() {
        enablePop.reset();
        clock.reset();
        layers.clear();
        width.reset(0);
        height.reset(0);
    }

    @Override
    protected void onDisable() {
        enablePop.close();
        stopDragging();
    }

    @EventHandler(priority = 10)
    private void onModuleToggle(ModuleToggleEvent event) {
        toggled(event.module(), event.enabled());
    }

    private static final class Layer {
        private final Notice notice;
        private final SpringAnimation fade = new SpringAnimation();
        private boolean leaving;

        private Layer(Notice notice) {
            this.notice = notice;
        }
    }

    private static final class Notice {
        private final SpringAnimation state = new SpringAnimation();
        private final SpringAnimation progress = new SpringAnimation();
        private final DigitAnimation digits = new DigitAnimation(FONT_SIZE, COUNTER_SPEED);
        private String title = "";
        private String detail = "";
        private float target = -1;
        private boolean accent;
        private boolean timed;
        private boolean shown;
        private boolean finished;
        private boolean filled;
        private long countdown = -1;
        private long countdownLimit;
        private String unit = "ms";
        private long expires;
    }
}
