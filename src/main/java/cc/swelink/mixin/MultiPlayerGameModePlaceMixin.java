package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.PlaceModule;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModePlaceMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void paradox$beforePlace(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        Paradox.MODULES.get(PlaceModule.class).beforeUse(player, hand, hit);
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void paradox$afterPlace(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                    CallbackInfoReturnable<InteractionResult> cir) {
        Paradox.MODULES.get(PlaceModule.class).afterUse(player, cir.getReturnValue());
    }
}
