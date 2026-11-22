package cc.swelink.systems.modules.movement.speed;

import cc.swelink.events.EventHandler;
import cc.swelink.events.InputEvent;
import cc.swelink.events.InteractionEvent;
import cc.swelink.events.TargetRefreshEvent;
import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.movement.speed.mode.CustomSpeed;
import cc.swelink.systems.modules.movement.speed.mode.GrimSpeed;
import cc.swelink.systems.modules.movement.speed.mode.SpeedMode;
import cc.swelink.systems.modules.movement.speed.mode.VanillaSpeed;
import cc.swelink.utils.player.Gameplay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class SpeedModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Custom", "Custom", "Vanilla", "Grim"));
    private final CustomSpeed custom = new CustomSpeed(this);
    private final GrimSpeed grim = new GrimSpeed(this);
    private final List<SpeedMode> modes = List.of(custom, new VanillaSpeed(this), grim);

    public SpeedModule() {
        super("Speed", "Makes you faster", Category.MOVEMENT);
        for (var speedMode : modes) {
            for (var setting : speedMode.settings()) {
                addSetting(setting);
            }
        }
    }

    public boolean selected(SpeedMode candidate) {
        return mode.get().equals(candidate.name());
    }

    private SpeedMode active(Minecraft client) {
        var player = client.player;
        if (!isEnabled() || player == null || client.level == null || !player.isAlive()
                || player.isSpectator() || player.isPassenger() || player.isFallFlying()
                || player.getAbilities().flying || player.isInWater() || player.isInLava()
                || player.onClimbable() || !Gameplay.focused(client)) return null;
        for (var speedMode : modes) {
            if (selected(speedMode)) return speedMode;
        }
        return null;
    }

    public Input prepareInput(Minecraft client, Input input) {
        var speedMode = active(client);
        return speedMode == null ? input : speedMode.prepareInput(client, input);
    }

    public Vec3 movement(Minecraft client, Vec3 input, Vec3 original) {
        var speedMode = active(client);
        if (speedMode == null) return original;
        double length = Math.sqrt(input.x * input.x + input.z * input.z);
        if (length < 1.0e-5) return original;
        return speedMode.movement(client, input.multiply(1 / length, 1, 1 / length), original);
    }

    public Vec3 velocity(Vec3 movement) {
        return grimSelected() ? grim.velocity(movement) : movement;
    }

    public GrimSpeed.PushWindow grimPushWindow(Minecraft client) {
        return active(client) == grim ? grim.pushWindow(client.player) : null;
    }

    public List<AABB> grimContactBoxes(net.minecraft.world.entity.Entity entity) {
        return grim.contactBoxes(entity);
    }

    private boolean grimSelected() {
        return isEnabled() && selected(grim);
    }

    @Override
    public void tick(Minecraft client) {
        if (active(client) == grim) grim.tick();
        else grim.idle();
    }

    @Override
    public void tickInput(Minecraft client) {
        if (active(client) == grim) grim.tickInput(client);
        else grim.releaseBoats(client);
    }

    @Override
    protected void onDisable() {
        grim.releaseBoats(Minecraft.getInstance());
    }

    public boolean noPush() {
        return grimSelected() && grim.noPush();
    }

    public void afterMove(LocalPlayer player) {
        if (grimSelected()) grim.afterMove(player);
    }

    public void sentPosition() {
        if (grimSelected()) grim.sentPosition();
    }

    public void teleported() {
        grim.teleported();
    }

    public boolean usesLocalTarget() {
        return grimSelected() && grim.usesLocalTarget();
    }

    public void beforeInteraction(Minecraft client) {
        if (grimSelected()) grim.beforeInteraction(client);
    }

    public void refreshLocalTarget(Minecraft client) {
        if (grimSelected()) grim.refreshLocalTarget(client);
    }

    public boolean customActive(Minecraft client) {
        return active(client) == custom;
    }

    @EventHandler(priority = 40)
    private void onInput(InputEvent event) {
        event.input(prepareInput(event.client(), event.input()));
    }

    @EventHandler(priority = 30)
    private void onInteraction(InteractionEvent event) {
        beforeInteraction(event.client());
    }

    @EventHandler(priority = 30)
    private void onTargetRefresh(TargetRefreshEvent event) {
        refreshLocalTarget(event.client());
    }
}
