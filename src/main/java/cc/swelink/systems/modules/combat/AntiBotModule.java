package cc.swelink.systems.modules.combat;

import cc.swelink.events.AttackEvent;
import cc.swelink.events.EventHandler;
import cc.swelink.events.PreTickEvent;
import cc.swelink.settings.BooleanSetting;
import cc.swelink.settings.IntSetting;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.utils.player.FakePlayerEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.regex.Pattern;

public final class AntiBotModule extends Module {
    private static final Pattern REAL_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final double CLOSE_RANGE = 20;
    private static final long PING_GRACE = 40_000_000_000L;
    private static final int GROUND_SPOOF_LIMIT = 20;
    private static final int YOUNG_TICKS = 20;
    private final IntSetting scoreNeeded = addSetting(new IntSetting("Score needed", 2, 1, 6));
    private final BooleanSetting missingFromTab = addSetting(new BooleanSetting("Missing from tab", true));
    private final BooleanSetting copiedName = addSetting(new BooleanSetting("Copied name", true));
    private final BooleanSetting impossibleStats = addSetting(new BooleanSetting("Impossible stats", true));
    private final BooleanSetting noGamemode = addSetting(new BooleanSetting("No gamemode", true));
    private final BooleanSetting oddName = addSetting(new BooleanSetting("Odd name", true));
    private final BooleanSetting groundSpoof = addSetting(new BooleanSetting("Ground spoofing", true));
    private final BooleanSetting noSkin = addSetting(new BooleanSetting("No skin", false));
    private final BooleanSetting zeroPing = addSetting(new BooleanSetting("Zero ping", false));
    private final BooleanSetting neverSwings = addSetting(new BooleanSetting("Never swings", false));
    private final BooleanSetting untouched = addSetting(new BooleanSetting("Untouched", false));
    private final BooleanSetting justSpawned = addSetting(new BooleanSetting("Just spawned", false));
    private final BooleanSetting staysClose = addSetting(new BooleanSetting("Stays close", false));
    private final AntiBotTracker tracker = new AntiBotTracker();
    private ClientLevel level;
    private ClientPacketListener connection;

    public AntiBotModule() {
        super("AntiBot", "Filters bots from real players", Category.COMBAT);
    }

    private boolean ready(Minecraft client) {
        if (level != client.level || connection != client.getConnection()) {
            tracker.clear();
            level = client.level;
            connection = client.getConnection();
        }
        return isEnabled() && level != null && connection != null;
    }

    @Override
    public void tick(Minecraft client) {
        if (!ready(client) || client.player == null) return;
        for (Player player : level.players()) {
            if (player == client.player) continue;
            if (client.player.distanceTo(player) > CLOSE_RANGE) tracker.body(player.getId(), player.getUUID()).wandered = true;
        }
    }

    public boolean isBot(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        if (!ready(client) || client.player == null || !(entity instanceof Player player)
                || player == client.player || player.level() != level || player instanceof FakePlayerEntity) return false;
        return score(player) >= scoreNeeded.get();
    }

    private int score(Player player) {
        UUID id = player.getUUID();
        String name = player.getGameProfile().name();
        var info = connection.getPlayerInfo(id);
        var listing = tracker.listing(id);
        var body = tracker.body(player.getId(), id);
        int score = 0;
        if (missingFromTab.get() && (info == null || !connection.getListedOnlinePlayers().contains(info))) score += 2;
        if (copiedName.get() && sharesName(name, id)) score += 2;
        if (impossibleStats.get() && impossible(player)) score += 2;
        if (noGamemode.get() && (info == null || listing != null && !listing.gameMode)) score++;
        if (oddName.get() && !REAL_NAME.matcher(name).matches()) score++;
        if (groundSpoof.get() && body.groundSpoofs >= GROUND_SPOOF_LIMIT) score++;
        if (noSkin.get() && listing != null && !listing.skin) score++;
        if (zeroPing.get() && listing != null && listing.ping == 0 && System.nanoTime() - listing.listedAt > PING_GRACE) score++;
        if (neverSwings.get() && !body.swung) score++;
        if (untouched.get() && !body.hitByYou) score++;
        if (justSpawned.get() && player.tickCount < YOUNG_TICKS) score++;
        if (staysClose.get() && !body.wandered) score++;
        return score;
    }

    private static boolean impossible(Player player) {
        float pitch = player.getXRot();
        float health = player.getHealth();
        return !Float.isFinite(pitch) || Math.abs(pitch) > 90
                || !Float.isFinite(health) || health > player.getMaxHealth()
                || player.getAttributeValue(Attributes.SCALE) != 1
                || player.getId() < 0;
    }

    private boolean sharesName(String name, UUID id) {
        for (var info : connection.getOnlinePlayers()) {
            if (info.getProfile().name().equalsIgnoreCase(name) && !info.getProfile().id().equals(id)) return true;
        }
        return false;
    }

    public void playerInfo(ClientboundPlayerInfoUpdatePacket packet) {
        if (!ready(Minecraft.getInstance())) return;
        var actions = packet.actions();
        long now = System.nanoTime();
        if (actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
            boolean gameMode = actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE);
            for (var entry : packet.entries()) {
                if (entry.profile() == null) continue;
                tracker.listed(entry.profileId(), !entry.profile().properties().isEmpty(), gameMode, entry.latency(), now);
            }
            return;
        }
        for (var entry : packet.entries()) {
            if (actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE)) tracker.gameMode(entry.profileId());
            if (actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY)) tracker.ping(entry.profileId(), entry.latency());
        }
    }

    public void movement(ClientboundMoveEntityPacket packet) {
        if (!ready(Minecraft.getInstance()) || !packet.hasPosition()) return;
        if (!(packet.getEntity(level) instanceof Player player)) return;
        var codec = player.getPositionCodec();
        double rise = packet.getPositionDelta().decode(codec).endPosition().y - codec.getBase().y;
        tracker.landed(player.getId(), player.getUUID(), packet.isOnGround(), rise > 1.0e-7);
    }

    public void packet(Packet<?> packet) {
        if (!ready(Minecraft.getInstance())) return;
        switch (packet) {
            case ClientboundSwingAnimationPacket swing -> {
                var body = tracker.existing(swing.entityId());
                if (body != null) body.swung = true;
                else if (level.getEntity(swing.entityId()) instanceof Player player) tracker.body(player.getId(), player.getUUID()).swung = true;
            }
            case ClientboundRemoveEntitiesPacket remove -> {
                for (int id : remove.entityIds()) tracker.forgetBody(id);
            }
            case ClientboundPlayerInfoRemovePacket remove -> {
                for (UUID id : remove.profileIds()) tracker.forgetListing(id);
            }
            default -> { }
        }
    }

    public void attacked(int entityId) {
        if (!ready(Minecraft.getInstance())) return;
        if (level.getEntity(entityId) instanceof Player player) tracker.body(entityId, player.getUUID()).hitByYou = true;
    }

    @Override
    protected void onEnable() {
        tracker.clear();
    }

    @Override
    protected void onDisable() {
        tracker.clear();
    }

    @Override
    protected boolean alwaysListening() {
        return true;
    }

    @EventHandler(priority = 10)
    private void onPreTick(PreTickEvent event) {
        ready(event.client());
    }

    @EventHandler(priority = 40)
    private void onAttack(AttackEvent event) {
        attacked(event.entityId());
    }
}
