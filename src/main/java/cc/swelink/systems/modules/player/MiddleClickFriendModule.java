package cc.swelink.systems.modules.player;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.Category;
import cc.swelink.systems.modules.Module;
import cc.swelink.systems.modules.render.DynamicIslandModule;
import cc.swelink.utils.player.Gameplay;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.sdl.SDLMouse;

public final class MiddleClickFriendModule extends Module {
    private boolean pressed;

    public MiddleClickFriendModule() {
        super("Friends", "Keybind to friend players", Category.PLAYER);
    }

    @Override
    public void tick(Minecraft client) {
        boolean down = (SDLMouse.SDL_GetMouseState(null, null) & (1 << (SDLMouse.SDL_BUTTON_MIDDLE - 1))) != 0;
        boolean clicked = down && !pressed;
        pressed = down;
        if (!clicked || client.player == null || !Gameplay.focused(client)
                || !(client.crosshairPickEntity instanceof Player target) || target == client.player) return;
        boolean added = Paradox.FRIENDS.toggle(target);
        Paradox.MODULES.get(DynamicIslandModule.class).friend(target.getGameProfile().name(), added);
    }

    @Override
    protected void onEnable() {
        pressed = true;
    }
}
