package cc.swelink.events;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;

public final class InputEvent {
    private final Minecraft client;
    private Input input;

    public InputEvent(Minecraft client, Input input) {
        this.client = client;
        this.input = input;
    }

    public Minecraft client() {
        return client;
    }

    public Input input() {
        return input;
    }

    public void input(Input input) {
        this.input = input;
    }
}
