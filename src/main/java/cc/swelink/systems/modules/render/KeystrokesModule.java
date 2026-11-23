package cc.swelink.systems.modules.render;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.PacketSentEvent;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.HudPopAnimation;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.SpringAnimation;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.systems.hud.PlacedHudElement;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.hud.HudPlacement;
import cc.swelink.utils.rotation.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.ARGB;


public final class KeystrokesModule extends Module implements PlacedHudElement {
    private static final float GAP = 2;
    private static final float KEY_SIZE = 36;
    private static final float KEY_RADIUS = 5;
    private static final float SPACE_HEIGHT = 20;
    private static final float WIDTH = KEY_SIZE * 3 + GAP * 2;
    private static final float HEIGHT = KEY_SIZE * 3 + SPACE_HEIGHT + GAP * 3;
    private static final float FONT_SIZE = 13;
    private static final float SMALL_FONT_SIZE = 11;
    private static final float DOT_SIZE = 6;
    private static final float MOUSE_RANGE = 12;
    private static final int TRAIL_SAMPLES = 256;
    private static final float TRAIL_LIFETIME = 0.28f;
    private static final float TRAIL_STRENGTH = 0.09f;
    private static final float TRAIL_STEP = 0.4f;
    private static final int TRAIL_MAX_DOTS = 320;
    private static final float MOUSE_SPEED = 22;
    private static final long PRESS_NANOS = 120_000_000L;
    private static final int FORWARD = 0;
    private static final int LEFT = 1;
    private static final int BACKWARD = 2;
    private static final int RIGHT = 3;
    private static final int ATTACK = 4;
    private static final int USE = 5;
    private static final int JUMP = 6;
    private final HudPlacement placement = new HudPlacement(0, 0.3f, false).withCenterSnap();
    private final SpringAnimation visibility = new SpringAnimation();
    private final FrameClock clock = new FrameClock();
    private final SpringAnimation[] keys = new SpringAnimation[JUMP + 1];
    private long attackUntil;
    private long useUntil;
    private Rotation lastRotation;
    private float mousePreviousX;
    private float mousePreviousY;
    private float mouseTargetX;
    private float mouseTargetY;
    private final SpringAnimation mouseSpringX = new SpringAnimation();
    private final SpringAnimation mouseSpringY = new SpringAnimation();
    private float mouseX;
    private float mouseY;
    private final float[] trailX = new float[TRAIL_SAMPLES];
    private final float[] trailY = new float[TRAIL_SAMPLES];
    private final float[] trailAge = new float[TRAIL_SAMPLES];
    private int trailHead;
    private int trailCount;

    public KeystrokesModule() {
        super("Keystrokes", "Shows your keys and mouse", Category.RENDER);
        for (int i = 0; i < keys.length; i++) keys[i] = new SpringAnimation();
    }

    @EventHandler(priority = 0)
    private void onPacketSent(PacketSentEvent event) {
        packetSent(event);
    }

    private void packetSent(PacketSentEvent event) {
        var packet = event.packet();
        if (packet instanceof ServerboundAttackPacket) {
            attackUntil = System.nanoTime() + PRESS_NANOS;
        } else if (packet instanceof ServerboundPlayerActionPacket action) {
            if (action.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                attackUntil = System.nanoTime() + PRESS_NANOS;
            }
        } else if (packet instanceof ServerboundUseItemOnPacket || packet instanceof ServerboundUseItemPacket
                || packet instanceof ServerboundInteractPacket) {
            useUntil = System.nanoTime() + PRESS_NANOS;
        }
    }

    @Override
    public void tick(Minecraft client) {
        var current = Paradox.ROTATIONS.serverRotation();
        var previous = lastRotation;
        lastRotation = current;
        mousePreviousX = mouseTargetX;
        mousePreviousY = mouseTargetY;
        if (client.isPaused() || current == null || previous == null) {
            mouseTargetX = 0;
            mouseTargetY = 0;
            return;
        }
        mouseTargetX = Math.clamp(Mth.wrapDegrees(current.yaw() - previous.yaw()) / MOUSE_RANGE, -1, 1);
        mouseTargetY = Math.clamp((current.pitch() - previous.pitch()) / MOUSE_RANGE, -1, 1);
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
        var input = client.player.input.keyPresses;
        long now = System.nanoTime();
        press(FORWARD, input.forward(), delta);
        press(LEFT, input.left(), delta);
        press(BACKWARD, input.backward(), delta);
        press(RIGHT, input.right(), delta);
        press(JUMP, input.jump(), delta);
        press(ATTACK, client.options.keyAttack.isDown() || now < attackUntil, delta);
        press(USE, client.options.keyUse.isDown() || now < useUntil, delta);
        float partial = client.isPaused() ? 1 : Math.clamp(client.getDeltaTracker().getGameTimeDeltaPartialTick(false), 0, 1);
        float targetX = Mth.lerp(partial, mousePreviousX, mouseTargetX);
        float targetY = Mth.lerp(partial, mousePreviousY, mouseTargetY);
        mouseX = mouseSpringX.update(targetX, MOUSE_SPEED, 1, delta);
        mouseY = mouseSpringY.update(targetY, MOUSE_SPEED, 1, delta);
        updateTrail(delta);
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(placement.renderScale(client));
        try {
            placement.updateAnchored(client, WIDTH, HEIGHT, HEIGHT);
            float x = placement.x();
            float y = placement.y();
            HudPopAnimation.pop(pose, reveal, x, y, WIDTH, HEIGHT, placement.touchedEdges());
            try (var renderer = new Renderer2D(graphics)) {
                renderer.opacity(reveal);
                float step = KEY_SIZE + GAP;
                drawKey(renderer, "W", x + step, y, KEY_SIZE, KEY_SIZE, FONT_SIZE, FORWARD);
                drawKey(renderer, "A", x, y + step, KEY_SIZE, KEY_SIZE, FONT_SIZE, LEFT);
                drawKey(renderer, "S", x + step, y + step, KEY_SIZE, KEY_SIZE, FONT_SIZE, BACKWARD);
                drawKey(renderer, "D", x + step * 2, y + step, KEY_SIZE, KEY_SIZE, FONT_SIZE, RIGHT);
                float buttonY = y + step * 2;
                drawKey(renderer, "LMB", x, buttonY, KEY_SIZE, KEY_SIZE, SMALL_FONT_SIZE, ATTACK);
                drawMouse(renderer, x + step, buttonY);
                drawKey(renderer, "RMB", x + step * 2, buttonY, KEY_SIZE, KEY_SIZE, SMALL_FONT_SIZE, USE);
                drawKey(renderer, "SPACE", x, buttonY + step, WIDTH, SPACE_HEIGHT, SMALL_FONT_SIZE, JUMP);
            }
        } finally {
            pose.popMatrix();
        }
    }

