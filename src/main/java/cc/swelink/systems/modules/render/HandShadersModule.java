package cc.swelink.systems.modules.render;

import cc.swelink.renderer.HandShaderRenderer;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.render.OutlineShaderSettings;
import cc.swelink.utils.render.PostEffectUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;

public final class HandShadersModule extends Module {
    private final OutlineShaderSettings shader = new OutlineShaderSettings(this::addSetting, () -> true);
    private final BooleanSetting thirdPerson = addSetting(new BooleanSetting("Third person", false));
    private final HandShaderRenderer renderer = new HandShaderRenderer();

    public HandShadersModule() {
        super("HandShaders", "ESP shaders on your hands", Category.RENDER);
    }

    public boolean active() {
        var client = Minecraft.getInstance();
        return isEnabled() && client.player != null && client.level != null
                && client.getCameraEntity() == client.player
                && !client.player.isSpectator() && !client.player.isSleeping() && !client.gui.hud.isHidden();
    }

    public boolean handsActive() {
        return active() && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    public boolean thirdPersonActive() {
        return active() && thirdPerson.get() && !Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    public HandShaderRenderer renderer() {
        return renderer;
    }

    public void configure(PostChain chain) {
        shader.configure(chain);
        int rgb = ThemeColors.primary();
        PostEffectUtils.writeUniform(chain, "HandColor", ((rgb >>> 16) & 255) / 255f,
                ((rgb >>> 8) & 255) / 255f, (rgb & 255) / 255f, 1);
    }

    @Override
    protected void onDisable() {
        renderer.requestRelease();
    }
}
