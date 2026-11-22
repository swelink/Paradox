package cc.swelink.settings;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.sdl.SDLMouse;

public class BindSetting extends Setting<Integer> {
    public BindSetting() {
        super("Bind", -1);
    }

    public void bind(int key) {
        set(key);
    }

    public void bindMouse(int button) {
        if (button < 1 || button > 32) {
            return;
        }

        set(-button - 1);
    }

    public boolean isDown() {
        int keyCode = get();
        if (keyCode == -1) {
            return false;
        }
        if (keyCode >= 0) {
            return InputConstants.isKeyDown(keyCode);
        }
        return isMouseDown(SDLMouse.SDL_GetMouseState(null, null));
    }

    private boolean isMouseDown(int pressedButtons) {
        int button = -get() - 1;
        return (pressedButtons & (1 << (button - 1))) != 0;
    }

    @Override
    public void cycle() {
    }

    @Override
    public String displayValue() {
        if (get() == -1) {
            return "None";
        }
        if (get() < -1) {
            int button = -get() - 1;
            return switch (button) {
                case SDLMouse.SDL_BUTTON_LEFT -> "LMB";
                case SDLMouse.SDL_BUTTON_MIDDLE -> "MMB";
                case SDLMouse.SDL_BUTTON_RIGHT -> "RMB";
                case SDLMouse.SDL_BUTTON_X1 -> "MB4";
                case SDLMouse.SDL_BUTTON_X2 -> "MB5";
                default -> "Mouse " + button;
            };
        }
        return InputConstants.Type.KEYBOARD.getOrCreate(get()).getDisplayName().getString();
    }
}
