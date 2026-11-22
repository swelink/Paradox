package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.movement.ElytraBoostModule;

import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerElytraBoostMixin {
    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void paradox$afterMove(CallbackInfo ci) {
        Paradox.MODULES.get(ElytraBoostModule.class).afterMove((LocalPlayer) (Object) this);
    }
}
