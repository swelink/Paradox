package cc.swelink.utils.render;

import net.minecraft.client.renderer.PostChain;

public final class ShaderKernels {
    private static final float[][] OUTLINE = new float[5][];
    private static final float[][] BLUR = new float[10][];

    static {
        for (int width = 1; width <= 5; width++) {
            float[] samples = new float[97 * 4];
            int count = 0;
            for (int y = -width; y <= width; y++) {
                for (int x = -width; x <= width; x++) {
                    if (x == 0 && y == 0) continue;
                    float distance = (float) Math.sqrt(x * x + y * y);
                    if (distance > width + 0.5f) continue;
                    int index = ++count * 4;
                    samples[index] = x;
                    samples[index + 1] = y;
                    samples[index + 2] = Math.clamp(width + 0.5f - distance, 0, 1);
                }
            }
            samples[0] = count;
            OUTLINE[width - 1] = samples;
        }
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

    public static void configure(PostChain chain, int width, int glow) {
        PostEffectUtils.writeUniform(chain, "OutlineKernel", OUTLINE[Math.clamp(width, 1, 5) - 1]);
        PostEffectUtils.writeUniform(chain, "BlurKernel", BLUR[Math.clamp(glow, 1, 10) - 1]);
    }

    private ShaderKernels() {
    }
}
