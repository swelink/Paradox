package cc.swelink.settings;

import java.util.List;

public interface ChoiceSetting {
    List<String> choices();

    String selectedChoice();

    void selectChoice(String choice);
}
