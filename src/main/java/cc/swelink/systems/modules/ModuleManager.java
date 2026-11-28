package cc.swelink.systems.modules;

import cc.swelink.Paradox;
import cc.swelink.events.EventHandler;
import cc.swelink.events.ModuleToggleEvent;
import cc.swelink.events.PreTickEvent;
import cc.swelink.settings.BindModeSetting;
import cc.swelink.systems.modules.client.ClickGuiModule;
import cc.swelink.systems.modules.client.HudEditorModule;
import cc.swelink.systems.modules.combat.AimbotModule;
import cc.swelink.systems.modules.combat.BowBotModule;
import cc.swelink.systems.modules.combat.BacktrackModule;
import cc.swelink.systems.modules.combat.FakeLagModule;
import cc.swelink.systems.modules.combat.KnockbackDelayModule;
import cc.swelink.systems.modules.combat.SprintResetModule;
import cc.swelink.systems.modules.combat.AntiBotModule;
import cc.swelink.systems.modules.combat.StickyTargetModule;
import cc.swelink.systems.modules.combat.AnchorAuraModule;
import cc.swelink.systems.modules.combat.AutoAnchorModule;
import cc.swelink.systems.modules.combat.SafeAnchorModule;
import cc.swelink.systems.modules.combat.AutoCrystalModule;
import cc.swelink.systems.modules.combat.CrystalAuraModule;
import cc.swelink.systems.modules.combat.AutoMaceModule;
import cc.swelink.systems.modules.combat.DiveMaceModule;
import cc.swelink.systems.modules.combat.AutoTrapModule;
import cc.swelink.systems.modules.combat.AutoCartModule;
import cc.swelink.systems.modules.combat.CartAuraModule;
import cc.swelink.systems.modules.combat.SafeCartModule;
import cc.swelink.systems.modules.combat.CriticalsModule;
import cc.swelink.systems.modules.combat.HoverTotemModule;
import cc.swelink.systems.modules.combat.AutoTotemModule;
import cc.swelink.systems.modules.combat.KillauraModule;
import cc.swelink.systems.modules.combat.KeyPearlModule;
import cc.swelink.systems.modules.combat.KeyWindModule;
import cc.swelink.systems.modules.combat.ShieldbreakerModule;
import cc.swelink.systems.modules.combat.SpearSwapModule;
import cc.swelink.systems.modules.combat.TriggerbotModule;
import cc.swelink.systems.modules.combat.velocity.VelocityModule;
import cc.swelink.systems.modules.movement.OmnisprintModule;
import cc.swelink.systems.modules.movement.ElytraBoostModule;
import cc.swelink.systems.modules.movement.NoSlowModule;
import cc.swelink.systems.modules.movement.NoWebModule;
import cc.swelink.systems.modules.movement.speed.SpeedModule;
import cc.swelink.systems.modules.combat.PearlCatchModule;
import cc.swelink.systems.modules.combat.PearlGrapplerModule;
import cc.swelink.systems.modules.movement.TargetStrafeModule;
import cc.swelink.systems.modules.movement.NoJumpDelayModule;
import cc.swelink.systems.modules.movement.AutoElyModule;
import cc.swelink.systems.modules.player.AutoExpModule;
import cc.swelink.systems.modules.player.AutoMlgModule;
import cc.swelink.systems.modules.player.ClutchModule;
import cc.swelink.systems.modules.player.FastPlaceModule;
import cc.swelink.systems.modules.player.AntiResourcePackModule;
import cc.swelink.systems.modules.player.MiddleClickFriendModule;
import cc.swelink.systems.modules.player.OptimizerModule;
import cc.swelink.systems.modules.player.MultiActionsModule;
import cc.swelink.systems.modules.player.SpinbotModule;
import cc.swelink.systems.modules.render.CapeModule;
import cc.swelink.systems.modules.render.FogModule;
import cc.swelink.systems.modules.render.PlaceModule;
import cc.swelink.systems.modules.render.TimeModule;
import cc.swelink.systems.modules.render.EffectsModule;
import cc.swelink.systems.modules.render.FakePlayerModule;
import cc.swelink.systems.modules.render.KeybindHudModule;
import cc.swelink.systems.modules.render.KeystrokesModule;
import cc.swelink.systems.modules.render.InventoryHudModule;
import cc.swelink.systems.modules.render.SpeedGraphModule;
import cc.swelink.systems.modules.render.ModuleListModule;
import cc.swelink.systems.modules.render.TrajectoryModule;
import cc.swelink.systems.modules.render.HandShadersModule;
import cc.swelink.systems.modules.render.HandViewModule;
import cc.swelink.systems.modules.render.BlockHighlightModule;
import cc.swelink.systems.modules.render.FreecamModule;
import cc.swelink.systems.modules.render.FreelookModule;
import cc.swelink.systems.modules.render.SmoothCameraModule;
import cc.swelink.systems.modules.render.SwingAnimationsModule;
import cc.swelink.systems.modules.render.NametagsModule;
import cc.swelink.systems.modules.render.RadarModule;
import cc.swelink.systems.modules.render.AspectRatioModule;
import cc.swelink.systems.modules.render.RotationsModule;
import cc.swelink.systems.modules.render.TargetHudModule;
import cc.swelink.systems.modules.render.AmbienceModule;
import cc.swelink.systems.modules.render.PostProcessingModule;
import cc.swelink.systems.modules.render.BrutalModule;
import cc.swelink.systems.modules.render.TargetEspModule;
import cc.swelink.systems.modules.render.WatermarkModule;
import cc.swelink.systems.modules.render.TextHudModule;
import cc.swelink.systems.modules.render.PromoModule;
import cc.swelink.systems.modules.render.DynamicIslandModule;
import cc.swelink.systems.modules.render.esp.EspModule;
import cc.swelink.systems.modules.world.AutoDrainModule;
import cc.swelink.systems.modules.world.AutoWebModule;
import cc.swelink.systems.modules.world.TimerModule;
import cc.swelink.utils.player.SlotSelection;

