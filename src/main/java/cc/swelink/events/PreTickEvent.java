package cc.swelink.events;

import net.minecraft.client.Minecraft;

public record PreTickEvent(Minecraft client) {
}
