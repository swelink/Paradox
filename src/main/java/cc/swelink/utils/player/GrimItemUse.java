package cc.swelink.utils.player;

import cc.swelink.mixin.EntityEventPacketAccessor;
import cc.swelink.mixin.LivingEntityFlagsAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;

import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.function.Consumer;

public final class GrimItemUse {
    private record Task(int at, Consumer<GrimItemUse> action) {
    }

    private final ArrayDeque<int[]> unanswered = new ArrayDeque<>();
    private final LinkedList<Task> tasks = new LinkedList<>();
    private int arrived, answered;
    private boolean slowed, offhand, teleport, flagged, fastTick, uncertain;
    private int slowedTx = Integer.MIN_VALUE, slowedSlot = -1, slot = -1, notUsing;

    public synchronized boolean mayGoFast() {
        return !flagged;
    }

    public synchronized void wentFast() {
        fastTick = true;
    }

    public synchronized boolean grimSlowed() {
        return slowed;
    }

    public synchronized void reset() {
        unanswered.clear();
        tasks.clear();
        arrived = answered = 0;
        slowed = offhand = teleport = flagged = fastTick = uncertain = false;
        slowedTx = Integer.MIN_VALUE;
        slowedSlot = slot = -1;
        notUsing = 0;
    }

    public synchronized void arrived(Packet<?> packet) {
        if (packet instanceof BundlePacket<?> bundle) {
            for (Packet<?> child : bundle.subPackets()) arrived(child);
            return;
        }
        if (packet instanceof ClientboundPingPacket ping) {
            unanswered.add(new int[]{++arrived, ping.getId()});
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        int playerId;
        try {
            playerId = player.getId();
        } catch (IllegalStateException unassigned) {
            return;
        }
        if (packet instanceof ClientboundSetEntityDataPacket data) {
            if (data.id() != playerId || data.packedItems() == null) return;
            int flagsId = LivingEntityFlagsAccessor.paradox$livingFlags().id();
            for (var value : data.packedItems()) {
                if (value.id() != flagsId || !(value.value() instanceof Byte flags)) continue;
                int marked = arrived;
                boolean active = (flags & 1) != 0, off = (flags & 2) != 0;
                schedule(marked, tracker -> tracker.slowed = false);
                schedule(marked + 1, tracker -> {
                    if (tracker.slowedTx < marked) {
                        tracker.slowed = active;
                        if (active) {
                            tracker.offhand = off;
                            tracker.slowedSlot = tracker.slot;
                        }
                    }
                });
            }
        } else if (packet instanceof ClientboundEntityEventPacket event) {
            if (event.getEventId() != 9 || ((EntityEventPacketAccessor) event).paradox$entityId() != playerId) return;
            schedule(arrived, tracker -> tracker.slowed = false);
            schedule(arrived + 1, tracker -> tracker.slowed = false);
        }
    }

    public synchronized void sent(Packet<?> packet) {
        if (packet instanceof ServerboundPongPacket pong) answer(pong.getId());
        else if (packet instanceof ServerboundUseItemPacket use) used(use.hand());
        else if (packet instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            slowed = false;
            slowedTx = answered;
        } else if (packet instanceof ServerboundSetCarriedItemPacket select) slot = select.getSlot();
        else if (packet instanceof ServerboundAcceptTeleportationPacket) teleport = true;
        else if (packet instanceof ServerboundMovePlayerPacket move) moved(move);
    }

    public synchronized void tickPre() {
        if (fastTick) flagged = true;
        fastTick = false;
        var player = Minecraft.getInstance().player;
        if (slowed && player != null && !player.isUsingItem() && ++notUsing > 40) {
            slowed = false;
            flagged = true;
            uncertain = true;
        } else if (player != null && player.isUsingItem()) {
            notUsing = 0;
        }
    }

    private void schedule(int at, Consumer<GrimItemUse> action) {
        if (at > answered) {
            int index = tasks.size();
            while (index > 0 && tasks.get(index - 1).at() > at) index--;
            tasks.add(index, new Task(at, action));
            return;
        }
        boolean was = slowed;
        action.accept(this);
        if (was && !slowed) flagged = true;
    }

    private void answer(int id) {
        boolean found = false;
        for (int[] entry : unanswered) {
            if (entry[1] == id) {
                found = true;
                break;
            }
        }
        if (!found) return;
        while (!unanswered.isEmpty()) {
            int[] entry = unanswered.poll();
            answered = entry[0];
            if (entry[1] == id) break;
        }
        while (!tasks.isEmpty() && tasks.peek().at() <= answered) tasks.poll().action().accept(this);
    }

    private void used(InteractionHand hand) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (slot < 0) slot = player.getInventory().getSelectedSlot();
        var stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            if (!slowed || offhand == (hand == InteractionHand.OFF_HAND)) slowed = false;
            return;
        }
        if (player.isUsingItem() && player.getUsedItemHand() == hand) {
            slowed = true;
            uncertain = false;
            slowedTx = answered;
            offhand = hand == InteractionHand.OFF_HAND;
            slowedSlot = slot;
            notUsing = 0;
        }
    }

    private void moved(ServerboundMovePlayerPacket move) {
        if (slowed && !offhand && slowedSlot != slot && slot >= 0 && slowedSlot >= 0) slowed = false;
        boolean checked = move.hasPosition() && !teleport;
        if (move.hasPosition()) teleport = false;
        if (fastTick) flagged = true;
        else if (checked && slowed) flagged = false;
        else if (checked && !uncertain && tasks.isEmpty()) flagged = false;
        fastTick = false;
    }
}
