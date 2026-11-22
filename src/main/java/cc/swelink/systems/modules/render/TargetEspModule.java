package cc.swelink.systems.modules.render;

import cc.swelink.utils.combat.Targets;
import cc.swelink.Paradox;
import cc.swelink.renderer.FrameClock;
import cc.swelink.renderer.SpringAnimation;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.settings.SecondsSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

public final class TargetEspModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Ghost", "Ghost", "Crosshair", "Ring"));
    private final IntSetting size = addSetting(new IntSetting("Size", 75, 25, 150));
    private final IntSetting speed = addSetting(new IntSetting("Speed", 10, 2, 30));
    private final IntSetting length = addSetting(new IntSetting("Trail length", 25, 10, 40));
    private final SecondsSetting holdTime = addSetting(new SecondsSetting("Hold time", 15, 50));
    private final BooleanSetting others = addSetting(new BooleanSetting("Others", false));
    private final FrameClock clock = new FrameClock();
    private final SpringAnimation visibility = new SpringAnimation();
    private ClientLevel level;
    private Entity target;
    private long lastSeen;
    private double animationTime;

    public TargetEspModule() {
        super("TargetESP", "Highlights your target", Category.RENDER);
        length.visibleWhen(() -> mode.get().equals("Ghost"));
    }

    public Snapshot snapshot(Minecraft client) {
        if (client.player == null || client.level == null || client.gui.hud.isHidden()) {
            reset();
            return null;
        }
        if (level != client.level) {
            reset();
            level = client.level;
        }
        Entity selected = null;
        long now = System.nanoTime();
        if (isEnabled()) {
            selected = Paradox.TARGETS.current();
            if (!valid(client, selected)) selected = null;
            if (selected == null && client.hitResult instanceof EntityHitResult hit && valid(client, hit.getEntity())) {
                selected = hit.getEntity();
            }
            if (selected != null) {
                if (target != selected) visibility.reset(0);
                target = selected;
                lastSeen = now;
            } else if (valid(client, target) && now - lastSeen < holdTime.seconds() * 1e9) {
                selected = target;
            }
        }
        if (!valid(client, target)) {
            target = null;
            visibility.reset(0);
        }
        float delta = clock.nextDelta();
        animationTime += delta * speed.get() / 10.0;
        float opacity = visibility.update(selected == null ? 0 : 1, 20, 1, delta);
        if (target == null || opacity <= 0.001f) return null;
        return new Snapshot(target, opacity, mode.get(), size.get() / 100f, animationTime, length.get());
    }

    private boolean valid(Minecraft client, Entity candidate) {
        if (candidate instanceof Player) return Targets.attackable(client, candidate);
        return others.get() && candidate != null && !(candidate instanceof ArmorStand) && candidate.isAlive()
                && !candidate.isRemoved() && candidate.level() == client.level;
    }

    private void reset() {
        target = null;
        level = null;
        lastSeen = 0;
        animationTime = 0;
        visibility.reset(0);
        clock.reset();
    }

    @Override
    protected void onEnable() {
        reset();
    }

    public record Snapshot(Entity target, float opacity, String mode, float size, double time, int length) {
    }
}
