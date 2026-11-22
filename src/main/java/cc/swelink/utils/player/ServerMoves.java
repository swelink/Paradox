package cc.swelink.utils.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Relative;

public final class ServerMoves {
    public static final ServerMoves INSTANCE = new ServerMoves();
    private boolean known, armed, awaiting;
    private int teleportId;
    private double lx, ly, lz, cx, cy, cz;
    private float lYaw, lPitch, cYaw, cPitch;

    private ServerMoves() {
    }

    public synchronized boolean armed() {
        return !known || armed;
    }

    private boolean fires(double x, double y, double z, float yaw, float pitch) {
        double dx = lx - x, dy = ly - y, dz = lz - z;
        return dx * dx + dy * dy + dz * dz > 0.00390625 || Math.abs(lYaw - yaw) + Math.abs(lPitch - pitch) > 10;
    }

    private boolean atEventStart() {
        return Double.doubleToLongBits(lx) == Double.doubleToLongBits(cx)
                && Double.doubleToLongBits(ly) == Double.doubleToLongBits(cy)
                && Double.doubleToLongBits(lz) == Double.doubleToLongBits(cz)
                && Float.floatToIntBits(lYaw) == Float.floatToIntBits(cYaw)
                && Float.floatToIntBits(lPitch) == Float.floatToIntBits(cPitch);
    }

    public synchronized void teleport(ClientboundPlayerPositionPacket packet, LocalPlayer player) {
        if (player == null || player.isPassenger()) {
            known = false;
            return;
        }
        var change = packet.change();
        var relatives = packet.relatives();
        double baseX = known ? cx : player.getX();
        double baseY = known ? cy : player.getY();
        double baseZ = known ? cz : player.getZ();
        double x = relatives.contains(Relative.X) ? baseX + change.position().x : change.position().x;
        double y = relatives.contains(Relative.Y) ? baseY + change.position().y : change.position().y;
        double z = relatives.contains(Relative.Z) ? baseZ + change.position().z : change.position().z;
        float baseYaw = known ? cYaw : Mth.wrapDegrees(player.getYRot());
        float basePitch = known ? cPitch : player.getXRot();
        float yaw = relatives.contains(Relative.Y_ROT) ? baseYaw + change.yRot() : change.yRot();
        float pitch = relatives.contains(Relative.X_ROT) ? basePitch + change.xRot() : change.xRot();
        lx = cx = x;
        ly = cy = y;
        lz = cz = z;
        lYaw = cYaw = yaw;
        lPitch = cPitch = Mth.clamp(pitch, -90, 90) % 360;
        armed = awaiting = known = true;
        teleportId = packet.id();
    }

    public synchronized void arrived(Packet<?> packet) {
        if (packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket) {
            known = armed = awaiting = false;
        }
    }

    public synchronized void sent(Packet<?> packet) {
        if (packet instanceof ServerboundAcceptTeleportationPacket confirm) {
            if (awaiting && confirm.id() == teleportId) awaiting = false;
        } else if (packet instanceof ServerboundMovePlayerPacket move) {
            moved(move);
        }
    }

    private void moved(ServerboundMovePlayerPacket move) {
        if (!known) return;
        var player = Minecraft.getInstance().player;
        if (player != null && player.isPassenger()) {
            known = false;
            return;
        }
        if (awaiting) {
            if (move.hasRotation()) {
                cYaw = Mth.wrapDegrees(move.getYRot(cYaw));
                cPitch = Mth.clamp(Mth.wrapDegrees(move.getXRot(cPitch)), -90, 90) % 360;
            }
            return;
        }
        double x = move.getX(cx), y = move.getY(cy), z = move.getZ(cz);
        float yaw = move.hasRotation() ? move.getYRot(cYaw) : cYaw;
        float pitch = move.hasRotation() ? move.getXRot(cPitch) : cPitch;
        if (fires(x, y, z, yaw, pitch)) {
            boolean fromHere = atEventStart();
            lx = x;
            ly = y;
            lz = z;
            lYaw = yaw;
            lPitch = pitch;
            if (armed && !fromHere) {
                armed = false;
                return;
            }
        }
        cx = x;
        cy = y;
        cz = z;
        cYaw = Mth.wrapDegrees(yaw);
        cPitch = Mth.clamp(Mth.wrapDegrees(pitch), -90, 90) % 360;
    }
}
