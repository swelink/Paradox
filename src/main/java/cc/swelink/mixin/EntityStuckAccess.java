package cc.swelink.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityStuckAccess {
    @Accessor("stuckSpeedMultiplier")
    Vec3 paradox$stuckSpeedMultiplier();

    @Accessor("stuckSpeedMultiplier")
    void paradox$setStuckSpeedMultiplier(Vec3 multiplier);
}
