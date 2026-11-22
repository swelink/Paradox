package cc.swelink.systems.modules.render;

import cc.swelink.renderer.CapeTexture;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Locale;

public final class CapeModule extends Module {
    private static final Identifier VANILLA_ELYTRA = Identifier.withDefaultNamespace("textures/entity/equipment/wings/elytra.png");
    private static final ClientAsset.Texture DEFAULT_ELYTRA = new ClientAsset.ResourceTexture(VANILLA_ELYTRA, VANILLA_ELYTRA);

    private static final int CLIP_COLUMNS = 11;
    private static final int CLIP_FRAMES = 44;
    private static final long CLIP_FRAME_NANOS = 50_000_000L;
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Paradox", "Paradox", "Hypergamy", "Job Application", "Bojack"));
    private final BooleanSetting physics = addSetting(new BooleanSetting("Physics", false));
    private final IntSetting quality = addSetting(new IntSetting("Quality", 8, 4, 32) {
        @Override
        protected String formatValue(int value) {
            return value + "x";
        }
    });

    public CapeModule() {
        super("Cape", "Paradox or image cape", Category.RENDER);
        quality.visibleWhen(physics::get);
    }

    public boolean physics() {
        return physics.get();
    }

    public int quality() {
        return quality.get();
    }

    public void apply(AvatarRenderState state) {
        PlayerSkin skin = state.skin;
        ClientAsset.Texture elytra = skin.elytra();
        if (elytra == null) {
            elytra = state.showCape && skin.cape() != null ? skin.cape() : DEFAULT_ELYTRA;
        }
        ClientAsset.Texture cape = mode.get().equals("Paradox") ? CapeTexture.texture(ThemeColors.theme())
                : mode.get().equals("Bojack") ? CapeTexture.clip("bojack", CLIP_COLUMNS, CLIP_FRAMES, CLIP_FRAME_NANOS)
                : CapeTexture.image(mode.get().toLowerCase(Locale.ROOT).replace(' ', '_'));
        state.skin = new PlayerSkin(skin.body(), cape, elytra, skin.model(), skin.secure());
        state.showCape = true;
    }
}
