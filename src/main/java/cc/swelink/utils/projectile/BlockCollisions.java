package cc.swelink.utils.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class BlockCollisions implements ProjectileTrajectory.Environment {
    protected final Level level;
    protected final Entity source;

    public BlockCollisions(Level level, Entity source) {
        this.level = level;
        this.source = source;
    }

    @Override
    public boolean loaded(Vec3 point) {
        return point.y >= level.getMinY() - 16 && level.hasChunkAt(BlockPos.containing(point));
    }

    @Override
    public boolean water(Vec3 point) {
        var block = BlockPos.containing(point);
        var fluid = level.getFluidState(block);
        return fluid.is(FluidTags.WATER) && point.y - block.getY() < fluid.getHeight(level, block);
    }

    @Override
    public ProjectileTrajectory.Impact collide(Vec3 from, Vec3 to, int tick) {
        var block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
        return block.getType() == HitResult.Type.MISS ? null
                : new ProjectileTrajectory.Impact(block.getLocation(), Vec3.atLowerCornerOf(block.getDirection().getUnitVec3i()));
    }
}
