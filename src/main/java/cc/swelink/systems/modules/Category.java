package cc.swelink.systems.modules;

import cc.swelink.Paradox;

import net.minecraft.resources.Identifier;

public enum Category {
    RENDER("Visuals", "render"),
    COMBAT("Combat", "combat"),
    PLAYER("Player", "player"),
    CLIENT("Client", "client"),
    MOVEMENT("Movement", "movement"),
    CONFIG("Config", null);

    private final String title;
    private final Identifier icon;

    Category(String title, String icon) {
        this.title = title;
        this.icon = icon == null ? null : Paradox.id("icons/clickgui/" + icon + ".png");
    }

    public String title() {
        return title;
    }

    public Identifier icon() {
        return icon;
    }
}
