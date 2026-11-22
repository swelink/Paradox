package cc.swelink.utils.combat;

import cc.swelink.mixin.LivingEntityFlagsAccessor;
import cc.swelink.mixin.RotateHeadPacketAccessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ShieldStates {
    public static final ShieldStates INSTANCE = new ShieldStates();
    private final Map<Integer, State> states = new ConcurrentHashMap<>();

    private ShieldStates() {
    }

    public void arrived(Packet<?> packet) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (var child : bundle.subPackets()) arrived(child);
        } else if (packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket) {
            states.clear();
        } else if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
            remove.entityIds().forEach(id -> states.remove(id));
        } else if (packet instanceof ClientboundSetEntityDataPacket data && data.packedItems() != null) {
            int flagsId = LivingEntityFlagsAccessor.paradox$livingFlags().id();
            for (var value : data.packedItems()) {
                if (value.id() != flagsId || !(value.value() instanceof Byte flags)) continue;
                var state = states.computeIfAbsent(data.id(), ignored -> new State());
                state.using = (flags & 1) != 0;
                state.hand = (flags & 2) != 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            }
        } else if (packet instanceof ClientboundRotateHeadPacket head) {
            states.computeIfAbsent(((RotateHeadPacketAccessor) head).paradox$entityId(), ignored -> new State()).headYaw = head.getYHeadRot();
        } else if (packet instanceof ClientboundSetEquipmentPacket equipment) {
            for (var slot : equipment.getSlots()) {
                if (slot.getFirst() == EquipmentSlot.MAINHAND) {
                    states.computeIfAbsent(equipment.getEntity(), ignored -> new State()).mainHand = slot.getSecond();
                } else if (slot.getFirst() == EquipmentSlot.OFFHAND) {
                    states.computeIfAbsent(equipment.getEntity(), ignored -> new State()).offHand = slot.getSecond();
                }
            }
        }
    }

    public boolean isUsingItem(Player player) {
        var state = states.get(player.getId());
        return state != null && state.using != null ? state.using : player.isUsingItem();
    }

    public InteractionHand usedHand(Player player) {
        var state = states.get(player.getId());
        return state != null && state.hand != null ? state.hand : player.getUsedItemHand();
    }

    public ItemStack itemInHand(Player player, InteractionHand hand) {
        var state = states.get(player.getId());
        ItemStack stack = state == null ? null : hand == InteractionHand.MAIN_HAND ? state.mainHand : state.offHand;
        return stack != null ? stack : player.getItemInHand(hand);
    }

    public float headYaw(Player player) {
        var state = states.get(player.getId());
        return state != null && state.headYaw != null ? state.headYaw : player.getYHeadRot();
    }

    private static final class State {
        private volatile Float headYaw;
        private volatile Boolean using;
        private volatile InteractionHand hand;
        private volatile ItemStack mainHand;
        private volatile ItemStack offHand;
    }
}
