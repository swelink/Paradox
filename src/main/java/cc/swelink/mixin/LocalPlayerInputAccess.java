package cc.swelink.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerInputAccess {
    @Accessor("lastSentInput")
    Input paradox$lastSentInput();

    @Accessor("sprintTriggerTime")
    void paradox$setSprintTriggerTime(int ticks);
}
