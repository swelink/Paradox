package cc.swelink.renderer;

public enum Theme {
    PARADOX("Paradox", 0xFF8B3DFF, 0xFF5B21B6),
    HAUNT("Haunt", 0xFFFF7518, 0xFFB8420F),
    COFFEE("Coffee", 0xFFC69263, 0xFF8B5A3C),
    SAKURA("Sakura", 0xFFFF6FA8, 0xFFFFFFFF),
    GOON("Goon", 0xFFFFFFFF, 0xFFFFF3C4),
    CANDY("Candy", 0xFFFF2D95, 0xFFFF6A13),
    FROST("Frost", 0xFF7DD3FC, 0xFF1D4ED8);

    private static final int BASE = 0xFF101010;
    private static final int TEXT = 0xFFF2F1F5;

    public final String title;
    public final int primary;
    public final int secondary;
    public final int header;
    public final int raised;
    public final int disabled;
    public final int muted;
    public final int text;
    public final int hudPanel;
    public final int hudHeader;

    Theme(String title, int primary, int secondary) {
        int base = BASE;
        int text = TEXT;
        this.title = title;
        this.primary = primary;
        this.secondary = secondary;
        this.text = text;
        header = base;
        raised = layer(base, text, 0.18f);
        disabled = layer(base, text, 0.4f);
        muted = layer(base, text, 0.7f);
        hudPanel = base & 0xFFFFFF | 0xDC000000;
        hudHeader = layer(base, text, 0.04f);
    }

    private static int layer(int base, int text, float amount) {
        int color = 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8) {
            int from = base >>> shift & 255;
            int to = text >>> shift & 255;
            color |= Math.round(from + (to - from) * amount) << shift;
        }
        return color;
    }

    public static Theme named(String title) {
        for (Theme theme : values()) if (theme.title.equals(title)) return theme;
        return PARADOX;
    }
}
