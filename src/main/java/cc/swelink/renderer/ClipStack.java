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
    private final List<QuadClip.Shape> shapes = new ArrayList<>();

    ClipStack(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
    }

    public Scope push(int x, int y, int width, int height) {
        graphics.enableScissor(x, y, x + Math.max(0, width), y + Math.max(0, height));
        shapes.add(screenShape(x, y, Math.max(0, width), Math.max(0, height), 0));
        return new Scope(++depth);
    }

    public Scope push(float x, float y, float width, float height) {
        return push(x, y, width, height, 0, false);
    }

    public Scope pushRounded(float x, float y, float width, float height, float radius) {
        return push(x, y, width, height, radius, false);
    }

    List<QuadClip.Shape> shapes() {
        return shapes.isEmpty() ? List.of() : List.copyOf(shapes);
    }

    private QuadClip.Shape screenShape(float x, float y, float width, float height, float radius) {
        var pose = graphics.pose();
        var topLeft = new Vector2f(x, y).mulPosition(pose);
        var bottomRight = new Vector2f(x + width, y + height).mulPosition(pose);
        float scale = (float) Math.sqrt(Math.abs(pose.m00() * pose.m11() - pose.m01() * pose.m10()));
        return new QuadClip.Shape(Math.min(topLeft.x, bottomRight.x), Math.min(topLeft.y, bottomRight.y),
                Math.max(topLeft.x, bottomRight.x), Math.max(topLeft.y, bottomRight.y),
                Math.min(radius * scale, Math.min(Math.abs(bottomRight.x - topLeft.x), Math.abs(bottomRight.y - topLeft.y)) / 2));
    }

    private Scope push(float x, float y, float width, float height, float radius, boolean covering) {
        var shape = screenShape(x, y, Math.max(0, width), Math.max(0, height), radius);
        shapes.add(shape);
        float x1 = shape.left();
        float y1 = shape.top();
        float x2 = shape.right();
        float y2 = shape.bottom();
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
        shapes.removeLast();
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
