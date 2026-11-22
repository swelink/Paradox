package cc.swelink.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenInvoker {
    @Invoker("getHoveredSlot")
    Slot paradox$getHoveredSlot(double mouseX, double mouseY);

    @Accessor("leftPos")
    int paradox$leftPos();

    @Accessor("topPos")
    int paradox$topPos();
}
