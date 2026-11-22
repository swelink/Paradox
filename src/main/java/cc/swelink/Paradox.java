package cc.swelink;

import cc.swelink.events.EventBus;
import cc.swelink.systems.modules.ModuleManager;
import cc.swelink.systems.config.ConfigManager;
import cc.swelink.systems.friends.Friends;
import cc.swelink.utils.combat.CombatTargets;
import cc.swelink.utils.combat.CriticalHitTiming;
import cc.swelink.utils.rotation.RotationManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class Paradox implements ClientModInitializer {
    public static final String MOD_ID = "paradox";
    public static final EventBus EVENTS = new EventBus();
    public static final ModuleManager MODULES = new ModuleManager();
    public static final ConfigManager CONFIGS = new ConfigManager(MODULES, FabricLoader.getInstance().getGameDir());
    public static final Friends FRIENDS = new Friends(FabricLoader.getInstance().getGameDir());
    public static final RotationManager ROTATIONS = new RotationManager();
    public static final CombatTargets TARGETS = new CombatTargets();

    @Override
    public void onInitializeClient() {
        MODULES.init();
        CONFIGS.init();
        FRIENDS.init();
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> CONFIGS.loadAutosave());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CONFIGS.autosave());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CONFIGS.autosave());
        ClientTickEvents.START_CLIENT_TICK.register(this::startTick);
        ClientTickEvents.END_CLIENT_TICK.register(this::endTick);
    }

    private void startTick(Minecraft client) {
        CriticalHitTiming.resetSprint();
        ROTATIONS.beginInputTick();
        MODULES.beforeTick(client);
    }

    private void endTick(Minecraft client) {
        ROTATIONS.beginTick();
        MODULES.tick(client);
        ROTATIONS.apply(client);
        MODULES.afterTick(client);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
