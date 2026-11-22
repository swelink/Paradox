package cc.swelink.systems.modules.combat.velocity;

import cc.swelink.events.EventHandler;
import cc.swelink.events.InputEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.combat.velocity.mode.JumpResetVelocity;
import cc.swelink.systems.modules.combat.velocity.mode.MinemenVelocity;
import cc.swelink.systems.modules.combat.velocity.mode.NormalVelocity;
import cc.swelink.systems.modules.combat.velocity.mode.VelocityMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public final class VelocityModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Custom", "Custom", "Jump Reset", "Minemen"));
    private final NormalVelocity normal = new NormalVelocity(this);
    private final JumpResetVelocity jumpReset = new JumpResetVelocity(this);
    private final MinemenVelocity minemen = new MinemenVelocity(this);
    private VelocityMode active;
    private LocalPlayer owner;

    public VelocityModule() {
        super("Velocity", "Reduces knockback", Category.COMBAT);
        for (var setting : normal.settings()) {
            addSetting(setting);
        }
        for (var setting : jumpReset.settings()) {
            addSetting(setting);
        }
        mode.onChanged(ignored -> changeMode());
    }

    public boolean selected(VelocityMode candidate) {
        return mode.get().equals(candidate.name());
    }

    private void changeMode() {
        if (!isEnabled()) {
            return;
        }
        VelocityMode next = switch (mode.get()) {
            case "Jump Reset" -> jumpReset;
            case "Minemen" -> minemen;
            default -> normal;
        };
        if (active == next) {
            return;
        }
        if (active != null) {
            active.disable();
        }
        active = next;
        next.enable();
    }

    @Override
    protected void onEnable() {
        owner = Minecraft.getInstance().player;
        changeMode();
    }

    @Override
    protected void onDisable() {
        if (active != null) {
            active.disable();
        }
        active = null;
        owner = null;
    }

    public void beforeTick(Minecraft client) {
        if (active == null) {
            return;
        }
        if (owner != client.player) {
            active.disable();
            active.enable();
            owner = client.player;
        }
        active.preTick(client);
    }

    public Input prepareInput(Minecraft client, Input input) {
        return active == null || client.player == null ? input : active.prepareInput(client, input);
    }

    public boolean cancelMotion() {
        return active == normal && normal.cancelMotion();
    }

    public Vec3 motion(LocalPlayer player, Vec3 movement) {
        return active == null ? movement : active.motion(player, movement);
    }

    public Vec3 explosion(ClientboundExplodePacket packet, Vec3 movement) {
        return active == null ? movement : active.explosion(packet, movement);
    }

    @EventHandler(priority = 110)
    private void onPreTick(PreTickEvent event) {
        beforeTick(event.client());
    }

    @EventHandler(priority = 10)
    private void onInput(InputEvent event) {
        event.input(prepareInput(event.client(), event.input()));
    }
}
