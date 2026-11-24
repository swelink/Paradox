package cc.swelink.utils.render;

import net.minecraft.client.renderer.PostChain;

public final class ShaderKernels {
    private static final float[][] BLUR = new float[10][];

    static {
        for (int glow = 1; glow <= 10; glow++) {
            float[] samples = new float[15 * 4];
            float sigma = 1 + glow * 0.8f;
            float total = 1;
            for (int i = 1; i <= 27; i += 2) {
                float a = i, b = i + 1;
                float wa = (float) Math.exp(-0.5f * a * a / (sigma * sigma));
                float wb = (float) Math.exp(-0.5f * b * b / (sigma * sigma));
                float weight = wa + wb;
                int index = (i + 1) / 2 * 4;
                samples[index] = (a * wa + b * wb) / Math.max(weight, 0.000001f);
                samples[index + 1] = weight;
                total += weight * 2;
            }
            samples[0] = total;
            BLUR[glow - 1] = samples;
        }
    }

    public static void configureBlur(PostChain chain, int glow) {
        PostEffectUtils.writeUniform(chain, "BlurKernel", BLUR[Math.clamp(glow, 1, 10) - 1]);
    }

    private ShaderKernels() {
    }
}
