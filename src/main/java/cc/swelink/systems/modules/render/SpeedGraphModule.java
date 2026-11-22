package cc.swelink.systems.modules.render;

import cc.swelink.Paradox;
import cc.swelink.renderer.DigitAnimation;
import cc.swelink.renderer.Glass;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.HudBackdrop;
import cc.swelink.renderer.HudPopAnimation;
import cc.swelink.renderer.AccentWave;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.SdfPipeline;
import cc.swelink.renderer.SpringAnimation;
import cc.swelink.renderer.TextRenderer;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.systems.hud.PlacedHudElement;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.hud.HudPlacement;
import cc.swelink.utils.player.HorizontalSpeed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.ARGB;

public final class SpeedGraphModule extends Module implements PlacedHudElement {
    private static final int SAMPLES = 100;
    private static final float WIDTH = 200;
    private static final float HEADER_HEIGHT = 28;
    private static final float GRAPH_HEIGHT = 56;
    private static final float PADDING = 7;
    private static final float HEIGHT = HEADER_HEIGHT + GRAPH_HEIGHT;
    private static final float ICON_SIZE = 18;
    private static final float ICON_GAP = 7;
    private static final float RADIUS = 10;
    private static final float FONT_SIZE = 13;
    private static final int SUBDIVISIONS = 4;
    private static final float LINE_WIDTH = 1.6f;
    private static final float SMOOTHING = 0.3f;
    private static final long VALUE_INTERVAL = 50_000_000L;
    private final BooleanSetting shadows = addSetting(new BooleanSetting("Shadows", true));
    private final HudPlacement placement = new HudPlacement(0, 0.6f, false).withCenterSnap();
    private final SpringAnimation visibility = new SpringAnimation();
    private final SpringAnimation ceiling = new SpringAnimation(1);
    private final FrameClock clock = new FrameClock();
    private final HorizontalSpeed sampler = new HorizontalSpeed();
    private final float[] samples = new float[SAMPLES];
    private int count;
    private int head;
    private float smoothed;
    private float displayed;
    private final DigitAnimation valueDigits = new DigitAnimation(FONT_SIZE);
    private String valueText = "0.00 bps";
    private long nextValueUpdate;
    private LocalPlayer samplePlayer;
    private ClientLevel sampleLevel;

    public SpeedGraphModule() {
        super("SpeedGraph", "Graphs your speed", Category.RENDER);
    }

    @Override
    public void tick(Minecraft client) {
        if (client.isPaused() || client.player == null || client.level == null) return;
        if (samplePlayer != client.player || sampleLevel != client.level) {
            clearSamples();
            samplePlayer = client.player;
            sampleLevel = client.level;
        }
        sampler.sample(client.player.getX(), client.player.getZ(), client.player.tickCount,
                client.level.tickRateManager().tickrate());
        smoothed = count == 0 ? sampler.speed() : smoothed + (sampler.speed() - smoothed) * SMOOTHING;
        samples[head] = smoothed;
        head = (head + 1) % SAMPLES;
        count = Math.min(count + 1, SAMPLES);
    }

