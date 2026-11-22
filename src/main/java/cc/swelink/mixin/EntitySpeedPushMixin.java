package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.movement.speed.SpeedModule;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntitySpeedPushMixin {
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void paradox$noPush(Entity entity, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && ((Object) this == player || entity == player)
                && Paradox.MODULES.get(SpeedModule.class).noPush()) ci.cancel();
    }
}
