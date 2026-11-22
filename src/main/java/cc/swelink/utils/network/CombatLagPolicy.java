package cc.swelink.utils.network;

public final class CombatLagPolicy {
    private CombatLagPolicy() {
    }

    public static boolean backtrack(double displayedDistance, double serverDistance, double minimum, double maximum) {
        return Double.isFinite(displayedDistance) && Double.isFinite(serverDistance)
                && displayedDistance >= minimum && displayedDistance <= maximum
                && serverDistance <= maximum + 1 && serverDistance + 0.02 >= displayedDistance;
    }

    public static boolean adaptiveBacktrack(double displayedDistance, double serverDistance, double reach,
                                            double minimum, double maximum) {
        return backtrack(displayedDistance, serverDistance, minimum, maximum)
                && displayedDistance <= reach && serverDistance > reach;
    }

    public static boolean fakeLag(double clientDistance, double serverDistance, boolean intersects) {
        return Double.isFinite(clientDistance) && Double.isFinite(serverDistance)
                && !intersects && serverDistance + 0.02 >= clientDistance;
    }
}
