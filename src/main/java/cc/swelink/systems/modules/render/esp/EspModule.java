package cc.swelink.systems.modules.render.esp;

import cc.swelink.systems.modules.render.PostProcessingModule;
import cc.swelink.utils.combat.Targets;
import cc.swelink.events.EventHandler;
import cc.swelink.events.OverlayRenderEvent;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.renderer.ThemeColors;
import cc.swelink.settings.ModeSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.render.esp.mode.EspMode;
import cc.swelink.systems.modules.render.esp.mode.GlowEsp;
import cc.swelink.systems.modules.render.esp.mode.TwoDEsp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class EspModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "2D", "2D", "Glow"));
    private final BooleanSetting self = addSetting(new BooleanSetting("Show self", false));
    private final BooleanSetting invisible = addSetting(new BooleanSetting("Invisible players", true));
    private final TwoDEsp twoDimensional = new TwoDEsp(this);
    private final GlowEsp glow = new GlowEsp(this);
    private EspMode active;

    public EspModule() {
        super("ESP", "Highlights players", Category.RENDER);
        for (var setting : twoDimensional.settings()) {
            addSetting(setting);
        }
        for (var setting : glow.settings()) {
            addSetting(setting);
        }
        mode.onChanged(ignored -> changeMode());
    }

    public boolean selected(EspMode candidate) {
        return mode.get().equals(candidate.name());
    }

    public boolean glowSelected() {
        return selected(glow);
    }

    public boolean glowActive() {
        return isEnabled() && active == glow && PostProcessingModule.active();
    }

    public int color() {
        return ThemeColors.primary();
    }

    public TwoDEsp boxes() {
        return twoDimensional;
    }

    public GlowEsp glow() {
        return glow;
    }

    public boolean shouldRender(Entity entity) {
        var client = Minecraft.getInstance();
        if (!(entity instanceof Player player) || client.player == null || client.level == null
                || !player.isAlive() || player.isSpectator() || player.level() != client.level
                || player.isInvisible() && !invisible.get() || Targets.bot(player)) {
            return false;
        }
        if (player == client.player && !self.get()
                || player == client.getCameraEntity() && client.options.getCameraType().isFirstPerson()) {
            return false;
        }
        return true;
    }

    private void changeMode() {
        if (!isEnabled()) {
            return;
        }
        EspMode next = glowSelected() ? glow : twoDimensional;
        if (active == next) {
            return;
        }
        if (active != null) {
            active.disable();
        }
        active = next;
        active.enable();
    }

    @Override
    protected void onEnable() {
        changeMode();
    }

    @Override
    protected void onDisable() {
        if (active != null) {
            active.disable();
        }
        active = null;
    }

    public void render(Minecraft client, GuiGraphicsExtractor graphics) {
        if (active != null && client.player != null && client.level != null && !client.gui.hud.isHidden()) {
            active.render(client, graphics);
        }
    }

    @EventHandler(priority = 20)
    private void onOverlayRender(OverlayRenderEvent event) {
        render(event.client(), event.graphics());
    }
}
