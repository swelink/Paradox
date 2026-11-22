package cc.swelink.systems.modules.render;

import cc.swelink.events.EventHandler;
import cc.swelink.events.PreTickEvent;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public final class FreelookModule extends Module {
    private LocalPlayer player;
    private float yaw;
    private float pitch;

    public FreelookModule() {
        super("Freelook", "Look around without turning", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        beforeTick(Minecraft.getInstance());
    }

    @Override
    protected void onDisable() {
        release(Minecraft.getInstance());
    }

    public void beforeTick(Minecraft client) {
        if (!isEnabled() || client.player == null || client.level == null || !client.player.isAlive()
                || client.player.isSleeping() || client.getCameraEntity() != client.player) {
            release(client);
            return;
        }
        if (player != client.player) {
            player = client.player;
            yaw = player.getYRot();
            pitch = player.getXRot();
        }
    }

    private void release(Minecraft client) {
        player = null;
    }

    public boolean active() {
        var client = Minecraft.getInstance();
        return isEnabled() && player != null && player == client.player && client.level != null
                && player.isAlive() && !player.isSleeping() && client.getCameraEntity() == player;
    }

    public boolean turn(double horizontal, double vertical) {
        if (!active()) return false;
        yaw = Mth.wrapDegrees(yaw + (float) (horizontal * 0.15));
        pitch = Math.clamp(pitch + (float) (vertical * 0.15), -90, 90);
        return true;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 100)
    private void onPreTick(PreTickEvent event) {
        beforeTick(event.client());
    }
}
