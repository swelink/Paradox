package cc.swelink.mixin;

import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundEntityEventPacket.class)
public interface EntityEventPacketAccessor {
    @Accessor("entityId")
    int paradox$entityId();
}