    @Override
    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if ((!isEnabled() && visibility.value() == 0) || client.player == null || client.level == null || client.gui.hud.isHidden()) {
            hide();
            return;
        }
        if (!HudPlacement.canDrag(client) || !isEnabled()) stopDragging();
        float delta = clock.nextDelta();
        float reveal = Math.clamp(visibility.update(isEnabled() ? 1 : 0, 20, 0.85f, delta), 0, 1);
        if (!isEnabled() && reveal <= 0.001f) {
            hide();
            return;
        }
        float partial = client.isPaused() ? 0 : client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float live = sampler.previousSpeed() + (sampler.speed() - sampler.previousSpeed()) * partial;
        displayed = FrameClock.approach(displayed, live, 30, delta);
        float peak = 1;
        for (int i = 0; i < count; i++) peak = Math.max(peak, sample(i));
        float top = ceiling.update(peak * 1.15f, 12, 1, delta);
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(placement.renderScale(client));
        try {
            placement.updateAnchored(client, WIDTH, HEIGHT, HEADER_HEIGHT);
            float x = placement.x();
            float y = placement.y();
            HudPopAnimation.pop(pose, reveal, x, y, WIDTH, HEIGHT, placement.touchedEdges());
            try (var renderer = new Renderer2D(graphics)) {
                renderer.opacity(reveal);
                int edges = placement.touchedEdges();
                if (shadows.get()) HudBackdrop.draw(renderer, List.of(HudBackdrop.docked(x, y, WIDTH, HEIGHT, RADIUS, edges)));
                renderer.blur.frostedGlassWithHeader(renderer, Glass.HUD, x, y, WIDTH, HEIGHT, RADIUS, ThemeColors.hudPanel(), HEADER_HEIGHT,
                        ThemeColors.hudHeader(), edges);
                int accent = ThemeColors.primary();
                renderer.textures.icon(Paradox.id("icons/flash.png"), x + PADDING,
                        y + (HEADER_HEIGHT - ICON_SIZE) / 2, ICON_SIZE, AccentWave.text());
                var title = renderer.text.withFont(TextRenderer.Font.INTER_MEDIUM);
                title.draw("Speed", x + PADDING + ICON_SIZE + ICON_GAP,
                        y + (HEADER_HEIGHT - title.lineHeight(15)) / 2, 15, ThemeColors.text());
                long now = System.nanoTime();
                if (now >= nextValueUpdate) {
                    valueText = String.format(Locale.ROOT, "%.2f bps", Math.max(0, displayed));
                    nextValueUpdate = now + VALUE_INTERVAL;
                }
                valueDigits.update(renderer, valueText, delta);
                valueDigits.draw(renderer, x + WIDTH - PADDING - valueDigits.width(),
                        y + (HEADER_HEIGHT - renderer.text.lineHeight(FONT_SIZE)) / 2, (glyphX, glyphY) -> ThemeColors.text());
                drawGraph(renderer, x, y, x, y + HEADER_HEIGHT, WIDTH, GRAPH_HEIGHT, partial, top, accent, edges);
            }
        } finally {
            pose.popMatrix();
        }
    }

    private void drawGraph(Renderer2D renderer, float panelX, float panelY, float left, float top, float width,
                           float height, float partial, float ceiling, int accent, int edges) {
        if (count < 3) return;
        float step = width / (SAMPLES - 2);
        float bottom = top + height;
        float right = left + width;
        int fill = ARGB.color(0x80, accent);
        float latest = count - 2 + partial;
        int first = (int) Math.max(0, Math.floor(latest - width / step));
        int whole = (int) Math.floor(latest);
        int columns = (whole - first) * SUBDIVISIONS + (int) Math.ceil((latest - whole) * SUBDIVISIONS);
        if (columns < 1) return;
        float flare = SdfPipeline.corner(RADIUS, WIDTH, HEIGHT) + 1;
        int points = columns + 3;
        float[] xs = new float[points];
        float[] ys = new float[points];
        float[] floors = new float[points];
        for (int i = 0; i <= columns; i++) {
            float index = Math.min(first + (float) i / SUBDIVISIONS, latest);
            xs[i + 1] = right - (latest - index) * step;
            ys[i + 1] = bottom - LINE_WIDTH / 2 - Math.clamp(curve(index) / ceiling, 0, 1) * (height - LINE_WIDTH);
            floors[i + 1] = bottom + flare;
        }
        boolean reachesLeft = xs[1] <= left + 0.01f;
        xs[0] = reachesLeft ? xs[1] - flare : xs[1];
        ys[0] = ys[1];
        floors[0] = bottom + flare;
        xs[points - 1] = xs[points - 2] + flare;
        ys[points - 1] = ys[points - 2];
        floors[points - 1] = bottom + flare;
        int from = reachesLeft ? 0 : 1;
        renderer.sdf.dockedArea(panelX, panelY, WIDTH, HEIGHT, RADIUS, fill, edges, xs, ys, floors, from, points);
        renderer.sdf.dockedPolyline(panelX, panelY, WIDTH, HEIGHT, RADIUS, accent, edges,
                Arrays.copyOfRange(xs, from, points), Arrays.copyOfRange(ys, from, points), points - from, LINE_WIDTH);
    }

    private float curve(float index) {
        float clamped = Math.clamp(index, 0, count - 1);
        int base = (int) Math.floor(clamped);
        float t = clamped - base;
        float p0 = sample(Math.max(base - 1, 0));
        float p1 = sample(base);
        float p2 = sample(Math.min(base + 1, count - 1));
        float p3 = sample(Math.min(base + 2, count - 1));
        float value = 0.5f * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t
                + (3 * p1 - p0 - 3 * p2 + p3) * t * t * t);
        return Math.max(0, value);
    }

    private float sample(int index) {
        int clamped = Math.clamp(index, 0, count - 1);
        float previous = raw(Math.max(clamped - 1, 0));
        float next = raw(Math.min(clamped + 1, count - 1));
        return (previous + raw(clamped) * 2 + next) / 4;
    }

    private float raw(int index) {
        return samples[Math.floorMod(head - count + index, SAMPLES)];
    }

    private void clearSamples() {
        sampler.reset();
        count = 0;
        head = 0;
        smoothed = 0;
        displayed = 0;
        valueText = "0.00 bps";
        nextValueUpdate = 0;
        valueDigits.reset();
        ceiling.reset(1);
    }

    private void hide() {
        visibility.reset(0);
        placement.clearSize();
        stopDragging();
        clock.reset();
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
        clearSamples();
        samplePlayer = null;
        sampleLevel = null;
    }

    @Override
    protected void onDisable() {
        stopDragging();
    }
}
