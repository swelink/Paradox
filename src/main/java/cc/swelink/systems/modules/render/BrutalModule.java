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

import java.util.HashMap;
import java.util.Map;

public final class BrutalModule extends Module {
    private static final Identifier LOGO = Paradox.id("icons/paradox-logo.png");
    private static final float LOGO_U0 = 92 / 1024f;
    private static final float LOGO_V0 = 253 / 1024f;
    private static final float LOGO_U1 = 932 / 1024f;
    private static final float LOGO_V1 = 771 / 1024f;
    private static final float LOGO_ASPECT = 840 / 518f;
    private static final long KILL_WINDOW = 5_000_000_000L;
    private static final float POP_TIME = 0.6f;
    private static final float FLASH_TIME = 0.5f;
    private static final float FLASH_ALPHA = 0.45f;
    private static final float COUNTER_HEIGHT = 0.3f;
    private static final float COUNTER_MARGIN = 0.03f;
    private static final int BLOOD = 0xFFE00000;
    private static int kills;

    private final IntSetting size = addSetting(new IntSetting("Logo size", 45, 10, 100));
    private final IntSetting speed = addSetting(new IntSetting("Spin speed", 90, 0, 720));
    private final PercentSetting opacity = addSetting(new PercentSetting("Logo opacity", 35));
    private final BooleanSetting counter = addSetting(new BooleanSetting("Kill counter", true));
    private final Map<Integer, Long> attacked = new HashMap<>();
    private float sinceKill = POP_TIME;
    private final FrameClock clock = new FrameClock();
    private float angle;

    public BrutalModule() {
        super("Brutal", "Spinning logo and kill counter", Category.RENDER);
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
            sinceKill = 0;
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
        sinceKill = Math.min(sinceKill + delta, POP_TIME);

        float centerX = graphics.guiWidth() / 2f;
        float centerY = graphics.guiHeight() / 2f;
        var pose = graphics.pose();
        try (var renderer = new Renderer2D(graphics, TextRenderer.Font.INTER_MEDIUM)) {
            float flash = 1 - FrameClock.smoothStep(Math.min(sinceKill / FLASH_TIME, 1));
            if (flash > 0) {
                renderer.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                        ARGB.color(Math.round(255 * FLASH_ALPHA * flash), BLOOD));
            }
            float logoWidth = graphics.guiHeight() * size.get() / 100f;
            float logoHeight = logoWidth / LOGO_ASPECT;
            if (opacity.get() > 0 && logoWidth > 0) {
                pose.pushMatrix();
                try {
                    pose.translate(centerX, centerY);
                    pose.rotate((float) Math.toRadians(angle));
                    renderer.textures.iconRegion(LOGO, -logoWidth / 2, -logoHeight / 2, logoWidth, logoHeight,
                            LOGO_U0, LOGO_V0, LOGO_U1, LOGO_V1,
                            ARGB.color(Math.round(255 * opacity.get() / 100f), BLOOD));
                } finally {
                    pose.popMatrix();
                }
            }
            if (counter.get()) {
                drawCounter(renderer, pose, graphics.guiWidth() * COUNTER_MARGIN, centerY, graphics.guiHeight() * COUNTER_HEIGHT);
            }
        }
    }

    private void drawCounter(Renderer2D renderer, Matrix3x2fStack pose, float left, float centerY, float size) {
        String text = "-" + kills;
        float progress = sinceKill / POP_TIME;
        float scale = 1 + 0.35f * (1 - FrameClock.smoothStep(progress));
        float lineHeight = renderer.text.lineHeight(size);
        float shadow = size / 30;
        pose.pushMatrix();
        try {
            pose.translate(left, centerY);
            pose.scale(scale);
            renderer.text.draw(text, shadow, -lineHeight / 2 + shadow, size, 0x99000000);
            renderer.text.draw(text, 0, -lineHeight / 2, size, BLOOD);
        } finally {
            pose.popMatrix();
        }
    }
}
