package cc.swelink.systems.modules.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class AntiBotTracker {
    static final class Body {
        UUID uuid;
        boolean hitByYou;
        boolean swung;
        boolean wandered;
        int groundSpoofs;
    }

    static final class Listing {
        boolean skin;
        boolean gameMode;
        int ping;
        long listedAt;
    }

    private final Map<Integer, Body> bodies = new HashMap<>();
    private final Map<UUID, Listing> listings = new HashMap<>();

    Body body(int id, UUID uuid) {
        Body body = bodies.get(id);
        if (body == null || body.uuid != null && !body.uuid.equals(uuid)) {
            body = new Body();
            bodies.put(id, body);
        }
        body.uuid = uuid;
        return body;
    }

    Body existing(int id) {
        return bodies.get(id);
    }

    void landed(int id, UUID uuid, boolean claimsGround, boolean rose) {
        Body body = body(id, uuid);
        if (claimsGround && rose) body.groundSpoofs = Math.min(40, body.groundSpoofs + 2);
        else if (body.groundSpoofs > 0) body.groundSpoofs--;
    }

    void listed(UUID id, boolean skin, boolean gameMode, int ping, long now) {
        Listing listing = listings.computeIfAbsent(id, ignored -> new Listing());
        listing.skin = skin;
        listing.gameMode = gameMode;
        listing.ping = ping;
        listing.listedAt = now;
    }

    void gameMode(UUID id) {
        Listing listing = listings.get(id);
        if (listing != null) listing.gameMode = true;
    }

    void ping(UUID id, int ping) {
        Listing listing = listings.get(id);
        if (listing != null) listing.ping = ping;
    }

    Listing listing(UUID id) {
        return listings.get(id);
    }

    void forgetBody(int id) {
        bodies.remove(id);
    }

    void forgetListing(UUID id) {
        listings.remove(id);
        bodies.values().removeIf(body -> id.equals(body.uuid));
    }

    void clear() {
        bodies.clear();
        listings.clear();
    }
}
