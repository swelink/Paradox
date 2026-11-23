package cc.swelink.systems.modules.render;

import cc.swelink.utils.combat.Targets;
import cc.swelink.Paradox;
import cc.swelink.renderer.DigitAnimation;
import cc.swelink.renderer.HudBackdrop;
import cc.swelink.renderer.Glass;
import cc.swelink.renderer.HudPopAnimation;
import cc.swelink.renderer.AccentWave;
import cc.swelink.renderer.Renderer2D;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.SpringAnimation;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.settings.SecondsSetting;
import cc.swelink.systems.hud.PlacedHudElement;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.hud.HudPlacement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.EntityHitResult;

import java.util.List;
import java.util.Locale;

public final class TargetHudModule extends Module implements PlacedHudElement {
    private static final int TRACK = 0xFF252529;
    private static final float PADDING = 4;
    private static final float MIN_WIDTH = 172;
    private static final float HEAD_SIZE = 36;
    private static final float HEAD_PANEL_WIDTH = 52;
    private static final float INFO_PADDING = 8;
    private static final float HEIGHT = 52;
    private static final float HEALTH_BAR_HEIGHT = 8;
    private static final float BAR_INSET = 6;
    private static final float FONT_SIZE = 14;
    private static final float HEALTH_SIZE = 11;
    private static final float DAMAGE_DURATION = 0.35f;
    private static final float TRAIL_HOLD = 0.3f;
    private static final int DAMAGE_COLOR = 0xFFFF5252;
    private final SecondsSetting holdTime = addSetting(new SecondsSetting("Hold time", 10, 50));
    private AbstractClientPlayer heldTarget;
    private long lastSeen;
    private final BooleanSetting shadows = addSetting(new BooleanSetting("Shadows", true));
    private final HudPlacement placement = new HudPlacement(0.5f, -1, true, 40);
    private final SpringAnimation visibilityMotion = new SpringAnimation();
    private final SpringAnimation healthMotion = new SpringAnimation();
    private final DigitAnimation healthDigits = new DigitAnimation(HEALTH_SIZE);
    private final SpringAnimation damageTrailMotion = new SpringAnimation();
    private AbstractClientPlayer healthTarget;
    private float previousHealth;
    private int previousHurtTime;
    private float damageAge = DAMAGE_DURATION;
    private float trailHold;
    private boolean trailReleasing;
    private final FrameClock visibilityClock = new FrameClock();
    private float visibility;
    private float width;
    private boolean visible;
    private AbstractClientPlayer exitingTarget;

