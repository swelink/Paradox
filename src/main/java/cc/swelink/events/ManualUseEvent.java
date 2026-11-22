package cc.swelink.events;

import net.minecraft.client.Minecraft;

public final class ManualUseEvent extends CancellableEvent {
    private final Minecraft client;

    public ManualUseEvent(Minecraft client) {
        this.client = client;
    }

    public Minecraft client() {
        return client;
    }
}
