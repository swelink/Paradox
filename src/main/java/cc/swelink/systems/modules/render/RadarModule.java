package cc.swelink.systems.modules.render;

import cc.swelink.utils.combat.Targets;
import cc.swelink.events.EventHandler;
import cc.swelink.events.OverlayRenderEvent;
import cc.swelink.Paradox;
import cc.swelink.renderer.AccentWave;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.hud.HudPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;

public final class RadarModule extends Module {
    private static final Identifier ARROW = Paradox.id("icons/hud/arrow.png");
    private final IntSetting radius = addSetting(new IntSetting("Radius", 120, 40, 240));
    private final IntSetting arrowSize = addSetting(new IntSetting("Arrow size", 14, 6, 24));
    private final IntSetting range = addSetting(new IntSetting("Range", 128, 16, 512));

    public RadarModule() {
        super("Radar", "Arrows pointing to players", Category.RENDER);
    }

    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if (!isEnabled() || client.player == null || client.level == null || client.gui.hud.isHidden()
                || client.gui.screen() != null) return;
        var camera = client.gameRenderer.mainCamera();
        if (camera.isPanoramicMode()) return;
        float scale = HudPlacement.scale(client);
        float viewportWidth = graphics.guiWidth() / scale;
        float viewportHeight = graphics.guiHeight() / scale;
        float size = arrowSize.get();
        float ringRadius = Math.min(radius.get(), Math.min(viewportWidth, viewportHeight) / 2 - size - 8);
        if (ringRadius <= 0) return;
        float centerX = viewportWidth / 2;
        float centerY = viewportHeight / 2;
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var origin = camera.position();
        double yaw = Math.toRadians(camera.yRot());
        double maximumDistance = (double) range.get() * range.get();
        var players = new ArrayList<>(client.level.players());
        players.sort(Comparator.comparingDouble((net.minecraft.world.entity.player.Player player) ->
                player.distanceToSqr(client.player)).reversed());
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(scale);
        try (var renderer = new Renderer2D(graphics)) {
            for (var player : players) {
                if (player == client.player || player == camera.entity() || !player.isAlive() || player.isSpectator()
                        || player.isInvisibleTo(client.player) || player.distanceToSqr(client.player) > maximumDistance
                        || Targets.bot(player)) continue;
                var position = player.getPosition(partialTick);
                double dx = position.x - origin.x;
                double dz = position.z - origin.z;
                if (dx * dx + dz * dz < 0.0001) continue;
                double bearing = Math.atan2(-dx, dz) - yaw;
                float x = centerX + (float) Math.sin(bearing) * ringRadius;
                float y = centerY - (float) Math.cos(bearing) * ringRadius;
                pose.pushMatrix();
                try {
                    pose.translate(x, y);
                    pose.rotate((float) bearing);
                    renderer.textures.icon(ARROW, -size / 2, -size / 2, size, AccentWave.text().color(x, y));
                } finally {
                    pose.popMatrix();
                }
            }
        } finally {
            pose.popMatrix();
        }
    }

    @EventHandler(priority = 10)
    private void onOverlayRender(OverlayRenderEvent event) {
        render(event.client(), event.graphics());
    }
}
