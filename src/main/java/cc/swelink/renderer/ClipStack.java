package cc.swelink.renderer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

public final class ClipStack implements AutoCloseable {
    private final GuiGraphicsExtractor graphics;
    private int depth;
    private final List<Scope> scopes = new ArrayList<>();

    ClipStack(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
    }

    public Scope push(int x, int y, int width, int height) {
        graphics.enableScissor(x, y, x + Math.max(0, width), y + Math.max(0, height));
        return new Scope(++depth);
    }

    public Scope push(float x, float y, float width, float height) {
        return push(x, y, width, height, false);
    }

    private Scope push(float x, float y, float width, float height, boolean covering) {
        var pose = graphics.pose();
        var topLeft = new Vector2f(x, y).mulPosition(pose);
        var bottomRight = new Vector2f(x + Math.max(0, width), y + Math.max(0, height)).mulPosition(pose);
        float x1 = Math.min(topLeft.x, bottomRight.x);
        float y1 = Math.min(topLeft.y, bottomRight.y);
        float x2 = Math.max(topLeft.x, bottomRight.x);
        float y2 = Math.max(topLeft.y, bottomRight.y);
        int left = covering ? (int) Math.floor(x1) : Math.round(x1);
        int top = covering ? (int) Math.floor(y1) : Math.round(y1);
        int right = covering ? (int) Math.ceil(x2) : Math.round(x2);
        int bottom = covering ? (int) Math.ceil(y2) : Math.round(y2);
        GuiGraphicsAccess.of(graphics).paradox$pushScissor(new ScreenRectangle(left, top, right - left, bottom - top));
        return new Scope(++depth);
    }

    public boolean contains(int x, int y) {
        return graphics.containsPointInScissor(x, y);
    }

    @Override
    public void close() {
        while (depth > 0) {
            pop();
        }
    }

    private void pop() {
        graphics.disableScissor();
        scopes.removeLast().closed = true;
        depth--;
    }

    public final class Scope implements AutoCloseable {
        private final int depthAtOpen;
        private boolean closed;

        private Scope(int depthAtOpen) {
            this.depthAtOpen = depthAtOpen;
            scopes.add(this);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            while (depth >= depthAtOpen) {
                pop();
            }
        }
    }
}
