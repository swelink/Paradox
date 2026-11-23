package cc.swelink.systems.modules.render.esp.mode;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.esp.EspModule;
import cc.swelink.utils.render.OutlineShaderSettings;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

public final class ShaderEsp extends EspMode {
    public static final Identifier POST_EFFECT = Paradox.id("esp");
    private final OutlineShaderSettings settings;

    public ShaderEsp(EspModule module) {
        super(module, "Shader");
        settings = new OutlineShaderSettings(this::add, () -> module.selected(this));
    }

    public void configure(PostChain chain) {
        if (chain != null && POST_EFFECT.equals(chain.id())) {
            settings.configure(chain);
        }
    }
}
