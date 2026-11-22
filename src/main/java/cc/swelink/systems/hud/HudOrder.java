package cc.swelink.systems.hud;

import cc.swelink.systems.modules.Module;

import java.util.ArrayList;
import java.util.List;

public final class HudOrder {
    private static final List<HudElement> RAISED = new ArrayList<>();

    private HudOrder() {
    }

    public static void raise(HudElement element) {
        RAISED.remove(element);
        RAISED.add(element);
    }

    public static List<HudElement> ordered(List<Module> modules) {
        var ordered = new ArrayList<HudElement>();
        for (Module module : modules) {
            if (module instanceof HudElement hud && !RAISED.contains(hud)) ordered.add(hud);
        }
        for (HudElement hud : RAISED) {
            if (hud instanceof Module module && modules.contains(module)) ordered.add(hud);
        }
        return ordered;
    }
}
