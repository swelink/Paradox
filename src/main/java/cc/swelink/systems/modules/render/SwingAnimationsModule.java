package cc.swelink.systems.modules.render;

import cc.swelink.Paradox;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.combat.KillauraModule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;

public final class SwingAnimationsModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Default", "Default", "Flick", "Swipe", "Power"));
    private final IntSetting speed = addSetting(new IntSetting("Speed", 100, 25, 300) {
        @Override
        protected String formatValue(int value) {
            return value + "%";
        }
    });
    private final BooleanSetting onlySwords = addSetting(new BooleanSetting("Only swords", true));
    private final BooleanSetting onlyKillAura = addSetting(new BooleanSetting("Only KillAura", false));

    public SwingAnimationsModule() {
        super("Animations", "Changes the swing animation", Category.RENDER);
    }

    public boolean customizes(InteractionHand hand, ItemStack item) {
        if (!isEnabled() || hand != InteractionHand.MAIN_HAND || onlySwords.get() && !item.is(ItemTags.SWORDS)
                || item.getAttackAnimation().type() != SwingAnimationType.WHACK) return false;
        if (!onlyKillAura.get()) return true;
        var aura = Paradox.MODULES.get(KillauraModule.class);
        return aura.isEnabled() && aura.target(Minecraft.getInstance()) != null;
    }

    public int swingDuration(int original) {
        var player = Minecraft.getInstance().player;
        if (player == null || speed.get() == 100 || !customizes(InteractionHand.MAIN_HAND, player.getMainHandItem())) return original;
        return Math.max(1, Math.round(original * 100f / speed.get()));
    }

    public void transform(PoseStack pose, HumanoidArm arm, float progress, float equipProgress) {
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float swing = Math.clamp(progress, 0, 1);
        if (mode.get().equals("Default")) {
            float sweep = (float) Math.sin(Math.sqrt(swing) * Math.PI);
            float turn = (float) Math.sin(swing * swing * Math.PI);
            pose.translate(side * 0.56f, -0.52f, -0.72f);
            pose.rotateDegrees(Axis.YP, side * (45 - turn * 18));
            pose.rotateDegrees(Axis.ZP, side * sweep * -18);
            pose.rotateDegrees(Axis.XP, sweep * -76);
            pose.rotateDegrees(Axis.YP, side * -45);
            return;
        }
        float sweep = (float) Math.sin(Math.pow(swing, 0.53) * Math.PI);
        float dip = mode.get().equals("Power") ? Math.clamp(equipProgress, 0, 1) * 0.6f : 0;
        pose.translate(side * 0.56f, -0.52f - dip, -0.72f);
        pose.rotateDegrees(Axis.XP, 48);
        if (mode.get().equals("Flick")) {
            pose.rotateDegrees(Axis.YP, side * -58);
            pose.rotateDegrees(Axis.ZP, side * (108 + sweep * 22));
        } else {
            pose.rotateDegrees(Axis.YP, side * (-58 + sweep * 32));
            pose.rotateDegrees(Axis.ZP, side * 108);
        }
    }
}
