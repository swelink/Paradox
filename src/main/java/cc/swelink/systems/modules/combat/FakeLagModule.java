package cc.swelink.systems.modules.combat;

import cc.swelink.events.AttackEvent;
import cc.swelink.events.EventHandler;
import cc.swelink.events.IncomingPacketEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.mixin.ConnectionLagAccess;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.MillisecondsSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.Targets;
import cc.swelink.utils.network.CombatLagPackets;
import cc.swelink.utils.network.CombatLagPolicy;
import cc.swelink.utils.network.TimedPacketQueue;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.time.GameTime;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSpectatorActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.nio.channels.ClosedChannelException;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

public final class FakeLagModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Dynamic", "Dynamic", "Constant"));
    private final MillisecondsSetting minDelay = addSetting(new MillisecondsSetting("Min delay", 200, 0, 1000));
    private final MillisecondsSetting maxDelay = addSetting(new MillisecondsSetting("Max delay", 350, 0, 1000));
    private final MillisecondsSetting recoil = addSetting(new MillisecondsSetting("Cooldown", 150, 0, 1000));
    private final IntSetting range = addSetting(new IntSetting("Range", 6, 1, 10));
    private final BooleanSetting showBox = addSetting(new BooleanSetting("Show box", true));
    private final BooleanSetting flushOnSwing = addSetting(new BooleanSetting("Flush on swing", false));
    private final TimedPacketQueue<Outgoing> packets = new TimedPacketQueue<>(2048);
    private ClientPacketListener listener;
    private ClientLevel level;
    private Vec3 serverPosition;
    private Vec3 previousServerPosition;
    private long resumeAt;
    private long pulseDelay;
    private boolean releasing;

    public FakeLagModule() {
        super("FakeLag", "Lags you in fights", Category.COMBAT);
        range.visibleWhen(() -> mode.get().equals("Dynamic"));
    }

    public void beforeTick(Minecraft client) {
        if (!synchronize(client)) return;
        previousServerPosition = serverPosition;
        long now = GameTime.nanos();
        if (!packets.isEmpty() && (!canQueue(client, now) || packets.age(now) >= pulseDelay)) flush(true);
    }

    public AABB serverBox(Minecraft client) {
        if (!isEnabled() || !showBox.get() || serverPosition == null
                || client.player == null || !client.player.isAlive() || client.level != level
                || client.getConnection() != listener || client.options.getCameraType().isFirstPerson()) return null;
        Vec3 previous = previousServerPosition == null ? serverPosition : previousServerPosition;
        Vec3 shown = previous.lerp(serverPosition, client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        return client.player.getBoundingBox().move(shown.subtract(client.player.position()));
    }

    public boolean outgoing(Connection connection, Packet<?> packet, ChannelFutureListener callback, boolean flush) {
        var client = Minecraft.getInstance();
        if (releasing || !client.isSameThread() || client.getConnection() == null
                || client.getConnection().getConnection() != connection || !synchronize(client)) return false;
        if (packet instanceof ServerboundKeepAlivePacket || packet instanceof ServerboundChatPacket
                || packet instanceof ServerboundChatCommandPacket) return false;
        if (packet.isTerminal() || packet instanceof ServerboundResourcePackPacket
                || packet instanceof ServerboundAttackPacket || packet instanceof ServerboundInteractPacket
                || packet instanceof ServerboundPunchPacket && flushOnSwing.get() || packet instanceof ServerboundSpectatorActionPacket
                || packet instanceof ServerboundPlayerActionPacket || packet instanceof ServerboundUseItemOnPacket
                || packet instanceof ServerboundUseItemPacket || packet instanceof ServerboundSignUpdatePacket) {
            flush(true);
            return false;
        }
        long now = GameTime.nanos();
        Vec3 position = packet instanceof ServerboundMovePlayerPacket move && move.hasPosition()
                ? new Vec3(move.getX(client.player.getX()), move.getY(client.player.getY()), move.getZ(client.player.getZ())) : null;
        if (!canQueue(client, now)) {
            flush(!packets.isEmpty());
            if (position != null) serverPosition = position;
            return false;
        }
        if (packets.isEmpty()) pulseDelay = nextPulse();
        if (!packets.isEmpty() && packets.age(now) >= pulseDelay && mode.get().equals("Constant")
                && recoil.get() == 0) {
            flush(true);
            pulseDelay = nextPulse();
        }
        if (pulseDelay == 0 || !packets.isEmpty() && packets.age(now) >= pulseDelay
                || !packets.offer(new Outgoing(packet, callback, flush, position), now)) {
            flush(true);
            if (position != null) serverPosition = position;
            return false;
        }
        return true;
    }

    private long nextPulse() {
        int minimum = Math.min(minDelay.get(), maxDelay.get());
        int maximum = Math.max(minDelay.get(), maxDelay.get());
        return ThreadLocalRandom.current().nextInt(minimum, maximum + 1) * 1_000_000L;
    }

    public void beforeAction() {
        if (isEnabled() || !packets.isEmpty()) flush(true);
    }

    public boolean releasingNow() {
        return releasing;
    }

    public boolean isQueued(Packet<?> packet) {
        return packets.any(outgoing -> outgoing.packet == packet);
    }

    public void incoming(ClientPacketListener source, Packet<?> packet) {
        var client = Minecraft.getInstance();
        if (source != client.getConnection()) return;
        if (CombatLagPackets.changesWorld(packet)) {
            flush(false);
            serverPosition = null;
            resumeAt = GameTime.nanos() + 500_000_000L;
        } else if (CombatLagPackets.correctsPlayer(packet) || CombatLagPackets.hurtsPlayer(client, packet)) {
            flush(true);
            serverPosition = null;
        }
    }

    private boolean synchronize(Minecraft client) {
        if (listener != client.getConnection() || level != client.level) {
            if (listener != null && listener == client.getConnection()) flush(false);
            else discard();
            listener = client.getConnection();
            level = client.level;
            serverPosition = client.player == null ? null : client.player.position();
            resumeAt = GameTime.nanos() + 500_000_000L;
        }
        if (listener == null || !listener.getConnection().isConnected()) {
            discard();
            return false;
        }
        if (client.player == null || level == null) {
            flush(false);
            return false;
        }
        if (serverPosition == null) serverPosition = client.player.position();
        return true;
    }

    private boolean canQueue(Minecraft client, long now) {
        var player = client.player;
        if (!isEnabled() || now - resumeAt < 0 || player == null || player.tickCount < 20 || !player.isAlive()
                || player.isSpectator() || player.isPassenger() || player.isUsingItem() || player.isInWater()
                || client.isPaused() || !Gameplay.focused(client)) return false;
        if (mode.get().equals("Constant")) return true;
        Player enemy = client.level.players().stream().filter(other -> Targets.attackable(client, other)
                        && other.distanceToSqr(player) <= (double) range.get() * range.get())
                .min(Comparator.comparingDouble(other -> other.distanceToSqr(player))).orElse(null);
        if (enemy == null) return false;
        if (packets.isEmpty()) return true;
        var currentBox = player.getBoundingBox();
        var serverBox = currentBox.move(serverPosition.subtract(player.position()));
        var enemyBox = enemy.getBoundingBox();
        return CombatLagPolicy.fakeLag(Math.sqrt(enemyBox.distanceToSqr(currentBox)),
                Math.sqrt(enemyBox.distanceToSqr(serverBox)), enemyBox.intersects(serverBox));
    }

    private void flush(boolean cooldown) {
        var client = Minecraft.getInstance();
        if (listener == null || client.getConnection() != listener || !listener.getConnection().isConnected()) {
            discard();
            return;
        }
        boolean queued = !packets.isEmpty();
        releasing = true;
        try {
            var connection = listener.getConnection();
            packets.drain(outgoing -> {
                ((ConnectionLagAccess) connection).paradox$sendQueuedPacket(outgoing.packet, outgoing.callback, outgoing.flush);
                if (outgoing.position != null) serverPosition = outgoing.position;
            });
            if (queued) connection.flushChannel();
        } finally {
            releasing = false;
        }
        if (cooldown && isEnabled()) resumeAt = GameTime.nanos() + recoil.get() * 1_000_000L;
    }

    private void discard() {
        var channel = listener == null ? null : ((ConnectionLagAccess) listener.getConnection()).paradox$channel();
        packets.drain(outgoing -> {
            if (outgoing.callback != null && channel != null) {
                channel.newFailedFuture(new ClosedChannelException()).addListener(outgoing.callback);
            }
        });
        serverPosition = null;
    }

    @Override
    protected void onDisable() {
        flush(false);
    }

    private record Outgoing(Packet<?> packet, ChannelFutureListener callback, boolean flush, Vec3 position) {
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 30)
    private void onPreTick(PreTickEvent event) {
        beforeTick(event.client());
    }

    @EventHandler(priority = 10)
    private void onAttack(AttackEvent event) {
        beforeAction();
    }

    @EventHandler(priority = 10)
    private void onIncomingPacket(IncomingPacketEvent event) {
        incoming(event.source(), event.packet());
    }
}
