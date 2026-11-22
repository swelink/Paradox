package cc.swelink.systems.friends;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.player.Player;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class Friends {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Map<String, String> names = new TreeMap<>();

    public Friends(Path gameDirectory) {
        file = gameDirectory.resolve("Paradox").resolve("friends.json").toAbsolutePath().normalize();
    }

    public void init() {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            if (Files.size(file) > 1_048_576) throw new IOException("Friends file is too large");
            var friends = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("friends");
            for (var entry : friends) add(entry.getAsString(), false);
        } catch (IOException | RuntimeException error) {
            LoggerFactory.getLogger(Friends.class).error("Could not load the Paradox friends list", error);
        }
    }

    public synchronized boolean isFriend(Player player) {
        return player != null && names.containsKey(key(player.getGameProfile().name()));
    }

    public synchronized List<String> list() {
        return new ArrayList<>(names.values());
    }

    public synchronized boolean toggle(Player player) {
        String name = player.getGameProfile().name();
        if (names.remove(key(name)) == null) {
            add(name, true);
            return true;
        }
        save();
        return false;
    }

    private void add(String name, boolean persist) {
        if (name == null || name.isBlank()) return;
        names.put(key(name), name);
        if (persist) save();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private void save() {
        var array = new JsonArray();
        names.values().forEach(array::add);
        var root = new JsonObject();
        root.add("friends", array);
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), ".friends-", ".tmp");
            try {
                Files.writeString(temporary, GSON.toJson(root));
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException error) {
            LoggerFactory.getLogger(Friends.class).error("Could not save the Paradox friends list", error);
        }
    }
}
