package cc.swelink.events;

import net.minecraft.client.Minecraft;

public record AttackEvent(Minecraft client, int entityId) {
}
