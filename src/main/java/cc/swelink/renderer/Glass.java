package cc.swelink.renderer;

public enum Glass {
    HUD(4, 4);

    final int downsample;
    final int passes;

    Glass(int downsample, int passes) {
        this.downsample = downsample;
        this.passes = passes;
    }

    BlurRenderer target() {
        return BlurRenderer.FROSTED_HUD;
    }
}
