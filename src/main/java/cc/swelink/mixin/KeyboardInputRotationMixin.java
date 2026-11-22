package cc.swelink.mixin;

import cc.swelink.systems.modules.combat.AutoTotemModule;
import cc.swelink.Paradox;
import cc.swelink.events.InputEvent;
import cc.swelink.systems.modules.movement.TargetStrafeModule;
import cc.swelink.utils.rotation.MoveFix;
import cc.swelink.utils.rotation.SilentMoveFix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.entity.player.Input;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputRotationMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void paradox$correctInput(CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || player.input != (Object) this) {
            return;
        }
        Input original = keyPresses;
        keyPresses = Paradox.EVENTS.post(new InputEvent(client, keyPresses)).input();
        Input movement = keyPresses;
        if (Paradox.ROTATIONS.movementCorrection() == MoveFix.SILENT) {
            keyPresses = SilentMoveFix.correct(keyPresses, player.getYRot(), Paradox.ROTATIONS.movementYaw(player.getYRot()),
                    player.isSprinting());
        } else {
            SilentMoveFix.reset();
        }
        Input strafe = Paradox.MODULES.get(TargetStrafeModule.class).remap(client, movement);
        if (strafe != null) keyPresses = strafe;
        if (Paradox.MODULES.get(AutoTotemModule.class).holdsInput()) keyPresses = Input.EMPTY;
        boolean changed = keyPresses.forward() != original.forward() || keyPresses.backward() != original.backward()
                || keyPresses.left() != original.left() || keyPresses.right() != original.right();
        if (changed || strafe != null || Paradox.ROTATIONS.movementCorrection() == MoveFix.SILENT) {
            float forward = (keyPresses.forward() ? 1 : 0) - (keyPresses.backward() ? 1 : 0);
            float sideways = (keyPresses.left() ? 1 : 0) - (keyPresses.right() ? 1 : 0);
            moveVector = new Vec2(sideways, forward).normalized();
        }
    }
}
