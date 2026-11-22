package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.events.InteractionEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeSpinbotMixin {
    @Inject(method = {"attack", "piercingAttack", "releaseUsingItem", "dropItem"}, at = @At("HEAD"))
    private void paradox$interactionRotation(CallbackInfo ci) {
        Paradox.EVENTS.post(new InteractionEvent(Minecraft.getInstance()));
    }

    @Inject(method = {"interact", "startDestroyBlock", "continueDestroyBlock"},
            at = @At("HEAD"))
    private void paradox$useRotation(CallbackInfoReturnable<?> ci) {
        Paradox.EVENTS.post(new InteractionEvent(Minecraft.getInstance()));
    }

    @Inject(method = "useItem", at = @At("HEAD"))
    private void paradox$itemUseRotation(Player player, InteractionHand hand, CallbackInfoReturnable<?> ci) {
        if (player.isFallFlying() && player.getItemInHand(hand).is(Items.FIREWORK_ROCKET)) {
            Paradox.ROTATIONS.releaseLocalRotation();
            return;
        }
        Paradox.EVENTS.post(new InteractionEvent(Minecraft.getInstance()));
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void paradox$blockUseRotation(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                       CallbackInfoReturnable<?> ci) {
        if (player.isFallFlying() && player.getItemInHand(hand).is(Items.FIREWORK_ROCKET)) {
            Paradox.ROTATIONS.releaseLocalRotation();
            return;
        }
        Paradox.EVENTS.post(new InteractionEvent(Minecraft.getInstance()));
    }
}