    public TargetHudModule() {
        super("TargetHUD", "Shows your target's health", Category.RENDER);
    }

    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if (client.player == null || client.level == null || client.gui.hud.isHidden()) {
            resetHeldTarget();
            clearVisibility();
            return;
        }
        boolean chat = HudPlacement.canDrag(client);
        AbstractClientPlayer selected = isEnabled() ? getTarget(client, chat) : null;
        float delta = visibilityClock.nextDelta();
        visibility = Math.clamp(visibilityMotion.update(selected == null ? 0 : 1, 20, 0.85f,
                delta), 0, 1);
        if (selected != null) exitingTarget = selected;
        AbstractClientPlayer player = exitingTarget;
        if (player == null || player.level() != client.level || selected == null && visibility <= 0.001f) {
            clearVisibility();
            return;
        }
        visible = selected != null;
        if (!chat || !visible) stopDragging();
        float actualHealth = healthFraction(player);
        float totalHealth = player.getHealth() + player.getAbsorptionAmount();
        if (healthTarget != player) {
            healthTarget = player;
            healthMotion.reset(actualHealth);
            damageTrailMotion.reset(actualHealth);
            damageAge = DAMAGE_DURATION;
            trailHold = 0;
            trailReleasing = false;
        } else {
            damageAge = Math.min(DAMAGE_DURATION, damageAge + delta);
            trailHold = Math.max(0, trailHold - delta);
            if (totalHealth < previousHealth - 0.001f || player.hurtTime > previousHurtTime) {
                damageAge = 0;
                if (!trailReleasing && trailHold <= 0) {
                    trailHold = TRAIL_HOLD;
                    damageTrailMotion.reset(damageTrailMotion.value());
                }
            }
        }
        previousHealth = totalHealth;
        previousHurtTime = player.hurtTime;
        float health = Math.clamp(healthMotion.update(actualHealth, 14, 1, delta), 0, 1);
        float trail = trailHold > 0 ? damageTrailMotion.value() : damageTrailMotion.update(actualHealth, 5, 1, delta);
        if (damageTrailMotion.value() < health) damageTrailMotion.reset(health);
        trailReleasing = trailHold <= 0 && damageTrailMotion.value() > health + 0.002f;
        float trailingHealth = Math.max(health, Math.clamp(trail, 0, 1));
        float scale = placement.renderScale(client);
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale);
        try (var renderer = new Renderer2D(graphics)) {
            String name = player.getName().getString();
            float targetWidth = Math.max(MIN_WIDTH, renderer.text.measure(name, FONT_SIZE).width() + HEAD_PANEL_WIDTH + INFO_PADDING * 2);
            width = width <= 0 ? targetWidth : FrameClock.approach(width, targetWidth, 22, delta);
            if (Math.abs(width - targetWidth) < 0.1f) width = targetWidth;
            placement.update(client, width, HEIGHT);
            renderer.opacity(visibility);
            int edges = placement.touchedEdges();
            HudPopAnimation.pop(graphics.pose(), visibility, placement.x(), placement.y(), width, HEIGHT, edges);
            graphics.pose().translate(placement.x(), placement.y());
            drawHud(renderer, player, width, health, trailingHealth, damageAge, edges, healthDigits, delta);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void clearVisibility() {
        visibility = 0;
        visibilityMotion.reset(0);
        visibilityClock.reset();
        exitingTarget = null;
        healthTarget = null;
        healthMotion.reset(0);
        damageTrailMotion.reset(0);
        previousHealth = 0;
        previousHurtTime = 0;
        damageAge = DAMAGE_DURATION;
        trailHold = 0;
        trailReleasing = false;
        width = 0;
        placement.clearSize();
        visible = false;
        stopDragging();
    }

    private AbstractClientPlayer getTarget(Minecraft client, boolean chat) {
        if (chat) {
            resetHeldTarget();
            return client.player;
        }
        AbstractClientPlayer selected = null;
        if (Paradox.TARGETS.current() instanceof AbstractClientPlayer reported && reported != client.player
                && reported.isAlive() && !reported.isSpectator() && !Targets.bot(reported)) {
            selected = reported;
        } else if (client.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof AbstractClientPlayer player && player != client.player
                && player.isAlive() && !player.isSpectator() && !Targets.bot(player)) {
            selected = player;
        }
        return holdTarget(selected, holdTime.seconds(), System.nanoTime());
    }

    private static float healthFraction(AbstractClientPlayer player) {
        return Math.clamp(player.getHealth() / Math.max(1, player.getMaxHealth()), 0, 1);
    }

    private void drawHud(Renderer2D renderer, AbstractClientPlayer player, float width, float health,
                         float trailingHealth, float damageAge, int edges, DigitAnimation healthDigits, float delta) {
        String name = player.getName().getString();
        float radius = 10;
        float headRadius = 6;
        if (shadows.get()) {
            HudBackdrop.draw(renderer, List.of(HudBackdrop.docked(0, 0, width, HEIGHT, radius, edges)));
        }
        renderer.blur.frostedGlass(renderer, Glass.HUD, 0, 0, width, HEIGHT, radius, ThemeColors.hudPanel(),
                HEAD_PANEL_WIDTH, ThemeColors.hudHeader(), edges);
        float headX = (HEAD_PANEL_WIDTH - HEAD_SIZE) / 2;
        float headY = (HEIGHT - HEAD_SIZE) / 2;
        float hurt = 1 - FrameClock.smoothStep(Math.clamp(damageAge / DAMAGE_DURATION, 0, 1));
        float shake = 1 - Math.clamp(damageAge / 0.24f, 0, 1);
        float headOffset = (float) Math.sin(damageAge * 75) * 1.5f * shake * shake;
        float headOffsetY = (float) Math.sin(damageAge * 95) * 0.8f * shake * shake;
        int headColor = ARGB.srgbLerp(hurt, 0xFFFFFFFF, 0xFFFF7070);
        var skin = player.getSkin().body().texturePath();
        renderer.textures.roundedRegion(skin, headX + headOffset, headY + headOffsetY, HEAD_SIZE, HEAD_SIZE, headRadius,
                8f / 64, 8f / 64, 16f / 64, 16f / 64, headColor);
        renderer.textures.roundedRegion(skin, headX + headOffset, headY + headOffsetY, HEAD_SIZE, HEAD_SIZE, headRadius,
                40f / 64, 8f / 64, 48f / 64, 16f / 64, headColor);
        float contentX = HEAD_PANEL_WIDTH + INFO_PADDING;
        float contentWidth = width - HEAD_PANEL_WIDTH - INFO_PADDING * 2;
        float nameHeight = renderer.text.lineHeight(FONT_SIZE);
        float nameY = 5;
        try (var clip = renderer.clips.push(contentX, nameY - 1, contentWidth, nameHeight + 2)) {
            renderer.text.draw(name, contentX, nameY, FONT_SIZE, 0xFFFFFFFF);
        }
        float barY = HEIGHT - BAR_INSET - HEALTH_BAR_HEIGHT;
        float barWidth = width - contentX - BAR_INSET;
        float heartSize = 11;
        float heartY = barY - heartSize - 3;
        float beatProgress = Math.clamp(damageAge / DAMAGE_DURATION, 0, 1);
        float beat = (float) Math.sin(beatProgress * Math.PI * 2);
        float pulsingHeartSize = heartSize * (1 + 0.4f * beat * beat * (1 - beatProgress));
        float heartOffset = (pulsingHeartSize - heartSize) / 2;
        renderer.textures.icon(Paradox.id("icons/hud/heart.png"), contentX - heartOffset, heartY - heartOffset,
                pulsingHeartSize, AccentWave.text(DAMAGE_COLOR, 1 - hurt));
        float displayedHealth = Math.round(health * player.getMaxHealth() * 10) / 10f;
        String healthText = displayedHealth == Math.round(displayedHealth)
                ? Integer.toString(Math.round(displayedHealth)) : String.format(Locale.ROOT, "%.1f", displayedHealth);
        healthDigits.update(renderer, healthText, delta);
        healthDigits.draw(renderer, contentX + heartSize + 4,
                heartY + (heartSize - renderer.text.lineHeight(HEALTH_SIZE)) / 2, (glyphX, glyphY) -> 0xFFE0E0E4);
        if (health < 1) {
            renderer.sdf.roundedRect(contentX, barY, barWidth, HEALTH_BAR_HEIGHT, 3,
                    TRACK);
        }
        float filled = barWidth * health;
        float trailingFilled = barWidth * trailingHealth;
        if (trailingFilled > filled + 0.1f) {
            renderer.sdf.clippedRoundedRect(contentX, barY, barWidth, HEALTH_BAR_HEIGHT, 3, DAMAGE_COLOR,
                    contentX + filled, barY - 1, contentX + trailingFilled + (trailingHealth >= 1 ? 1 : 0),
                    barY + HEALTH_BAR_HEIGHT + 1);
        }
        if (filled > 0) {
            renderer.sdf.clippedRoundedRect(contentX, barY, barWidth, HEALTH_BAR_HEIGHT, 3, ThemeColors.primary(),
                    contentX - 1, barY - 1, contentX + filled + (health >= 1 ? 1 : 0), barY + HEALTH_BAR_HEIGHT + 1);
        }
    }

    @Override
    public float cornerRadius() {
        return 10;
    }

    @Override
    public HudPlacement placement() {
        return placement;
    }

    @Override
    public boolean visibleForDragging() {
        return visible;
    }

    @Override
    protected void onEnable() {
        healthDigits.reset();
        clearVisibility();
        resetHeldTarget();
    }

    @Override
    protected void onDisable() {
        resetHeldTarget();
        stopDragging();
    }
    private AbstractClientPlayer holdTarget(AbstractClientPlayer selected, double seconds, long now) {
        if (selected != null) {
            heldTarget = selected;
            lastSeen = now;
        }
        if (heldTarget == null || !heldTarget.isAlive() || heldTarget.isSpectator() || heldTarget.isRemoved()
                || now - lastSeen > seconds * 1_000_000_000L) {
            heldTarget = null;
        }
        return heldTarget;
    }

    private void resetHeldTarget() {
        heldTarget = null;
        lastSeen = 0;
    }
}
