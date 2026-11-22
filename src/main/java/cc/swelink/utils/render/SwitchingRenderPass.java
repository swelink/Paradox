package cc.swelink.utils.render;

import com.mojang.renderpearl.api.commands.RenderPass;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public final class SwitchingRenderPass {
    private RenderPass target;
    private final RenderPass proxy;

    public SwitchingRenderPass(RenderPass target) {
        this.target = target;
        this.proxy = (RenderPass) Proxy.newProxyInstance(RenderPass.class.getClassLoader(), new Class<?>[] {RenderPass.class},
                (instance, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> instance == args[0];
                            case "hashCode" -> System.identityHashCode(instance);
                            default -> "SwitchingRenderPass[" + this.target + "]";
                        };
                    }
                    try {
                        return method.invoke(this.target, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    public RenderPass proxy() {
        return proxy;
    }

    public RenderPass target() {
        return target;
    }

    public void target(RenderPass target) {
        this.target = target;
    }
}
