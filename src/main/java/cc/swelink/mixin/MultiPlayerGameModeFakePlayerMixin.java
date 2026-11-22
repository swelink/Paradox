package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.FakePlayerModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeFakePlayerMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void paradox$attackFakePlayer(Player player, Entity target, CallbackInfo ci) {
        var module = Paradox.MODULES.get(FakePlayerModule.class);
        if (target == null || !module.isFake(target.getId())) return;
        module.hit(Minecraft.getInstance());
        player.resetAttackStrengthTicker();
        ci.cancel();
    }
}
