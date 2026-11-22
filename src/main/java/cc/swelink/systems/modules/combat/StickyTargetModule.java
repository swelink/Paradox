package cc.swelink.systems.modules.combat;

import cc.swelink.utils.time.GameTime;
import cc.swelink.events.AttackEvent;
import cc.swelink.events.EventHandler;
import cc.swelink.settings.SecondsSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class StickyTargetModule extends Module {
    private final SecondsSetting duration = addSetting(new SecondsSetting("Duration", 30, 10, 50));
    private ClientLevel level;
    private int targetId = -1;
    private long expiresAt;

    public StickyTargetModule() {
        super("StickyTarget", "Locks onto your last target", Category.COMBAT);
    }

    public void attacked(Minecraft client, int entityId) {
        if (!isEnabled() || client.level == null || !(client.level.getEntity(entityId) instanceof Player)) return;
        level = client.level;
        targetId = entityId;
        expiresAt = GameTime.nanos() + (long) (duration.seconds() * 1_000_000_000L);
    }

    public boolean allows(Minecraft client, Entity entity) {
        if (!(entity instanceof Player)) return true;
        Player locked = locked(client);
        return locked == null || locked == entity;
    }

    private Player locked(Minecraft client) {
        if (!isEnabled() || targetId == -1) return null;
        if (client.level != level || GameTime.nanos() - expiresAt > 0
                || !(client.level.getEntity(targetId) instanceof Player target)
                || !target.isAlive() || target.isRemoved() || target.isSpectator()) {
            reset();
            return null;
        }
        return target;
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        level = null;
        targetId = -1;
    }

    @EventHandler(priority = 50)
    private void onAttack(AttackEvent event) {
        attacked(event.client(), event.entityId());
    }
}