    private void press(int key, boolean pressed, float delta) {
        keys[key].update(pressed ? 1 : 0, 34, 1, delta);
    }

    private void drawKey(Renderer2D renderer, String label, float x, float y, float width, float height, float size, int key) {
        float amount = Math.clamp(keys[key].value(), 0, 1);
        renderer.blur.hudGlass(renderer, x, y, width, height, KEY_RADIUS, ThemeColors.hudPanel());
        renderer.sdf.roundedRect(x, y, width, height, KEY_RADIUS, ARGB.color(amount, ThemeColors.primary()));
        renderer.text.centeredInBox(label, x, y, width, height, size, ThemeColors.text());
    }

    private void updateTrail(float delta) {
        for (int i = 0; i < TRAIL_SAMPLES; i++) trailAge[i] += delta;
        while (trailCount > 0 && trailAge[Math.floorMod(trailHead - trailCount, TRAIL_SAMPLES)] > TRAIL_LIFETIME) trailCount--;
        trailX[trailHead] = mouseX;
        trailY[trailHead] = mouseY;
        trailAge[trailHead] = 0;
        trailHead = (trailHead + 1) % TRAIL_SAMPLES;
        trailCount = Math.min(trailCount + 1, TRAIL_SAMPLES);
    }

    private void drawMouse(Renderer2D renderer, float x, float y) {
        renderer.blur.hudGlass(renderer, x, y, KEY_SIZE, KEY_SIZE, KEY_RADIUS, ThemeColors.hudPanel());
        float center = KEY_SIZE / 2;
        float range = center - DOT_SIZE / 2 - 3;
        int color = ThemeColors.primary();
        float length = 0;
        for (int i = 1; i < trailCount; i++) {
            int a = Math.floorMod(trailHead - trailCount + i - 1, TRAIL_SAMPLES);
            int b = Math.floorMod(trailHead - trailCount + i, TRAIL_SAMPLES);
            length += (float) Math.hypot(trailX[b] - trailX[a], trailY[b] - trailY[a]) * range;
        }
        float step = Math.max(TRAIL_STEP, length / TRAIL_MAX_DOTS);
        float carry = 0;
        for (int i = 1; i < trailCount; i++) {
            int a = Math.floorMod(trailHead - trailCount + i - 1, TRAIL_SAMPLES);
            int b = Math.floorMod(trailHead - trailCount + i, TRAIL_SAMPLES);
            float ax = trailX[a] * range;
            float ay = trailY[a] * range;
            float bx = trailX[b] * range;
            float by = trailY[b] * range;
            float segment = (float) Math.hypot(bx - ax, by - ay);
            if (segment <= 0) continue;
            float t = carry;
            while (t < segment) {
                float f = t / segment;
                float age = Mth.lerp(f, trailAge[a], trailAge[b]);
                float life = 1 - Math.clamp(age / TRAIL_LIFETIME, 0, 1);
                if (life > 0) {
                    float fade = FrameClock.smoothStep(life);
                    float size = DOT_SIZE * (0.45f + 0.55f * fade);
                    drawDot(renderer, x + center + Mth.lerp(f, ax, bx), y + center + Mth.lerp(f, ay, by), size,
                            ARGB.color(Math.clamp(fade * TRAIL_STRENGTH * (step / TRAIL_STEP), 0, 1), color));
                }
                t += step;
            }
            carry = t - segment;
        }
        drawDot(renderer, x + center + mouseX * range, y + center + mouseY * range, DOT_SIZE, color);
    }

    private static void drawDot(Renderer2D renderer, float x, float y, float size, int color) {
        renderer.sdf.roundedRect(x - size / 2, y - size / 2, size, size, size / 2, color);
    }

    private void hide() {
        visibility.reset(0);
        placement.clearSize();
        stopDragging();
        clock.reset();
    }

    @Override
    public float cornerRadius() {
        return KEY_RADIUS;
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
        for (var key : keys) key.reset(0);
        lastRotation = null;
        mouseTargetX = 0;
        mouseTargetY = 0;
        mousePreviousX = 0;
        mousePreviousY = 0;
        mouseSpringX.reset(0);
        mouseSpringY.reset(0);
        mouseX = 0;
        mouseY = 0;
        trailHead = 0;
        trailCount = 0;
    }

    @Override
    protected void onDisable() {
        stopDragging();
    }
}
