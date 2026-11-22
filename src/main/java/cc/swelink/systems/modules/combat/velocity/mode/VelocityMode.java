package cc.swelink.systems.modules.combat.velocity.mode;

import cc.swelink.settings.Setting;
import cc.swelink.systems.modules.combat.velocity.VelocityModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public abstract class VelocityMode {
    protected final VelocityModule module;
    private final String name;
    private final List<Setting<?>> settings = new ArrayList<>();

    protected VelocityMode(VelocityModule module, String name) {
        this.module = module;
        this.name = name;
    }

    protected <T extends Setting<?>> T add(T setting) {
        return add(setting, () -> true);
    }

    protected <T extends Setting<?>> T add(T setting, BooleanSupplier condition) {
        setting.visibleWhen(() -> module.selected(this) && condition.getAsBoolean());
        settings.add(setting);
        return setting;
    }

    public final String name() {
        return name;
    }
    public final List<Setting<?>> settings() {
        return List.copyOf(settings);
    }
    public void enable() {
    }
    public void disable() {
    }
    public void preTick(Minecraft client) {
    }
    public Input prepareInput(Minecraft client, Input input) {
        return input;
    }
    public Vec3 motion(LocalPlayer player, Vec3 movement) {
        return movement;
    }
    public Vec3 explosion(ClientboundExplodePacket packet, Vec3 movement) {
        return movement;
    }
}
