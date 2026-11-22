package cc.swelink.utils.combat;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.combat.AntiBotModule;
import cc.swelink.systems.modules.player.OptimizerModule;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;

public final class Targets {
    private Targets() {
    }

    public static boolean attackable(Minecraft client, Entity entity) {
        if (!(entity instanceof Player player) || client.player == null || player == client.player
                || player.level() != client.level || !player.isAlive() || player.isRemoved() || player.isSpectator()) {
            return false;
        }
        return !bot(player) && !Paradox.FRIENDS.isFriend(player);
    }

    public static boolean hurtReady(Minecraft client, Player enemy) {
        return enemy.hurtTime <= 1 + latencyTicks(client);
    }

    public static int latencyTicks(Minecraft client) {
        if (!Paradox.MODULES.get(OptimizerModule.class).pingCompensation()) return 0;
        var connection = client.getConnection();
        var info = connection == null || client.player == null ? null : connection.getPlayerInfo(client.player.getUUID());
        return info == null ? 0 : Math.max(info.getLatency(), 0) / 50;
    }

    public static boolean visible(Minecraft client, Player player, boolean throughWalls) {
        return throughWalls || client.player.hasLineOfSight(player);
    }

    public static boolean pickable(Entity entity) {
        return EntitySelector.CAN_BE_PICKED.test(entity);
    }

    public static boolean bot(Player player) {
        var antiBot = Paradox.MODULES.get(AntiBotModule.class);
        return antiBot.isEnabled() && antiBot.isBot(player);
    }
}
