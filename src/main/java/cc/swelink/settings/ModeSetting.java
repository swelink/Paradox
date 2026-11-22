package cc.swelink.settings;

import java.util.List;
import java.util.function.Consumer;

public class ModeSetting extends Setting<String> implements ChoiceSetting {
    private final List<String> choices;
    private Consumer<String> onChanged = ignored -> { };

    public ModeSetting(String name, String defaultValue, String... choices) {
        super(name, defaultValue);
        this.choices = choices.length == 0 ? List.of(defaultValue) : List.of(choices);
        if (!this.choices.contains(defaultValue)) {
            set(this.choices.getFirst());
        }
    }

    public ModeSetting onChanged(Consumer<String> listener) {
        onChanged = listener;
        return this;
    }

    @Override
    public List<String> choices() {
        return choices;
    }

    @Override
    public String selectedChoice() {
        return get();
    }

    @Override
    public void selectChoice(String choice) {
        if (choices.contains(choice) && !choice.equals(get())) {
            set(choice);
            onChanged.accept(choice);
        }
    }

    @Override
    public void cycle() {
        int nextIndex = (choices.indexOf(get()) + 1) % choices.size();
        selectChoice(choices.get(nextIndex));
    }

    @Override
    public String displayValue() {
        return get();
    }
}
