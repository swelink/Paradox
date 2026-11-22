package cc.swelink.systems.modules.combat.velocity.mode;

import cc.swelink.systems.modules.combat.velocity.VelocityModule;

import net.minecraft.client.Minecraft;

public final class MinemenVelocity extends VelocityMode {
    private static final double REDUCE = 0.01;

    public MinemenVelocity(VelocityModule module) {
        super(module, "Minemen");
    }

    @Override
    public void preTick(Minecraft client) {
        var player = client.player;
        if (player != null && player.hurtTime > 0 && !player.onGround()) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(REDUCE, 1, REDUCE));
        }
    }
}
