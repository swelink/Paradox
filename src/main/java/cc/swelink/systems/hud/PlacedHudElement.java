package cc.swelink.systems.hud;

import cc.swelink.utils.hud.HudPlacement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;

public interface PlacedHudElement extends HudElement {
    HudPlacement placement();
    boolean isEnabled();

    default float cornerRadius() {
        return 0;
    }

    default boolean visibleForDragging() {
        return true;
    }

    @Override
    default Bounds editorBounds() {
        return placement().bounds();
    }

    @Override
    default boolean mouseClicked(MouseButtonEvent event) {
        var client = Minecraft.getInstance();
        return isEnabled() && visibleForDragging() && client.player != null && !client.gui.hud.isHidden()
                && HudPlacement.canDrag(client) && placement().mouseClicked(event);
    }

    @Override
    default boolean mouseDragged(MouseButtonEvent event) {
        return placement().mouseDragged(event);
    }

    @Override
    default boolean mouseReleased(MouseButtonEvent event) {
        return placement().mouseReleased(event);
    }

    @Override
    default void stopDragging() {
        placement().stopDragging();
    }
}
