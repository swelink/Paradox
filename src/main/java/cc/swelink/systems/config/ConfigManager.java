package cc.swelink.systems.config;

import cc.swelink.settings.BindModeSetting;
import cc.swelink.settings.BindSetting;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.ColorSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.settings.ModeSetting;
import cc.swelink.settings.Setting;
import cc.swelink.systems.hud.PlacedHudElement;
import cc.swelink.systems.modules.movement.ElytraBoostModule;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.ModuleManager;
import cc.swelink.systems.modules.client.ClickGuiModule;
import cc.swelink.systems.modules.render.WatermarkModule;
import cc.swelink.systems.modules.render.DynamicIslandModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final ModuleManager manager;
    private final Path directory;

    public ConfigManager(ModuleManager manager, Path gameDirectory) {
        this.manager = manager;
        directory = gameDirectory.resolve("Paradox").resolve("configs").toAbsolutePath().normalize();
    }

    public void init() {
        try {
            Files.createDirectories(directory);
        } catch (IOException error) {
            LoggerFactory.getLogger(ConfigManager.class).error("Could not create the Paradox config directory", error);
        }
    }

    public List<String> list() throws IOException {
        Files.createDirectories(directory);
        try (var files = Files.list(directory)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
    }

    public void save(String name) throws IOException {
        save(file(name));
    }

    public void autosave() {
        try {
            save(autosaveFile());
        } catch (IOException | RuntimeException error) {
            LoggerFactory.getLogger(ConfigManager.class).error("Could not autosave the Paradox config", error);
        }
    }

    public void loadAutosave() {
        if (!Files.isRegularFile(autosaveFile(), LinkOption.NOFOLLOW_LINKS)) return;
        try {
            load(autosaveFile(), false);
        } catch (IOException | RuntimeException error) {
            LoggerFactory.getLogger(ConfigManager.class).error("Could not load the Paradox autosave", error);
        }
    }

    private Path autosaveFile() {
        return directory.getParent().resolve("autosave.json");
    }

    private void save(Path target) throws IOException {
        var root = new JsonObject();
        root.addProperty("version", 1);
        var modules = new JsonObject();
        for (Module module : manager.modules()) {
            var data = new JsonObject();
            data.addProperty("enabled", module.isEnabled());
            var settings = new JsonObject();
            for (var entry : settings(module).entrySet()) {
                Object value = entry.getValue().get();
                settings.add(entry.getKey(), value instanceof Boolean flag ? new JsonPrimitive(flag)
                        : value instanceof Number number ? new JsonPrimitive(number) : new JsonPrimitive(value.toString()));
            }
            data.add("settings", settings);
            if (module instanceof PlacedHudElement hud) data.add("hud", hud.placement().save());
            modules.add(module.getClass().getSimpleName(), data);
        }
        root.add("modules", modules);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".saving-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(root));
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void load(String name) throws IOException {
        load(file(name), true);
    }

    private void load(Path path, boolean announce) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Config not found");
        if (Files.size(path) > 1_048_576) throw new IOException("Config is too large");
        List<LoadPlan> plans = new ArrayList<>();
        try {
            var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (integer(root.get("version")) != 1) throw new IllegalArgumentException("Unsupported config version");
            var modules = root.getAsJsonObject("modules");
            for (Module module : manager.modules()) {
                boolean renamedIsland = modules.has("DynamicIslandModule");
                boolean legacy = !modules.has("StatsModule") && !renamedIsland;
                String key = module.getClass().getSimpleName();
                if (module instanceof WatermarkModule) key = modules.has("StatsModule") ? "StatsModule" : "WatermarkModule";
                if (module instanceof DynamicIslandModule) key = renamedIsland ? "DynamicIslandModule" : "WatermarkModule";
                if (!modules.has(key) && module instanceof ElytraBoostModule && modules.has("ElytraFlyModule")) key = "ElytraFlyModule";
                if (!modules.has(key)) continue;
                var data = modules.getAsJsonObject(key);
                boolean enabled = bool(data.get("enabled"));
                boolean island = legacy && module instanceof DynamicIslandModule;
                var savedSettings = (island && modules.has("NotificationsModule")
                        ? modules.getAsJsonObject("NotificationsModule") : data).getAsJsonObject("settings");
                List<Runnable> changes = new ArrayList<>();
                for (var entry : settings(module).entrySet()) {
                    if (savedSettings.has(entry.getKey())) changes.add(change(entry.getValue(), savedSettings.get(entry.getKey())));
                }
                if (module instanceof PlacedHudElement hud && data.has("hud")) {
                    var placement = data.getAsJsonObject("hud");
                    float horizontal = number(placement.get("horizontal"), 0, 1);
                    float vertical = number(placement.get("vertical"), -1, 1);
                    float size = number(placement.get("size"), 0.5f, 2.5f);
                    boolean centered = bool(placement.get("centered"));
                    changes.add(() -> hud.placement().restore(horizontal, vertical, size, centered));
                }
                plans.add(new LoadPlan(module, enabled, changes));
            }
        } catch (RuntimeException error) {
            throw new IOException("Invalid config: " + error.getMessage(), error);
        }
        var notifications = manager.get(DynamicIslandModule.class);
        notifications.mute(true);
        try {
            for (var plan : plans) if (!(plan.module() instanceof ClickGuiModule)) plan.module().setEnabled(false);
            for (var plan : plans) for (var change : plan.changes()) change.run();
            for (var plan : plans) if (!(plan.module() instanceof ClickGuiModule)) plan.module().setEnabled(plan.enabled());
        } finally {
            notifications.mute(false);
        }
        if (!announce) return;
        String file = path.getFileName().toString();
        notifications.configLoaded(file.substring(0, file.length() - 5));
    }

    public void delete(String name) throws IOException {
        if (!Files.deleteIfExists(file(name))) throw new IOException("Config not found");
    }

    private Path file(String input) {
        String name = input.strip();
        if (name.endsWith(".json")) name = name.substring(0, name.length() - 5);
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9 _.-]{0,63}") || name.endsWith(".")) {
            throw new IllegalArgumentException("Use 1–64 letters, numbers, spaces, dots, dashes or underscores");
        }
        return directory.resolve(name + ".json");
    }

    private static Map<String, Setting<?>> settings(Module module) {
        Map<String, Setting<?>> result = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (var setting : module.settings()) {
            String type = setting instanceof BindModeSetting ? "bind-mode" : setting instanceof BindSetting ? "bind"
                    : setting instanceof ColorSetting ? "color" : setting instanceof IntSetting ? "number"
                    : setting instanceof BooleanSetting ? "boolean" : setting instanceof ModeSetting ? "mode" : null;
            if (type == null) continue;
            String key = type + ":" + setting.name();
            int occurrence = counts.merge(key, 1, Integer::sum);
            result.put(key + ":" + occurrence, setting);
        }
        return result;
    }

    private static Runnable change(Setting<?> setting, JsonElement value) {
        if (setting instanceof BooleanSetting flag) {
            boolean enabled = bool(value);
            return () -> flag.setValue(enabled);
        }
        if (setting instanceof BindModeSetting bindMode) {
            var mode = BindModeSetting.Mode.valueOf(value.getAsString());
            return () -> bindMode.select(mode);
        }
        if (setting instanceof ModeSetting mode) {
            String choice = value.getAsString();
            if (!mode.choices().contains(choice)) return () -> { };
            return () -> mode.selectChoice(choice);
        }
        int number = integer(value);
        if (setting instanceof ColorSetting color) {
            float[] hsv = Color.RGBtoHSB((number >>> 16) & 255, (number >>> 8) & 255, number & 255, null);
            return () -> color.select(hsv[0], hsv[1], hsv[2]);
        }
        if (setting instanceof IntSetting slider) return () -> slider.restore(number);
        if (setting instanceof BindSetting bind) {
            if (number < -33) throw new IllegalArgumentException("Invalid keybind");
            return () -> bind.bind(number);
        }
        throw new IllegalArgumentException("Unsupported setting");
    }

    private static int integer(JsonElement value) {
        return value.getAsBigDecimal().intValueExact();
    }

    private static boolean bool(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Expected a boolean");
        }
        return value.getAsBoolean();
    }

    private static float number(JsonElement value, float minimum, float maximum) {
        float number = value.getAsFloat();
        if (!Float.isFinite(number) || number < minimum || number > maximum) {
            throw new IllegalArgumentException("Invalid placement value");
        }
        return number;
    }

    private record LoadPlan(Module module, boolean enabled, List<Runnable> changes) {
    }
}
