package cc.swelink.systems.modules.movement;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.InputEvent;
import cc.swelink.events.InteractionEvent;
import cc.swelink.events.TargetRefreshEvent;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.rotation.Rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.EntityHitResult;

public final class OmnisprintModule extends Module {
    private final BooleanSetting rotations = addSetting(new BooleanSetting("Rotations", true));
    private Input movement = Input.EMPTY;

    public OmnisprintModule() {
        super("OmniSprint", "Sprints in any direction", Category.MOVEMENT);
    }

    public Input prepareInput(Minecraft client, Input input) {
        movement = input;
        if (!active(client) || !moving(input) || input.shift()) {
            return input;
        }
        if (rotations.get()) {
            requestRotation(client);
            Paradox.ROTATIONS.apply(client);
        }
        return new Input(input.forward(), input.backward(), input.left(), input.right(),
                input.jump(), input.shift(), true);
    }

    @Override
    public void tick(Minecraft client) {
        if (active(client) && rotations.get() && moving(movement) && !movement.shift()) {
            requestRotation(client);
        }
    }

    public boolean allowsAnyDirection(Minecraft client) {
        return active(client) && !rotations.get() && moving(client.player.input.keyPresses);
    }

    public boolean usesRotations(Minecraft client) {
        return active(client) && rotations.get();
    }

    public void beforeInteraction(Minecraft client) {
        if (usesRotations(client) && !Paradox.ROTATIONS.hasHigherPriority(-2)) {
            Paradox.ROTATIONS.holdLocalRotation(client, true);
        }
    }

    public void refreshLocalTarget(Minecraft client) {
        if (!usesRotations(client) || Paradox.ROTATIONS.hasHigherPriority(-2)) {
            return;
        }
        client.hitResult = client.player.raycastHitResult(1, client.player);
        client.crosshairPickEntity = client.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
    }

    private void requestRotation(Minecraft client) {
        int forward = (movement.forward() ? 1 : 0) - (movement.backward() ? 1 : 0);
        int sideways = (movement.left() ? 1 : 0) - (movement.right() ? 1 : 0);
        float yaw = client.player.getYRot() + (float) Math.toDegrees(Math.atan2(-sideways, forward));
        Paradox.ROTATIONS.requestSilent(new Rotation(yaw, client.player.getXRot()), 180, -2, true);
    }

    private boolean active(Minecraft client) {
        var player = client.player;
        return isEnabled() && Gameplay.canAct(client) && !player.isPassenger() && !player.isFallFlying();
    }

    private static boolean moving(Input input) {
        return input.forward() != input.backward() || input.left() != input.right();
    }

    @Override
    protected void onDisable() {
        movement = Input.EMPTY;
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 20)
    private void onInput(InputEvent event) {
        event.input(prepareInput(event.client(), event.input()));
    }

    @EventHandler(priority = 20)
    private void onInteraction(InteractionEvent event) {
        beforeInteraction(event.client());
    }

    @EventHandler(priority = 20)
    private void onTargetRefresh(TargetRefreshEvent event) {
        refreshLocalTarget(event.client());
    }
}
