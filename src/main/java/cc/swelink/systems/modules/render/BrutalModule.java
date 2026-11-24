package cc.swelink.systems.modules.render;

import cc.swelink.Paradox;
import cc.swelink.events.AttackEvent;
import cc.swelink.events.EventHandler;
import cc.swelink.events.IncomingPacketEvent;
import cc.swelink.events.OverlayRenderEvent;
import cc.swelink.mixin.EntityEventPacketAccessor;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.TextRenderer;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.PercentSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BrutalModule extends Module {
    private static final Identifier LOGO = Paradox.id("icons/paradox-logo.png");
    private static final float LOGO_U0 = 92 / 1024f;
    private static final float LOGO_V0 = 253 / 1024f;
    private static final float LOGO_U1 = 932 / 1024f;
    private static final float LOGO_V1 = 771 / 1024f;
    private static final float LOGO_ASPECT = 840 / 518f;
    private static final long KILL_WINDOW = 5_000_000_000L;
    private static final float POPUP_TIME = 1.4f;
    private static final float POPUP_SIZE = 72;
    private static final float COUNTER_SIZE = 18;
    private static final int KILL_COLOR = 0xFFFF3B3B;
    private static int kills;

    private final IntSetting size = addSetting(new IntSetting("Logo size", 45, 10, 100));
    private final IntSetting speed = addSetting(new IntSetting("Spin speed", 90, 0, 720));
    private final PercentSetting opacity = addSetting(new PercentSetting("Logo opacity", 35));
    private final BooleanSetting counter = addSetting(new BooleanSetting("Kill counter", true));
    private final Map<Integer, Long> attacked = new HashMap<>();
    private final List<Float> popups = new ArrayList<>();
    private final FrameClock clock = new FrameClock();
    private float angle;

    public BrutalModule() {
        super("Brutal", "Spinning logo and kill popups", Category.RENDER);
    }

    @EventHandler(priority = 0)
    private void onAttack(AttackEvent event) {
        if (event.client().level != null && event.client().level.getEntity(event.entityId()) instanceof Player) {
            attacked.put(event.entityId(), System.nanoTime());
        }
    }

    @EventHandler(priority = 0)
    private void onIncomingPacket(IncomingPacketEvent event) {
        if (!(event.packet() instanceof ClientboundEntityEventPacket packet) || packet.getEventId() != EntityEvent.DEATH) {
            return;
        }
        Long time = attacked.remove(((EntityEventPacketAccessor) packet).paradox$entityId());
        if (time != null && System.nanoTime() - time <= KILL_WINDOW) {
            kills++;
            popups.add(0f);
        }
    }

    @EventHandler(priority = 0)
    private void onOverlayRender(OverlayRenderEvent event) {
        render(event.client(), event.graphics());
    }

    private void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if (client.player == null || client.level == null || client.gui.hud.isHidden()) {
            clock.reset();
            return;
        }
        float delta = clock.nextDelta();
        angle = (angle + speed.get() * delta) % 360;
        long now = System.nanoTime();
        attacked.values().removeIf(time -> now - time > KILL_WINDOW);
        popups.replaceAll(time -> time + delta);
        popups.removeIf(time -> time >= POPUP_TIME);

        float centerX = graphics.guiWidth() / 2f;
        float centerY = graphics.guiHeight() / 2f;
        var pose = graphics.pose();
        try (var renderer = new Renderer2D(graphics, TextRenderer.Font.INTER_MEDIUM)) {
            float logoWidth = graphics.guiHeight() * size.get() / 100f;
            float logoHeight = logoWidth / LOGO_ASPECT;
            if (opacity.get() > 0 && logoWidth > 0) {
                pose.pushMatrix();
                try {
                    pose.translate(centerX, centerY);
                    pose.rotate((float) Math.toRadians(angle));
                    renderer.textures.iconRegion(LOGO, -logoWidth / 2, -logoHeight / 2, logoWidth, logoHeight,
                            LOGO_U0, LOGO_V0, LOGO_U1, LOGO_V1,
                            ARGB.color(Math.round(255 * opacity.get() / 100f), ThemeColors.primary()));
                } finally {
                    pose.popMatrix();
                }
            }
            if (counter.get()) {
                String text = kills == 1 ? "1 kill" : kills + " kills";
                float y = centerY + logoWidth / 2 + 6;
                renderer.text.centered(text, centerX + 1, y + 1, COUNTER_SIZE, 0x99000000);
                renderer.text.centered(text, centerX, y, COUNTER_SIZE, ThemeColors.primary());
            }
            for (float time : popups) {
                drawPopup(renderer, pose, centerX, centerY, time / POPUP_TIME);
            }
        }
    }

    private void drawPopup(Renderer2D renderer, Matrix3x2fStack pose, float centerX, float centerY, float progress) {
        float pop = Math.min(progress / 0.18f, 1);
        float scale = pop < 1 ? 0.4f + 1.1f * FrameClock.smoothStep(pop) : 1.5f - 0.5f * FrameClock.smoothStep(Math.min((progress - 0.18f) / 0.2f, 1));
        float fade = progress < 0.65f ? 1 : 1 - FrameClock.smoothStep((progress - 0.65f) / 0.35f);
        float rise = 40 * FrameClock.smoothStep(progress);
        int alpha = Math.round(255 * fade);
        float lineHeight = renderer.text.lineHeight(POPUP_SIZE);
        pose.pushMatrix();
        try {
            pose.translate(centerX, centerY - rise);
            pose.scale(scale);
            renderer.text.centered("-1", 2, -lineHeight / 2 + 2, POPUP_SIZE, ARGB.color(alpha * 3 / 5, 0xFF000000));
            renderer.text.centered("-1", 0, -lineHeight / 2, POPUP_SIZE, ARGB.color(alpha, KILL_COLOR));
        } finally {
            pose.popMatrix();
        }
    }
}
