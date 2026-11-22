package cc.swelink.utils.time;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.world.TimerModule;

public final class GameTime {
    private static long lastReal = System.nanoTime();
    private static long scaled = lastReal;
    private static double remainder;

    private GameTime() {
    }

    public static synchronized long nanos() {
        long now = System.nanoTime();
        double advanced = (now - lastReal) * Paradox.MODULES.get(TimerModule.class).multiplier() + remainder;
        long whole = (long) advanced;
        remainder = advanced - whole;
        scaled += whole;
        lastReal = now;
        return scaled;
    }

}
