package cc.swelink.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityFlagsAccessor {
    @Accessor("DATA_LIVING_ENTITY_FLAGS")
    static EntityDataAccessor<Byte> paradox$livingFlags() {
        throw new AssertionError();
    }

    @Accessor("lerpYHeadRot")
    double paradox$lerpYHeadRot();

    @Accessor("lerpHeadSteps")
    int paradox$lerpHeadSteps();
}
