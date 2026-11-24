package cc.swelink.systems.modules.render.esp.mode;

import cc.swelink.renderer.CombatLagRenderer;
import cc.swelink.renderer.TargetEspRenderer;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.PercentSetting;
import cc.swelink.systems.modules.render.esp.EspModule;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.ARGB;

public final class BoxEsp extends EspMode {
    private final PercentSetting opacity = add(new PercentSetting("Box opacity", 18));
    private final BooleanSetting hurt = add(new BooleanSetting("Hurt flash", true));

    public BoxEsp(EspModule module) {
        super(module, "Box");
    }

    public void submit(Minecraft client, PoseStack pose, LevelRenderState state, SubmitNodeCollector collector) {
        float tick = state.worldPartialTicks;
        for (var player : client.level.players()) {
            if (!module.shouldRender(player)) {
                continue;
            }
            int color = module.color();
            if (hurt.get()) {
                color = TargetEspRenderer.hurtColor(player, color, tick);
            }
            var offset = player.getPosition(tick).subtract(player.position());
            CombatLagRenderer.drawBox(player.getBoundingBox().move(offset),
                    ARGB.multiplyAlpha(color, opacity.get() / 100f), pose, state, collector);
        }
    }
}
