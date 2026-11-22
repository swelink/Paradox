package cc.swelink.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftRightClickAccess {
    @Accessor("rightClickDelay")
    int paradox$rightClickDelay();

    @Accessor("rightClickDelay")
    void paradox$setRightClickDelay(int delay);
}
