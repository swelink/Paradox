package cc.swelink.mixin;

import com.mojang.datafixers.util.Either;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;
import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
public interface ProjectileUtilInvoker {
    @Invoker("getHitEntitiesAlong")
    static Either<BlockHitResult, Collection<EntityHitResult>> paradox$traceAttack(
            Entity entity, Vec3 eyes, Vec3 start, Predicate<Entity> predicate,
            Vec3 end, float margin, ClipContext.Block blocks) {
        throw new AssertionError("Mixin not applied");
    }
}
