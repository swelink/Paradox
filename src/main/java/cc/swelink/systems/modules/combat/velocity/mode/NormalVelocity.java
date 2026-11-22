package cc.swelink.systems.modules.combat.velocity.mode;

import cc.swelink.settings.PercentSetting;
import cc.swelink.systems.modules.combat.velocity.VelocityModule;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;

public final class NormalVelocity extends VelocityMode {
    private final PercentSetting horizontal = add(new PercentSetting("Horizontal", 0));
    private final PercentSetting vertical = add(new PercentSetting("Vertical", 0));

    public NormalVelocity(VelocityModule module) {
        super(module, "Custom");
    }

    @Override
    public Vec3 motion(LocalPlayer player, Vec3 movement) {
        return scale(movement);
    }

    public boolean cancelMotion() {
        return horizontal.get() == 0 && vertical.get() == 0;
    }

    @Override
    public Vec3 explosion(ClientboundExplodePacket packet, Vec3 movement) {
        return scale(movement);
    }

    private Vec3 scale(Vec3 movement) {
        return movement.multiply(horizontal.get() / 100.0, vertical.get() / 100.0, horizontal.get() / 100.0);
    }
}
