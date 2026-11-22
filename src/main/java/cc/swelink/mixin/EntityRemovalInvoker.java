package cc.swelink.mixin;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityRemovalInvoker {
    @Invoker("unsetRemoved")
    void paradox$unsetRemoved();
}
