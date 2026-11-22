package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.SwingAnimationsModule;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingSpeedMixin {
    @ModifyReturnValue(method = "getModifiedSwingDuration", at = @At("RETURN"))
    private int paradox$swingSpeed(int original) {
        if ((Object) this != Minecraft.getInstance().player) return original;
        return Paradox.MODULES.get(SwingAnimationsModule.class).swingDuration(original);
    }
}
