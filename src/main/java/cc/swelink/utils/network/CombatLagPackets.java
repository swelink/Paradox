package cc.swelink.utils.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;

public final class CombatLagPackets {
    private CombatLagPackets() {
    }

    public static boolean changesWorld(Packet<?> packet) {
        return packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket
                || packet instanceof ClientboundStartConfigurationPacket;
    }

    public static boolean correctsPlayer(Packet<?> packet) {
        return packet instanceof ClientboundPlayerPositionPacket || packet instanceof ClientboundPlayerRotationPacket;
    }

    public static boolean hurtsPlayer(Minecraft client, Packet<?> packet) {
        var player = client.player;
        if (player == null) return false;
        return packet instanceof ClientboundHurtAnimationPacket hurt && hurt.id() == player.getId()
                || packet instanceof ClientboundSetHealthPacket health && health.getHealth() < player.getHealth()
                || packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == player.getId()
                && motion.movement().lengthSqr() > 0
                || packet instanceof ClientboundExplodePacket explosion
                && explosion.playerKnockback().filter(knockback -> knockback.lengthSqr() > 0).isPresent();
    }
}
