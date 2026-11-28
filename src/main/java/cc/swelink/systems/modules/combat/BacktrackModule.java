package cc.swelink.systems.modules.combat;

import cc.swelink.Paradox;
import cc.swelink.events.AttackEvent;
import cc.swelink.events.EventHandler;
import cc.swelink.events.IncomingPacketEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.MillisecondsSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.combat.Targets;
import cc.swelink.utils.network.CombatLagPackets;
import cc.swelink.utils.network.CombatLagPolicy;
import cc.swelink.utils.network.IncomingPacketDelay;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.time.GameTime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

public final class BacktrackModule extends Module {
    private final ModeSetting targetMode = addSetting(new ModeSetting("Target", "Attack", "Attack", "Range", "Adaptive"));
    private final MillisecondsSetting minDelay = addSetting(new MillisecondsSetting("Min delay", 100, 0, 5000));
    private final MillisecondsSetting maxDelay = addSetting(new MillisecondsSetting("Max delay", 150, 0, 5000));
    private final MillisecondsSetting afterHit = addSetting(new MillisecondsSetting("After hit", 1000, 100, 10000));
    private final IntSetting minRange = addSetting(new IntSetting("Min range", 1, 0, 8));
    private final IntSetting maxRange = addSetting(new IntSetting("Max range", 4, 1, 8));
    private final BooleanSetting flushOnHurt = addSetting(new BooleanSetting("Flush on hurt", true));
    private final BooleanSetting showBox = addSetting(new BooleanSetting("Show box", true));
    private final IncomingPacketDelay packets = new IncomingPacketDelay(this::reset, () -> { });
    private final VecDeltaCodec serverPosition = new VecDeltaCodec();
    private Player target;
    private long lastAttack;
    private long delay;
    private long resumeAt;
    private long holdStart;

    public BacktrackModule() {
        super("Backtrack", "Delays your target's movement", Category.COMBAT);
        afterHit.visibleWhen(() -> targetMode.get().equals("Attack"));
    }

    public void attacked(Minecraft client, int entityId) {
        if (!isEnabled() || !packets.synchronize(client)) return;
        if (client.level.getEntity(entityId) instanceof Player player && valid(client, player)) {
            select(player);
            lastAttack = GameTime.nanos();
        }
    }

    public AABB serverBox(Minecraft client) {
        if (!isEnabled() || !showBox.get() || packets.isEmpty() || !packets.matches(client)
                || !valid(client, target)) return null;
        return target.getBoundingBox().move(serverPosition.getBase().subtract(target.position()));
    }

    public void beforeTick(Minecraft client) {
        if (!packets.synchronize(client)) return;
        if (isEnabled() && !targetMode.get().equals("Attack")) {
            Player selected = Paradox.MODULES.get(KillauraModule.class).target(client);
            if (!valid(client, selected)) {
                selected = client.level.players().stream().filter(player -> valid(client, player))
                        .filter(player -> player.getBoundingBox().distanceToSqr(client.player.getEyePosition())
                                <= (double) maxRange.get() * maxRange.get())
                        .min(Comparator.comparingDouble(player -> player.distanceToSqr(client.player))).orElse(null);
            }
            if (selected != target) select(selected);
        }
        if (!canDelay(client)) {
            flush();
            if (!valid(client, target)) target = null;
            return;
        }
        packets.release(delay);
    }

    public boolean incoming(ClientPacketListener source, Packet<?> packet) {
        var client = Minecraft.getInstance();
        if (!packets.accepts(client, source)) return false;
        if (CombatLagPackets.changesWorld(packet)) {
            flush();
            target = null;
            return false;
        }
        if (packet instanceof ClientboundDisconnectPacket) {
            packets.disconnect();
            return false;
        }
        if (packet instanceof ClientboundSystemChatPacket || packet instanceof ClientboundDisguisedChatPacket) return false;
        if (CombatLagPackets.correctsPlayer(packet) || flushOnHurt.get() && CombatLagPackets.hurtsPlayer(client, packet)) {
            flush();
            target = null;
            resumeAt = GameTime.nanos() + 150_000_000L;
            return false;
        }
        if (target == null) return false;
        if (packet instanceof ClientboundRemoveEntitiesPacket removed && removed.entityIds().contains(target.getId())) {
            flush();
            target = null;
            return false;
        }
        if (belongsToTarget(packet)) track(packet);
        if (packets.isEmpty()) {
            chooseDelay();
            holdStart = GameTime.nanos();
        }
        if (!canDelay(client) || delay == 0) {
            flush();
            return false;
        }
        packets.release(delay);
        if (!packets.offer(packet)) {
            flush();
            resumeAt = GameTime.nanos() + 150_000_000L;
            return false;
        }
        return true;
    }

