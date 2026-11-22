package cc.swelink.systems.modules.movement.speed.mode;

import cc.swelink.Paradox;
import cc.swelink.settings.Setting;
import cc.swelink.systems.modules.movement.speed.SpeedModule;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public abstract class SpeedMode {
    protected final SpeedModule module;
    private final String name;
    private final List<Setting<?>> settings = new ArrayList<>();

    protected SpeedMode(SpeedModule module, String name) {
        this.module = module;
        this.name = name;
    }

    protected <T extends Setting<?>> T add(T setting) {
        setting.visibleAlsoWhen(() -> module.selected(this));
        settings.add(setting);
        return setting;
    }

    public final String name() {
        return name;
    }

    public final List<Setting<?>> settings() {
        return List.copyOf(settings);
    }

    public Input prepareInput(Minecraft client, Input input) {
        return input;
    }

    public Vec3 movement(Minecraft client, Vec3 direction, Vec3 original) {
        return original;
    }

    protected static Vec3 horizontal(Minecraft client, Vec3 direction, double amount) {
        double yaw = Math.toRadians(Paradox.ROTATIONS.movementYaw(client.player.getYRot()));
        double sideways = direction.x * amount;
        double forward = direction.z * amount;
        return new Vec3(sideways * Math.cos(yaw) - forward * Math.sin(yaw), 0,
                forward * Math.cos(yaw) + sideways * Math.sin(yaw));
    }
}
