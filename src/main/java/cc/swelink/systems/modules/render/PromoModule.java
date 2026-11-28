package cc.swelink.systems.modules.render;

import cc.swelink.renderer.AccentWave;
import cc.swelink.renderer.HudPopAnimation;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.TextRenderer;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.renderer.UiTokens;
import cc.swelink.systems.hud.PlacedHudElement;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.hud.HudPlacement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.concurrent.ThreadLocalRandom;

public final class PromoModule extends Module implements PlacedHudElement {
    private static final String TEXT = "https://github.com/swelink/paradox";
    private static final float FONT_SIZE = UiTokens.TEXT_TITLE;
    private static final long MIN_PAUSE = 3_000_000_000L;
    private static final long MAX_PAUSE = 12_000_000_000L;
    private static final long ROLL_DURATION = 1_800_000_000L;
    private static final float AMPLITUDE = 5;
    private static final float WAVES = 2.5f;
    private static final float SPREAD = 0.45f;
    private final HudPlacement placement = new HudPlacement(0.5f, 0.9f, false).withCenterSnap();
    private final HudPopAnimation enablePop = new HudPopAnimation();
    private final String[] glyphs = TEXT.codePoints().mapToObj(Character::toString).toArray(String[]::new);
    private float[] offsets;
    private long rollStart;
    private long nextRoll;

    public PromoModule() {
        super("Promo", "Shows the Paradox GitHub link", Category.RENDER);
    }

    @Override
    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if ((!isEnabled() && !enablePop.exiting()) || client.player == null || client.level == null
                || client.gui.hud.isHidden()) {
            placement.clearSize();
            stopDragging();
            return;
        }
        if (!HudPlacement.canDrag(client)) stopDragging();
        long now = System.nanoTime();
        if (nextRoll == 0) schedule(now);
        if (rollStart == 0 && now >= nextRoll) rollStart = now;
        float roll = rollStart == 0 ? -1 : (now - rollStart) / (float) ROLL_DURATION;
        if (roll >= 1) {
            rollStart = 0;
            roll = -1;
            schedule(now);
        }
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(placement.renderScale(client));
        try (var renderer = new Renderer2D(graphics)) {
            TextRenderer title = renderer.text.withFont(TextRenderer.Font.INTER_MEDIUM);
            if (offsets == null) {
                offsets = new float[glyphs.length + 1];
                StringBuilder prefix = new StringBuilder();
                for (int i = 0; i < glyphs.length; i++) {
                    prefix.append(glyphs[i]);
                    offsets[i + 1] = title.measure(prefix.toString(), FONT_SIZE).width();
                }
            }
            float width = offsets[glyphs.length];
            float height = title.lineHeight(FONT_SIZE);
            placement.updateAnchored(client, width, height, height);
            float x = placement.x();
            float y = placement.y();
            enablePop.apply(renderer, x, y, width, height, placement.touchedEdges());
            renderer.opacity(enablePop.value());
            TextRenderer.ColorFunction colors = AccentWave.wave(ThemeColors.primary(), 1);
            if (roll < 0) {
                title.drawColored(TEXT, x, y, FONT_SIZE, colors);
                return;
            }
            float envelope = (float) Math.sin(Math.PI * roll);
            float phase = roll * WAVES * (float) Math.PI * 2;
            for (int i = 0; i < glyphs.length; i++) {
                float lift = (float) Math.sin(phase - i * SPREAD) * AMPLITUDE * envelope;
                title.drawColored(glyphs[i], x + offsets[i], y - lift, FONT_SIZE, colors);
            }
        } finally {
            pose.popMatrix();
        }
    }

    private void schedule(long now) {
        nextRoll = now + ThreadLocalRandom.current().nextLong(MIN_PAUSE, MAX_PAUSE + 1);
    }

    @Override
    public HudPlacement placement() {
        return placement;
    }

    @Override
    protected void onEnable() {
        enablePop.reset();
        rollStart = 0;
        nextRoll = 0;
    }

    @Override
    protected void onDisable() {
        enablePop.close();
        stopDragging();
    }
}
