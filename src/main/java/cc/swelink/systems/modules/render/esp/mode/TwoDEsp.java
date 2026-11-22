package cc.swelink.systems.modules.render.esp.mode;

import cc.swelink.renderer.EspProjection;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.PercentSetting;
import cc.swelink.systems.modules.render.esp.EspModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.joml.Matrix4f;
import net.minecraft.util.ARGB;

public final class TwoDEsp extends EspMode {
    private final IntSetting thickness = add(new IntSetting("Box width", 1, 1, 4));
    private final PercentSetting fill = add(new PercentSetting("Box fill", 10));
    private final BooleanSetting health = add(new BooleanSetting("Health bar", true));

    public TwoDEsp(EspModule module) {
        super(module, "2D");
    }

    @Override
    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        var camera = client.gameRenderer.mainCamera();
        var matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
        float tick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        try (var renderer = new Renderer2D(graphics)) {
            for (var player : client.level.players()) {
                if (!module.shouldRender(player)) {
                    continue;
                }
                var offset = player.getPosition(tick).subtract(player.position());
                var bounds = EspProjection.project(player.getBoundingBox().move(offset), camera.position(), matrix,
                        graphics.guiWidth(), graphics.guiHeight());
                if (bounds != null) {
                    drawBox(renderer, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                            player.getHealth() / Math.max(1, player.getMaxHealth()));
                }
            }
        }
    }

    public void drawBox(Renderer2D renderer, float x, float y, float width, float height, float healthFraction) {
        int color = module.color();
        if (fill.get() > 0) {
            renderer.fill(x, y, width, height, ARGB.color(Math.round(255 * fill.get() / 100f), color));
        }
        renderer.sdf.border(x - 1, y - 1, width + 2, height + 2, 0, thickness.get() + 2, 0xCC101014);
        renderer.sdf.border(x, y, width, height, 0, thickness.get(), color);
        if (health.get()) {
            float amount = Math.clamp(healthFraction, 0, 1);
            renderer.fill(x - 6, y - 1, 4, height + 2, 0xDD101014);
            int healthColor = 0xFF000000 | (Math.round(255 * (1 - amount)) << 16) | (Math.round(255 * amount) << 8);
            renderer.fill(x - 5, y + height * (1 - amount), 2, height * amount, healthColor);
        }
    }
}
