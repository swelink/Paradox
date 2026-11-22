package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.player.AutoMlgModule;
import cc.swelink.systems.modules.world.AutoDrainModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BucketItem.class)
public abstract class BucketAutoDrainMixin {
    @WrapOperation(method = "emptyContents", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean paradox$rememberSource(Level level, BlockPos pos, BlockState state, int flags,
                                         Operation<Boolean> original, @Local(argsOnly = true) LivingEntity actor) {
        boolean placed = original.call(level, pos, state, flags);
        if (placed) {
            paradox$rememberPlacement(actor, level, pos);
        }
        return placed;
    }

    @WrapOperation(method = "emptyContents", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/LiquidBlockContainer;placeLiquid(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)Z"))
    private boolean paradox$rememberWaterlogging(LiquidBlockContainer container, LevelAccessor level, BlockPos pos,
                                              BlockState state, FluidState fluid, Operation<Boolean> original,
                                              @Local(argsOnly = true) LivingEntity actor) {
        boolean placed = original.call(container, level, pos, state, fluid);
        if (placed) {
            paradox$rememberPlacement(actor, level, pos);
        }
        return placed;
    }

    @Unique
    private static void paradox$rememberPlacement(LivingEntity actor, LevelAccessor level, BlockPos pos) {
        var client = Minecraft.getInstance();
        if (client.player != null && actor == client.player && level == client.level) {
            Paradox.MODULES.get(AutoDrainModule.class).recordPlacedLiquid(client, pos);
            Paradox.MODULES.get(AutoMlgModule.class).recordPlacedWater(pos);
        }
    }
}
