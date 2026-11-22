package cc.swelink.utils.network;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.combat.FakeLagModule;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;

public final class PostMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger("paradox-post");
    private static final ArrayDeque<String> history = new ArrayDeque<>();
    private static String firstPost;
    private static boolean sentFlying;
    private static boolean movedBeforeTickEnd;
    private static boolean teleportPending;

    private PostMonitor() {
    }

    public static synchronized void sent(Packet<?> packet) {
        String name = packet.getClass().getSimpleName().replace("Serverbound", "").replace("Packet", "");
        boolean released = Paradox.MODULES.get(FakeLagModule.class).releasingNow();
        history.addLast(name + (released ? "*" : "") + (Thread.currentThread().getName().equals("Render thread") ? "" : "@" + Thread.currentThread().getName()));
        while (history.size() > 24) history.removeFirst();
        if (packet instanceof ServerboundAcceptTeleportationPacket) {
            teleportPending = true;
            return;
        }
        boolean tick;
        if (packet instanceof ServerboundMovePlayerPacket) {
            tick = !teleportPending;
            teleportPending = false;
            movedBeforeTickEnd = true;
        } else if (packet instanceof ServerboundClientTickEndPacket) {
            tick = !movedBeforeTickEnd;
            movedBeforeTickEnd = false;
        } else {
            tick = false;
        }
        if (tick) {
            firstPost = null;
            sentFlying = true;
            return;
        }
        if (packet instanceof ServerboundPongPacket) {
            if (sentFlying && firstPost != null) {
                LOGGER.warn("[PostMonitor] would flag Post ({}): {}", firstPost, String.join(" ", history));
            }
            firstPost = null;
            sentFlying = false;
            return;
        }
        if (!sentFlying) return;
        if (firstPost == null && (packet instanceof ServerboundPlayerAbilitiesPacket
                || packet instanceof ServerboundSetCarriedItemPacket || packet instanceof ServerboundInteractPacket
                || packet instanceof ServerboundUseItemOnPacket || packet instanceof ServerboundAttackPacket
                || packet instanceof ServerboundPunchPacket || packet instanceof ServerboundUseItemPacket
                || packet instanceof ServerboundPlayerActionPacket || packet instanceof ServerboundPlayerCommandPacket)) {
            firstPost = name;
        }
    }
}
