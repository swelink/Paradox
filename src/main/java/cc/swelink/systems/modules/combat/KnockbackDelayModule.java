package cc.swelink.systems.modules.combat;

import cc.swelink.utils.time.GameTime;
import cc.swelink.events.AttackEvent;
import cc.swelink.events.EventHandler;
import cc.swelink.events.IncomingPacketEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.Paradox;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.MillisecondsSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.network.CombatLagPackets;
import cc.swelink.utils.network.IncomingPacketDelay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class KnockbackDelayModule extends Module {
    private static final long PLAYER_HIT_WINDOW = 150_000_000L;
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Always", "Always", "Range", "Attack"));
    private final IntSetting range = addSetting(new IntSetting("Range", 6, 1, 6));
    private final MillisecondsSetting minDelay = addSetting(new MillisecondsSetting("Min delay", 250, 0, 10000));
    private final MillisecondsSetting maxDelay = addSetting(new MillisecondsSetting("Max delay", 400, 0, 10000));
    private final MillisecondsSetting releaseTime = addSetting(new MillisecondsSetting("Release time", 300, 0, 2000));
    private final MillisecondsSetting cooldown = addSetting(new MillisecondsSetting("Cooldown", 500, 0, 5000));
    private final BooleanSetting showBox = addSetting(new BooleanSetting("Show box", true));
    private final Map<Integer, VecDeltaCodec> serverPositions = new HashMap<>();
    private final IncomingPacketDelay packets = new IncomingPacketDelay(this::reset,
            () -> resumeAt = GameTime.nanos() + cooldown.get() * 1_000_000L);
    private long attack;
    private long delay;
    private long resumeAt;
    private long releaseStart;

    public KnockbackDelayModule() {
        super("KnockbackDelay", "Delays knockback", Category.COMBAT);
        range.visibleWhen(() -> mode.get().equals("Range"));
    }

    public void beforeTick(Minecraft client) {
        if (!packets.synchronize(client)) return;
        if (packets.isEmpty()) {
            releaseStart = 0;
            return;
        }
        long now = GameTime.nanos();
        if (!isEnabled() || client.player.isDeadOrDying()) {
            flush();
            return;
        }
        if (releaseStart == 0 && packets.age(now) < delay) return;
        long spread = releaseTime.get() * 1_000_000L;
        if (releaseStart == 0) releaseStart = now;
        long elapsed = now - releaseStart;
        if (elapsed >= spread) {
            flush();
            return;
        }
        packets.release(delay - delay * elapsed / spread);
        if (packets.isEmpty()) {
            releaseStart = 0;
            resumeAt = GameTime.nanos() + cooldown.get() * 1_000_000L;
        }
    }

    public boolean incoming(ClientPacketListener source, Packet<?> packet) {
        var client = Minecraft.getInstance();
        if (!packets.accepts(client, source)) return false;
        if (packet instanceof ClientboundDisconnectPacket) {
            packets.disconnect();
            return false;
        }
        if (CombatLagPackets.changesWorld(packet) || CombatLagPackets.correctsPlayer(packet)) {
            flush();
            return false;
        }
        if (packet instanceof ClientboundSystemChatPacket || packet instanceof ClientboundDisguisedChatPacket) return false;
        if (packets.isEmpty()) {
            if (!isEnabled() || !triggered(client) || !canStart(client)) return false;
            Paradox.MODULES.get(BacktrackModule.class).flushAll();
            int minimum = Math.min(minDelay.get(), maxDelay.get());
            int maximum = Math.max(minDelay.get(), maxDelay.get());
            attack = 0;
            delay = ThreadLocalRandom.current().nextInt(minimum, maximum + 1) * 1_000_000L;
            if (delay == 0) return false;
        }
        if (!packets.offer(packet)) {
            flush();
            return false;
        }
        track(packet);
        return true;
    }

    public Map<Integer, AABB> serverBoxes(Minecraft client) {
        var boxes = new HashMap<Integer, AABB>();
        if (!isEnabled() || !showBox.get() || packets.isEmpty() || !packets.matches(client)) return boxes;
        for (var entry : serverPositions.entrySet()) {
            if (client.level.getEntity(entry.getKey()) instanceof Player player && player != client.player
                    && player.isAlive() && !player.isRemoved()) {
                boxes.put(player.getId(), player.getBoundingBox().move(entry.getValue().getBase().subtract(player.position())));
            }
        }
        return boxes;
    }

    public Vec3 serverPosition(int entityId) {
        if (packets.isEmpty()) return null;
        var codec = serverPositions.get(entityId);
        return codec == null ? null : codec.getBase();
    }

    private void track(Packet<?> packet) {
        if (packet instanceof ClientboundMoveEntityPacket move && move.hasPosition()) {
            var codec = codec(move.getEntity(packets.level()));
            if (codec != null) codec.setBase(move.getPositionDelta().decode(codec).endPosition());
        } else if (packet instanceof ClientboundEntityPositionSyncPacket position) {
            var codec = codec(packets.level().getEntity(position.id()));
            if (codec != null) codec.setBase(position.position().endPosition());
        } else if (packet instanceof ClientboundTeleportEntityPacket teleport) {
            var entity = packets.level().getEntity(teleport.id());
            var codec = codec(entity);
            if (codec != null) {
                var current = new PositionMoveRotation(codec.getBase(), entity.getDeltaMovement(), entity.getYRot(), entity.getXRot());
                codec.setBase(PositionMoveRotation.calculateAbsolute(current, teleport.change(), teleport.relatives()).position());
            }
        }
    }

    private VecDeltaCodec codec(Entity entity) {
        if (!(entity instanceof Player player) || player == Minecraft.getInstance().player) return null;
        return serverPositions.computeIfAbsent(player.getId(), ignored -> {
            var codec = new VecDeltaCodec();
            codec.setBase(player.getPositionCodec().getBase());
            return codec;
        });
    }

    private boolean triggered(Minecraft client) {
        return switch (mode.get()) {
            case "Range" -> playerInRange(client);
            case "Attack" -> attack != 0 && GameTime.nanos() - attack <= PLAYER_HIT_WINDOW;
            default -> true;
        };
    }

    private boolean playerInRange(Minecraft client) {
        var player = client.player;
        double limit = (double) range.get() * range.get();
        for (var other : client.level.players()) {
            if (other != player && other.isAlive() && !other.isSpectator() && other.distanceToSqr(player) <= limit
                    && !Paradox.MODULES.get(AntiBotModule.class).isBot(other)) return true;
        }
        return false;
    }

    private void attacked(Minecraft client, int entityId) {
        if (client.level != null && client.level.getEntity(entityId) instanceof Player target && target != client.player
                && !Paradox.MODULES.get(AntiBotModule.class).isBot(target)) {
            attack = GameTime.nanos();
        }
    }

    private boolean canStart(Minecraft client) {
        var player = client.player;
        return player.tickCount >= 20 && player.isAlive() && !player.isSpectator() && !client.isPaused()
                && GameTime.nanos() - resumeAt >= 0;
    }

    @Override
    public long delayRemaining() {
        if (!isEnabled() || packets.isEmpty()) return -1;
        long now = GameTime.nanos();
        long release = releaseTime.get() * 1_000_000L;
        long remaining = releaseStart != 0 ? release - (now - releaseStart) : Math.max(0, delay - packets.age(now)) + release;
        return Math.clamp((remaining + 999_999) / 1_000_000, 0, delayLimit());
    }

    @Override
    public long delayLimit() {
        return Math.max(minDelay.get(), maxDelay.get()) + releaseTime.get();
    }

    public boolean holding() {
        return !packets.isEmpty();
    }

    private void reset() {
        serverPositions.clear();
        attack = 0;
    }

    private void flush() {
        releaseStart = 0;
        serverPositions.clear();
        packets.flush();
    }

    @Override
    protected void onDisable() {
        flush();
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 40)
    private void onPreTick(PreTickEvent event) {
        beforeTick(event.client());
    }

    @EventHandler(priority = 60)
    private void onAttack(AttackEvent event) {
        attacked(event.client(), event.entityId());
    }

    @EventHandler(priority = 20)
    private void onIncomingPacket(IncomingPacketEvent event) {
        if (incoming(event.source(), event.packet())) event.cancel();
    }
}
