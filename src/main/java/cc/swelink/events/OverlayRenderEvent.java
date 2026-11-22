package cc.swelink.events;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public record OverlayRenderEvent(Minecraft client, GuiGraphicsExtractor graphics) {
}
