package cc.swelink.settings;

import java.util.List;

public class BindModeSetting extends Setting<BindModeSetting.Mode> implements ChoiceSetting {
    public enum Mode {
        TOGGLE, HOLD
    }

    public BindModeSetting() {
        super("Mode", Mode.TOGGLE);
    }

    public void select(Mode mode) {
        set(mode);
    }

    @Override
    public List<String> choices() {
        return List.of("Toggle", "Hold");
    }

    @Override
    public String selectedChoice() {
        return displayValue();
    }

    @Override
    public void selectChoice(String choice) {
        if (choice.equals("Toggle")) {
            select(Mode.TOGGLE);
        } else if (choice.equals("Hold")) {
            select(Mode.HOLD);
        }
    }

    @Override
    public void cycle() {
        set(get() == Mode.TOGGLE ? Mode.HOLD : Mode.TOGGLE);
    }

    @Override
    public String displayValue() {
        return get() == Mode.TOGGLE ? "Toggle" : "Hold";
    }
}
