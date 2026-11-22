package cc.swelink.systems.modules.movement.speed.mode;

import cc.swelink.Paradox;
import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.movement.TargetStrafeModule;
import cc.swelink.systems.modules.movement.speed.SpeedModule;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class CustomSpeed extends SpeedMode {
    private final IntSetting speed = add(new IntSetting("Speed", 80, 10, 300) {
        @Override
        protected String formatValue(int value) {
            return String.format(Locale.ROOT, "%.1f b/s", value / 10f);
        }
    });

    public CustomSpeed(SpeedModule module) {
        super(module, "Custom");
    }

    @Override
    public Vec3 movement(Minecraft client, Vec3 direction, Vec3 original) {
        double perTick = speed.get() / 10.0 / 20.0;
        Vec3 orbit = Paradox.MODULES.get(TargetStrafeModule.class).customMovement(client, perTick);
        if (orbit != null) return new Vec3(orbit.x, original.y, orbit.z);
        Vec3 motion = horizontal(client, direction, perTick);
        return new Vec3(motion.x, original.y, motion.z);
    }
}
