package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.combat.AntiBotModule;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerAntiBotMixin {
    @Inject(method = "handlePlayerInfoUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER))
    private void paradox$antiBotPlayerInfo(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        Paradox.MODULES.get(AntiBotModule.class).playerInfo(packet);
    }

    @Inject(method = "handleMoveEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER))
    private void paradox$antiBotMovement(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        Paradox.MODULES.get(AntiBotModule.class).movement(packet);
    }

    @Inject(method = {"handleSwingAnimation", "handleRemoveEntities", "handlePlayerInfoRemove"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER))
    private void paradox$antiBotEvidence(@Coerce Packet<?> packet, CallbackInfo ci) {
        Paradox.MODULES.get(AntiBotModule.class).packet(packet);
    }
}
