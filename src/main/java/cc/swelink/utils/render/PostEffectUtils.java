package cc.swelink.utils.render;

import cc.swelink.mixin.PostChainAccessor;
import cc.swelink.mixin.PostPassAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostChain;
import org.lwjgl.system.MemoryStack;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

public final class PostEffectUtils {
    private static final Map<GpuBuffer, float[]> LAST_VALUES = new WeakHashMap<>();
    private PostEffectUtils() {
    }

    public static void writeUniform(PostChain chain, String name, float... values) {
        if (chain == null) {
            return;
        }
        var passes = ((PostChainAccessor) chain).paradox$passes();
        if (passes.isEmpty()) {
            return;
        }
        for (var pass : passes) {
            var uniforms = ((PostPassAccessor) pass).paradox$uniforms();
            var uniform = uniforms.get(name);
            if (uniform == null || Arrays.equals(LAST_VALUES.get(uniform), values)) {
                continue;
            }
            try (var stack = MemoryStack.stackPush()) {
                var data = stack.malloc(values.length * Float.BYTES);
                for (float value : values) {
                    data.putFloat(value);
                }
                data.flip();
                var device = RenderSystem.getDevice();
                if ((uniform.usage() & GpuBuffer.USAGE_COPY_DST) == 0) {
                    var replacement = device.createBuffer(() -> "Paradox " + name,
                            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, data);
                    uniforms.put(name, replacement);
                    LAST_VALUES.remove(uniform);
                    uniform.close();
                    uniform = replacement;
                } else {
                    device.createCommandEncoder().writeToBuffer(uniform.slice(), data);
                }
            }
            LAST_VALUES.put(uniform, values.clone());
        }
    }
}
