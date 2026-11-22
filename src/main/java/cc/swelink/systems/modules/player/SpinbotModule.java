package cc.swelink.systems.modules.player;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.InteractionEvent;
import cc.swelink.events.TargetRefreshEvent;
import cc.swelink.settings.StepSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.player.Gameplay;
import cc.swelink.utils.rotation.MoveFixSettings;
import cc.swelink.utils.rotation.Rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.EntityHitResult;

public final class SpinbotModule extends Module {
    private final StepSetting speed = addSetting(new StepSetting("Speed", 90, 45, 90, 180));
    private final MoveFixSettings correction = new MoveFixSettings(this::addSetting, () -> true);
    private LocalPlayer spinningPlayer;
    private float yawOffset;

    public SpinbotModule() {
        super("SpinBot", "Spin the botnet", Category.PLAYER);
    }

    @Override
    public void tick(Minecraft client) {
        var player = client.player;
        if (!Gameplay.canAct(client)) {
            return;
        }

        if (spinningPlayer != player) {
            spinningPlayer = player;
            yawOffset = 0;
        }
        yawOffset = Mth.wrapDegrees(yawOffset + speed.get());
        var rotation = new Rotation(Mth.wrapDegrees(player.getYRot() + yawOffset), 90);
        Paradox.ROTATIONS.requestSilent(rotation, 180, -1, correction.get());
    }

    public void beforeInteraction(Minecraft client) {
        if (isEnabled() && client.player != null && client.level != null && !Paradox.ROTATIONS.hasHigherPriority(-1)) {
            Paradox.ROTATIONS.holdLocalRotation(client, correction.get());
        }
    }

    public void refreshLocalTarget(Minecraft client) {
        if (!isEnabled() || client.player == null || client.level == null || Paradox.ROTATIONS.hasHigherPriority(-1)) {
            return;
        }
        client.hitResult = client.player.raycastHitResult(1, client.player);
        client.crosshairPickEntity = client.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
    }

    @Override
    protected void onDisable() {
        spinningPlayer = null;
        Paradox.ROTATIONS.clear();
    }

    @EventHandler(priority = 10)
    private void onInteraction(InteractionEvent event) {
        beforeInteraction(event.client());
    }

    @EventHandler(priority = 10)
    private void onTargetRefresh(TargetRefreshEvent event) {
        refreshLocalTarget(event.client());
    }
}
