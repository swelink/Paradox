package cc.swelink.mixin;

import net.minecraft.client.MouseHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos")
    void paradox$setXpos(double xpos);

    @Accessor("ypos")
    void paradox$setYpos(double ypos);
}
