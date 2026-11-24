package cc.swelink.systems.modules.render.esp.mode;

import cc.swelink.Paradox;
import cc.swelink.renderer.TargetEspRenderer;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.systems.modules.render.esp.EspModule;
import cc.swelink.utils.render.GlowSettings;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public final class GlowEsp extends EspMode {
    public static final Identifier POST_EFFECT = Paradox.id("esp_glow");
    private final GlowSettings glow = new GlowSettings(this::add, () -> true, 12);
    private final BooleanSetting hurt = add(new BooleanSetting("Hurt flash", true));

    public GlowEsp(EspModule module) {
        super(module, "Glow");
    }

    public int color(Entity entity, float partialTicks) {
        int color = module.color();
        return hurt.get() ? TargetEspRenderer.hurtColor(entity, color, partialTicks) : color;
    }

    public void configure(PostChain chain) {
        if (chain != null && POST_EFFECT.equals(chain.id())) {
            glow.configure(chain, 0xFFFFFF);
        }
    }
}
