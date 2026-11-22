package cc.swelink.systems.modules.combat;

import cc.swelink.events.AttackEvent;
import cc.swelink.events.EventHandler;
import cc.swelink.events.InputEvent;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.settings.PercentSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.Targets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;

import java.util.concurrent.ThreadLocalRandom;

public final class SprintResetModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "SprintTap", "SprintTap", "WTap"));
    private final PercentSetting chance = addSetting(new PercentSetting("Chance", 100));
    private final IntSetting hurtTime = addSetting(new IntSetting("Hurt time", 10, 0, 10));
    private final IntSetting resprint = addSetting(new IntSetting("Resprint ticks", 0, 0, 10));
    private final IntSetting delay = addSetting(new IntSetting("Delay ticks", 0, 0, 10));
    private final IntSetting release = addSetting(new IntSetting("Release ticks", 1, 1, 10));
    private final BooleanSetting onlyForward = addSetting(new BooleanSetting("Only forward", true));
    private final BooleanSetting onlyGround = addSetting(new BooleanSetting("Only on ground", false));
    private final BooleanSetting notInWater = addSetting(new BooleanSetting("Not in water", true));
    private int blockFrom = Integer.MAX_VALUE;
    private int blockUntil = Integer.MIN_VALUE;

    public SprintResetModule() {
        super("SprintReset", "Resets sprint after hits", Category.COMBAT);
        resprint.visibleWhen(() -> mode.get().equals("SprintTap"));
        delay.visibleWhen(() -> mode.get().equals("WTap"));
        release.visibleWhen(() -> mode.get().equals("WTap"));
    }

    private void attacked(Minecraft client, int entityId) {
        LocalPlayer player = client.player;
        if (!isEnabled() || player == null || client.level == null || active(player)) return;
        if (!(client.level.getEntity(entityId) instanceof LivingEntity target) || target.hurtTime > Math.min(hurtTime.get() + Targets.latencyTicks(client), 10)) return;
        if (!player.isSprinting() || player.isPassenger() || player.isFallFlying()) return;
        if (onlyGround.get() && !player.onGround()) return;
        if (notInWater.get() && player.isInWater()) return;
        Input keys = player.input.keyPresses;
        if (onlyForward.get() && (!keys.forward() || keys.backward() || keys.left() != keys.right())) return;
        if (ThreadLocalRandom.current().nextInt(100) >= chance.get()) return;
        int next = player.tickCount + 1;
        if (mode.get().equals("SprintTap")) {
            blockFrom = next;
            blockUntil = next + 1 + resprint.get();
        } else {
            blockFrom = next + delay.get();
            blockUntil = blockFrom + release.get();
        }
    }

    private boolean active(LocalPlayer player) {
        return player.tickCount >= blockFrom && player.tickCount < blockUntil;
    }

    public boolean blocksSprint(LocalPlayer player) {
        return isEnabled() && mode.get().equals("SprintTap") && active(player);
    }

    private Input prepareInput(Minecraft client, Input input) {
        LocalPlayer player = client.player;
        if (!isEnabled() || player == null || !mode.get().equals("WTap") || !active(player)) return input;
        return new Input(false, false, false, false, input.jump(), input.shift(), input.sprint());
    }

    private void reset() {
        blockFrom = Integer.MAX_VALUE;
        blockUntil = Integer.MIN_VALUE;
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    @EventHandler(priority = 60)
    private void onAttack(AttackEvent event) {
        attacked(event.client(), event.entityId());
    }

    @EventHandler(priority = 10)
    private void onInput(InputEvent event) {
        event.input(prepareInput(event.client(), event.input()));
    }
}
