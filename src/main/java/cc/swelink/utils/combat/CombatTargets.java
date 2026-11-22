package cc.swelink.utils.combat;

import net.minecraft.world.entity.player.Player;

public final class CombatTargets {
    private static final long FRESH_NANOS = 100_000_000L;

    private Player target;
    private long reportedAt;

    public void report(Player player) {
        if (player == null) return;
        long now = System.nanoTime();
        if (player == target || now - reportedAt > FRESH_NANOS) {
            target = player;
            reportedAt = now;
        }
    }

    public Player current() {
        return System.nanoTime() - reportedAt > FRESH_NANOS ? null : target;
    }
}