    private boolean belongsToTarget(Packet<?> packet) {
        return packet instanceof ClientboundMoveEntityPacket move && move.getEntity(packets.level()) == target
                || packet instanceof ClientboundRotateHeadPacket rotation && rotation.getEntity(packets.level()) == target
                || packet instanceof ClientboundEntityPositionSyncPacket position && position.id() == target.getId()
                || packet instanceof ClientboundTeleportEntityPacket teleport && teleport.id() == target.getId()
                || packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == target.getId();
    }

    private void track(Packet<?> packet) {
        if (packet instanceof ClientboundMoveEntityPacket move && move.hasPosition()) {
            serverPosition.setBase(move.getPositionDelta().decode(serverPosition).endPosition());
        } else if (packet instanceof ClientboundEntityPositionSyncPacket position) {
            serverPosition.setBase(position.position().endPosition());
        } else if (packet instanceof ClientboundTeleportEntityPacket teleport) {
            var current = new PositionMoveRotation(serverPosition.getBase(), target.getDeltaMovement(), target.getYRot(), target.getXRot());
            serverPosition.setBase(PositionMoveRotation.calculateAbsolute(current, teleport.change(), teleport.relatives()).position());
        }
    }

    private boolean canDelay(Minecraft client) {
        if (!isEnabled() || !valid(client, target) || client.player.tickCount < 20 || client.isPaused()
                || !Gameplay.focused(client) || client.player.isUsingItem()
                || GameTime.nanos() - resumeAt < 0
                || targetMode.get().equals("Attack") && GameTime.nanos() - lastAttack > afterHit.get() * 1_000_000L) return false;
        var eyes = client.player.getEyePosition();
        var displayed = target.getBoundingBox();
        var actual = displayed.move(serverPosition.getBase().subtract(target.position()));
        double displayedDistance = Math.sqrt(displayed.distanceToSqr(eyes));
        double serverDistance = Math.sqrt(actual.distanceToSqr(eyes));
        double minimum = Math.min(minRange.get(), maxRange.get());
        double maximum = Math.max(minRange.get(), maxRange.get());
        if (targetMode.get().equals("Adaptive")) {
            return CombatLagPolicy.adaptiveBacktrack(displayedDistance, serverDistance,
                    client.player.entityInteractionRange(), minimum, maximum);
        }
        return CombatLagPolicy.backtrack(displayedDistance, serverDistance, minimum, maximum);
    }

    private boolean valid(Minecraft client, Player player) {
        return client.player != null && client.player.isAlive() && !client.player.isSpectator()
                && Targets.attackable(client, player);
    }

    private void select(Player selected) {
        if (selected == target) return;
        flush();
        target = selected;
        if (target != null) serverPosition.setBase(target.getPositionCodec().getBase());
        chooseDelay();
    }

    private void chooseDelay() {
        int minimum = Math.min(minDelay.get(), maxDelay.get());
        int maximum = Math.max(minDelay.get(), maxDelay.get());
        delay = ThreadLocalRandom.current().nextInt(minimum, maximum + 1) * 1_000_000L;
    }

    private void reset() {
        target = null;
        lastAttack = 0;
    }

    @Override
    public long delayRemaining() {
        if (!isEnabled() || packets.isEmpty()) return -1;
        long remaining = targetMode.get().equals("Attack")
                ? afterHit.get() * 1_000_000L - (GameTime.nanos() - lastAttack)
                : delay - (GameTime.nanos() - holdStart);
        return Math.clamp((remaining + 999_999) / 1_000_000, 0, delayLimit());
    }

    @Override
    public long delayLimit() {
        int delayLimit = Math.max(minDelay.get(), maxDelay.get());
        return targetMode.get().equals("Attack") ? Math.max(delayLimit, afterHit.get()) : delayLimit;
    }

    public void flushAll() {
        flush();
    }

    private void flush() {
        packets.flush();
    }

    @Override
    protected void onDisable() {
        flush();
        target = null;
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 20)
    private void onPreTick(PreTickEvent event) {
        beforeTick(event.client());
    }

    @EventHandler(priority = 20)
    private void onAttack(AttackEvent event) {
        attacked(event.client(), event.entityId());
    }

    @EventHandler(priority = 30)
    private void onIncomingPacket(IncomingPacketEvent event) {
        if (incoming(event.source(), event.packet())) event.cancel();
    }
}