import cc.swelink.gui.screens.ClickGuiScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    private boolean initialized;
    private final Map<Class<? extends Module>, Module> modulesByClass = new HashMap<>();
    private final List<Module> modules = new ArrayList<>();
    private List<Module> modulesSnapshot = List.of();
    private final Map<Module, BindState> bindStates = new IdentityHashMap<>();

    public void init() {
        if (initialized) {
            return;
        }
        Paradox.EVENTS.subscribe(this);
        var velocity = new VelocityModule();
        var esp = new EspModule();
        var criticals = new CriticalsModule();
        var shieldBreaker = new ShieldbreakerModule();
        var spearSwap = new SpearSwapModule();
        var autoMace = new AutoMaceModule();
        var autoCrystal = new AutoCrystalModule();
        var autoDrain = new AutoDrainModule();
        var autoWeb = new AutoWebModule();
        var spinbot = new SpinbotModule();
        var omnisprint = new OmnisprintModule();
        var rotationVisuals = new RotationsModule();
        var clickGui = new ClickGuiModule(this);
        var targetHud = new TargetHudModule();
        var watermark = new DynamicIslandModule();
        register(new AntiBotModule());
        register(new StickyTargetModule());
        register(new AimbotModule());
        register(new BowBotModule());
        register(new KillauraModule());
        register(new BacktrackModule());
        register(new FakeLagModule());
        register(new KnockbackDelayModule());
        register(new SprintResetModule());
        register(criticals);
        register(shieldBreaker);
        register(spearSwap);
        register(autoMace);
        register(new DiveMaceModule());
        register(velocity);
        register(new AutoAnchorModule());
        register(new AnchorAuraModule());
        register(new SafeAnchorModule());
        register(autoCrystal);
        register(new CrystalAuraModule());
        register(autoDrain);
        register(autoWeb);
        register(new AutoTrapModule());
        register(new CartAuraModule());
        register(new AutoCartModule());
        register(new SafeCartModule());
        register(new HoverTotemModule());
        register(new AutoTotemModule());
        register(new TriggerbotModule());
        register(spinbot);
        register(new MiddleClickFriendModule());
        register(new AutoMlgModule());
        register(new AutoExpModule());
        register(new ClutchModule());
        register(omnisprint);
        register(new SpeedModule());
        register(new NoSlowModule());
        register(new NoWebModule());
        register(new ElytraBoostModule());
        register(new PearlCatchModule());
        register(new PearlGrapplerModule());
        register(new KeyPearlModule());
        register(new KeyWindModule());
        register(new TargetStrafeModule());
        register(new AutoElyModule());
        register(new NoJumpDelayModule());
        register(new FastPlaceModule());
        register(new OptimizerModule());
        register(new AntiResourcePackModule());
        register(new MultiActionsModule());
        register(new TimerModule());
        register(rotationVisuals);
        register(esp);
        register(new HandShadersModule());
        register(new BlockHighlightModule());
        register(new SwingAnimationsModule());
        register(new HandViewModule());
        register(new FreelookModule());
        register(new FreecamModule());
        register(new SmoothCameraModule());
        register(new NametagsModule());
        register(new RadarModule());
        register(new FakePlayerModule());
        register(new AspectRatioModule());
        register(new FogModule());
        register(new PlaceModule());
        register(new TimeModule());
        register(new CapeModule());
        register(new TrajectoryModule());
        register(watermark);
        register(new WatermarkModule());
        register(new TextHudModule());
        register(new PromoModule());
        register(targetHud);
        register(new TargetEspModule());
        register(new BrutalModule());
        register(new AmbienceModule());
        register(new PostProcessingModule());
        register(new EffectsModule());
        register(new KeybindHudModule());
        register(new KeystrokesModule());
        register(new InventoryHudModule());
        register(new SpeedGraphModule());
        register(new ModuleListModule());
        register(clickGui);
        register(new HudEditorModule(this));
        initialized = true;
    }

    public <T extends Module> T get(Class<T> type) {
        Module module = modulesByClass.get(type);
        if (module == null) {
            throw new IllegalArgumentException("Module is not registered: " + type.getSimpleName());
        }
        return type.cast(module);
    }

    public void beforeTick(Minecraft client) {
        Paradox.EVENTS.post(new PreTickEvent(client));
    }

    public void afterTick(Minecraft client) {
        get(RotationsModule.class).capture(client);
        get(ClickGuiModule.class).tickAnimations();
    }

    @EventHandler(priority = 140)
    private void onSlotReset(PreTickEvent event) {
        SlotSelection.resetForInventory(event.client().player == null ? null : event.client().player.getInventory());
    }

    @EventHandler(priority = 230)
    private void onBinds(PreTickEvent event) {
        handleBinds(event.client());
    }

    public void register(Module module) {
        for (Module existing : modules) {
            if (existing.name().equalsIgnoreCase(module.name())) {
                return;
            }
        }
        modules.add(module);
        modulesByClass.put(module.getClass(), module);
        modulesSnapshot = List.copyOf(modules);
        if (module.alwaysListening()) Paradox.EVENTS.subscribe(module);
    }

    public List<Module> modules() {
        return modulesSnapshot;
    }

    public void notifyToggle(Module module, boolean enabled) {
        if (!initialized || modulesByClass.get(module.getClass()) != module) return;
        Paradox.EVENTS.post(new ModuleToggleEvent(module, enabled));
    }

    public void handleBinds(Minecraft client) {
        for (Module module : modulesSnapshot) {
            BindState state = bindStates.computeIfAbsent(module, ignored -> new BindState());
            int key = module.bind().get();
            boolean pressed = module.bind().isDown();
            BindModeSetting.Mode mode = module.bindMode().get();
            updateBind(module, state, key, mode, pressed, canUseBind(client, module));
        }
    }

    private static boolean canUseBind(Minecraft client, Module module) {
        if (!client.getWindow().isFocused()) {
            return false;
        }
        var screen = client.gui.screen();
        if (screen == null) {
            return true;
        }
        if (module instanceof ClickGuiModule gui) {
            return !gui.isBinding() && (module.isEnabled() || gui.canOpenOver(screen));
        }
        return !(screen instanceof ClickGuiScreen gui && gui.isTyping() || screen instanceof ChatScreen
                || screen instanceof AbstractSignEditScreen || screen instanceof BookEditScreen
                || screen.getFocused() instanceof EditBox);
    }

    private static void updateBind(Module module, BindState state, int key,
                                   BindModeSetting.Mode mode, boolean pressed, boolean canUseBind) {
        if (state.key != key || state.mode != mode) {
            if (state.holdingBind) {
                module.setEnabled(false);
            }
            state.holdingBind = false;
            state.key = key;
            state.mode = mode;
            state.pressed = pressed;
            return;
        }
        if (mode == BindModeSetting.Mode.HOLD) {
            if (state.holdingBind && (!pressed || !Minecraft.getInstance().getWindow().isFocused())) {
                state.holdingBind = false;
                module.setEnabled(false);
            } else if (canUseBind && pressed && !state.pressed) {
                state.holdingBind = true;
                module.setEnabled(true);
            }
        } else if (canUseBind && pressed && !state.pressed) {
            module.toggle();
        }
        state.pressed = pressed;
    }

    private static class BindState {
        int key = -1;
        BindModeSetting.Mode mode = BindModeSetting.Mode.TOGGLE;
        boolean pressed;
        boolean holdingBind;
    }

    public void tick(Minecraft client) {
        if (client.player == null) {
            return;
        }
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.tick(client);
            }
        }
    }

    public void tickInput(Minecraft client) {
        if (client.player == null) {
            return;
        }
        for (Module module : modulesSnapshot) {
            if (module.isEnabled()) {
                module.tickInput(client);
            }
        }
    }
}
