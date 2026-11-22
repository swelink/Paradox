package cc.swelink.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Connection.class)
public interface ConnectionLagAccess {
    @Invoker("sendPacket")
    void paradox$sendQueuedPacket(Packet<?> packet, ChannelFutureListener listener, boolean flush);

    @Accessor("channel")
    Channel paradox$channel();

    @Invoker("genericsFtw")
    static void paradox$handlePacket(Packet<?> packet, PacketListener listener) {
        throw new AssertionError();
    }
}
