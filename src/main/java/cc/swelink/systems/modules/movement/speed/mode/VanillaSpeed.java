package cc.swelink.systems.modules.movement.speed.mode;

import cc.swelink.systems.modules.movement.speed.SpeedModule;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;

public final class VanillaSpeed extends SpeedMode {
    public VanillaSpeed(SpeedModule module) {
        super(module, "Vanilla");
    }

    @Override
    public Input prepareInput(Minecraft client, Input input) {
        if (!input.forward() || input.backward() || input.shift()
                || !client.player.isSprinting() || !client.player.onGround()) return input;
        return new Input(input.forward(), input.backward(), input.left(), input.right(),
                true, input.shift(), input.sprint());
    }
}
