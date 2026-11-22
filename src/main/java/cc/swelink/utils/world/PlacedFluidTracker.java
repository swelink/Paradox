package cc.swelink.utils.world;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import java.util.HashMap;
import java.util.Map;

public final class PlacedFluidTracker {
    private final Map<BlockPos, Fluid> sources = new HashMap<>();
    private Level level;

    public void update(Level currentLevel) {
        if (level != currentLevel) {
            sources.clear();
            level = currentLevel;
        }
        if (level != null) {
            sources.entrySet().removeIf(entry -> level.hasChunkAt(entry.getKey())
                    && !matches(entry.getKey(), entry.getValue()));
        }
    }

    public void record(Level currentLevel, BlockPos position) {
        update(currentLevel);
        if (level == null) {
            return;
        }
        var fluid = level.getFluidState(position);
        if (fluid.isSource() && (fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA))) {
            sources.put(position.immutable(), fluid.getType());
        }
    }

    public boolean contains(BlockPos position) {
        var fluid = sources.get(position);
        return fluid != null && matches(position, fluid);
    }

    private boolean matches(BlockPos position, Fluid fluid) {
        var current = level.getFluidState(position);
        return current.isSource() && current.getType().isSame(fluid);
    }
}
