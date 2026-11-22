package cc.swelink.systems.modules.combat.velocity.mode;

import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.settings.PercentSetting;
import cc.swelink.systems.modules.combat.velocity.VelocityModule;
import cc.swelink.utils.player.Gameplay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

public final class JumpResetVelocity extends VelocityMode {
    private final PercentSetting chance = add(new PercentSetting("Chance", 100));
    private final ModeSetting cooldown = add(new ModeSetting("Jump cooldown", "Ticks", "Off", "Ticks", "Hits"));
    private final IntSetting ticks = add(new IntSetting("Ticks until jump", 2, 0, 20), () -> cooldown.get().equals("Ticks"));
    private final IntSetting hits = add(new IntSetting("Hits until jump", 2, 0, 10), () -> cooldown.get().equals("Hits"));
    private int jumpCounter;
    private boolean fallDamage;

    public JumpResetVelocity(VelocityModule module) {
        super(module, "Jump Reset");
    }

    @Override
    public void enable() {
        reset();
    }

    @Override
    public void disable() {
        reset();
    }

    private void reset() {
        jumpCounter = 0;
        fallDamage = false;
    }

    @Override
    public Vec3 motion(LocalPlayer player, Vec3 movement) {
        fallDamage = movement.x == 0 && movement.z == 0 && movement.y < 0;
        return movement;
    }

    @Override
    public Input prepareInput(Minecraft client, Input input) {
        var player = client.player;
        boolean cooldownReady = switch (cooldown.get()) {
            case "Ticks" -> jumpCounter >= ticks.get();
            case "Hits" -> jumpCounter >= hits.get();
            default -> true;
        };
        if (!player.isAlive() || player.isSpectator() || !Gameplay.focused(client) || player.hurtTime != 9 || !player.onGround()
                || !player.isSprinting() || fallDamage || !cooldownReady
                || ThreadLocalRandom.current().nextInt(100) >= chance.get()) {
            if (!cooldown.get().equals("Hits") || player.hurtTime == 9) {
                if (jumpCounter < Integer.MAX_VALUE) {
                    jumpCounter++;
                }
            }
            return input;
        }
        jumpCounter = 0;
        return new Input(input.forward(), input.backward(), input.left(), input.right(),
                true, input.shift(), input.sprint());
    }
}
