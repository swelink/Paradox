package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.combat.velocity.VelocityModule;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerVelocityMixin {
    @Inject(method = "handleSetEntityMotion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundSetEntityMotionPacket;movement()Lnet/minecraft/world/phys/Vec3;"),
            cancellable = true)
    private void paradox$cancelMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && packet.id() == player.getId() && Paradox.MODULES.get(VelocityModule.class).cancelMotion()) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "handleSetEntityMotion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundSetEntityMotionPacket;movement()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 paradox$motion(Vec3 movement, ClientboundSetEntityMotionPacket packet) {
        var player = Minecraft.getInstance().player;
        return player == null || packet.id() != player.getId() ? movement : Paradox.MODULES.get(VelocityModule.class).motion(player, movement);
    }

    @ModifyExpressionValue(method = "handleExplosion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundExplodePacket;playerKnockback()Ljava/util/Optional;"))
    private Optional<Vec3> paradox$explosion(Optional<Vec3> movement, ClientboundExplodePacket packet) {
        return movement.map(value -> Paradox.MODULES.get(VelocityModule.class).explosion(packet, value));
    }

}
