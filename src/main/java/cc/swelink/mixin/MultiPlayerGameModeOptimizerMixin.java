package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.player.OptimizerModule;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeOptimizerMixin {
    @Unique
    private BlockState paradox$clickedState;

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void paradox$rememberClicked(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        paradox$clickedState = player.level().getBlockState(hit.getBlockPos());
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void paradox$predictAnchor(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                       CallbackInfoReturnable<InteractionResult> cir) {
        BlockState clicked = paradox$clickedState;
        paradox$clickedState = null;
        if (clicked != null && cir.getReturnValue().consumesAction()) {
            Paradox.MODULES.get(OptimizerModule.class).anchorUsed(player, hand, hit.getBlockPos(), clicked);
        }
    }
}
