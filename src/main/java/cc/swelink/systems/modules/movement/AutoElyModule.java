package cc.swelink.systems.modules.movement;

import cc.swelink.events.EventHandler;
import cc.swelink.events.InputEvent;
import cc.swelink.mixin.LocalPlayerInputAccess;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;

public final class AutoElyModule extends Module {
    public AutoElyModule() {
        super("AutoEly", "Takes off and recasts elytra", Category.MOVEMENT);
    }

    public Input prepareInput(Minecraft client, Input input) {
        var player = client.player;
        if (!isEnabled() || !input.jump() || !((LocalPlayerInputAccess) player).paradox$lastSentInput().jump()) {
            return input;
        }
        boolean canTakeOff = !player.onGround() && !player.isFallFlying() && !player.isPassenger()
                && !player.getAbilities().flying && !player.getAbilities().mayfly
                && !player.onClimbable() && !player.isInLiquid() && !player.hasEffect(MobEffects.LEVITATION)
                && EquipmentSlot.VALUES.stream().anyMatch(slot -> LivingEntity.canGlideUsing(player.getItemBySlot(slot), slot));
        if (!canTakeOff) {
            return input;
        }
        return new Input(input.forward(), input.backward(), input.left(), input.right(),
                false, input.shift(), input.sprint());
    }

    @EventHandler(priority = 60)
    private void onInput(InputEvent event) {
        event.input(prepareInput(event.client(), event.input()));
    }
}
