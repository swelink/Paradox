package cc.swelink.systems.modules.render;

import java.util.List;

final class RowShapes {
    private RowShapes() {
    }

    record Slot(float top, float bottom, float left, float right, float weight, boolean dockLeft, boolean dockRight) {
    }

    static float[] corners(List<Slot> slots, int index, float radius, float screenTop, float screenBottom) {
        return new float[] {
                corner(slots, index, -1, true, radius, screenTop),
                corner(slots, index, -1, false, radius, screenTop),
                corner(slots, index, 1, false, radius, screenBottom),
                corner(slots, index, 1, true, radius, screenBottom)
        };
    }

    private static float corner(List<Slot> slots, int index, int direction, boolean left, float radius, float screen) {
        Slot slot = slots.get(index);
        boolean docked = left ? slot.dockLeft() : slot.dockRight();
        float apart = docked ? 0 : radius;
        float reach = slot.bottom() - slot.top();
        float remaining = 1;
        float value = 0;
        for (int i = index + direction; i >= 0 && i < slots.size(); i += direction) {
            Slot other = slots.get(i);
            float share = remaining * other.weight();
            float step = left ? other.left() - slot.left() : slot.right() - other.right();
            float size = Math.min(radius, Math.abs(step) / 2);
            float gap = direction > 0 ? other.top() - slot.bottom() : slot.top() - other.bottom();
            float near = closeness(gap, reach);
            value += share * (step > 0 ? lerp(apart, size, near) : lerp(apart, -size, touching(near)));
            remaining -= share;
        }
        return lerp(apart, value + remaining * end(slot, direction, docked, radius, screen, reach), slot.weight());
    }

    private static float end(Slot slot, int direction, boolean docked, float radius, float screen, float reach) {
        boolean touching = !Float.isNaN(screen);
        if (docked) return touching ? 0 : -radius;
        if (!touching) return radius;
        return lerp(radius, -radius, touching(closeness(direction > 0 ? screen - slot.bottom() : slot.top() - screen, reach)));
    }

    private static float closeness(float gap, float reach) {
        return Math.clamp(1 - Math.abs(gap) / reach, 0, 1);
    }

    private static float touching(float closeness) {
        float squared = closeness * closeness;
        return squared * squared * squared;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }
}
