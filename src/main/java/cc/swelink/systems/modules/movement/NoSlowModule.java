package cc.swelink.systems.modules.movement;

import cc.swelink.events.EventHandler;
import cc.swelink.events.PreTickEvent;
import cc.swelink.settings.ModeSetting;
import cc.swelink.settings.PercentSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.player.GrimItemUse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class NoSlowModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Grim", "Grim", "Custom"));
    private final PercentSetting speed = addSetting(new PercentSetting("Speed", 100));
    private final GrimItemUse tracker = new GrimItemUse();

    public NoSlowModule() {
        super("NoSlow", "No slowdown when using items", Category.MOVEMENT);
        speed.visibleWhen(() -> !grim());
    }

    public GrimItemUse tracker() {
        return tracker;
    }

    private boolean grim() {
        return mode.get().equals("Grim");
    }

    public void beforeTick(Minecraft client) {
        if (client.player == null) {
            tracker.reset();
            return;
        }
        tracker.tickPre();
    }

    public boolean itemSlowdown(LocalPlayer player, boolean using) {
        if (!isEnabled() || !grim()) return using;
        if (player.isPassenger() || player.isFallFlying()) return using;
        if (!using && !tracker.grimSlowed()) return false;
        if (!tracker.mayGoFast()) return true;
        if (!player.onGround() && landsThisTick(player)) return true;
        tracker.wentFast();
        return false;
    }

    public float speedMultiplier(float original) {
        return isEnabled() && !grim() ? speed.get() / 100f : original;
    }

    private static boolean landsThisTick(LocalPlayer player) {
        double vy = player.getDeltaMovement().y;
        return vy < 0 && !player.level().noCollision(player, player.getBoundingBox().move(0, vy, 0));
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 120)
    private void onPreTick(PreTickEvent event) {
        beforeTick(event.client());
    }
}
