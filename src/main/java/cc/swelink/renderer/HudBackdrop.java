package cc.swelink.renderer;

import java.util.ArrayList;
import java.util.List;

public final class HudBackdrop {
    private static final List<Shadow> SHADOWS = List.of(
            new Shadow(12, 0x94000000),
            new Shadow(4, 0x88000000));
    private static final float PADDING = 56;
    private static final float DOCKED_OVERHANG = 40;

    private HudBackdrop() {
    }

    public record Background(float x, float y, float width, float height, float radius, float[] corners) {
        public Background(float x, float y, float width, float height, float radius) {
            this(x, y, width, height, radius, null);
        }

        public Background withCorners(float[] value) {
            return new Background(x, y, width, height, radius, value);
        }
    }

    public static Background docked(float x, float y, float width, float height, float radius, int edges) {
        float left = x - ((edges & BlurRenderer.LEFT) != 0 ? DOCKED_OVERHANG : 0);
        float top = y - ((edges & BlurRenderer.TOP) != 0 ? DOCKED_OVERHANG : 0);
        float right = x + width + ((edges & BlurRenderer.RIGHT) != 0 ? DOCKED_OVERHANG : 0);
        float bottom = y + height + ((edges & BlurRenderer.BOTTOM) != 0 ? DOCKED_OVERHANG : 0);
        return new Background(left, top, right - left, bottom - top, radius);
    }

    public static void draw(Renderer2D renderer, List<Background> backgrounds) {
        for (Background background : backgrounds) {
            draw(renderer, background, backgrounds);
        }
    }

    public static void draw(Renderer2D renderer, Background background, List<Background> cutouts) {
        if (!(background.width > 0 && background.height > 0)) {
            return;
        }
        BlurRenderer.group(renderer);
        List<Region> outside = List.of(new Region(background.x - PADDING, background.y - PADDING,
                background.x + background.width + PADDING, background.y + background.height + PADDING));
        for (Background cutout : cutouts) {
            if (cutout == background || !(cutout.width > 0 && cutout.height > 0)) {
                continue;
            }
            float corner = cornerSize(cutout);
            for (Region shape : List.of(
                    new Region(cutout.x, cutout.y + corner, cutout.x + cutout.width, cutout.y + cutout.height - corner),
                    new Region(cutout.x + corner, cutout.y, cutout.x + cutout.width - corner, cutout.y + cutout.height))) {
                if (shape.left >= shape.right || shape.top >= shape.bottom) continue;
                var remaining = new ArrayList<Region>();
                for (Region region : outside) {
                    region.subtract(shape, remaining);
                }
                outside = remaining;
            }
        }
        for (Region region : outside) {
            for (Shadow shadow : SHADOWS) {
                if (background.corners != null) {
                    renderer.sdf.clippedBackdropShadow(background.x, background.y, background.width, background.height,
                            background.corners, shadow.sigma, shadow.color, region.left, region.top, region.right, region.bottom);
                } else {
                    renderer.sdf.clippedBackdropShadow(background.x, background.y, background.width, background.height,
                            background.radius, shadow.sigma, shadow.color, region.left, region.top, region.right, region.bottom);
                }
            }
        }
    }

    private static float cornerSize(Background background) {
        if (background.corners == null) return SdfPipeline.corner(background.radius, background.width, background.height);
        float corner = 0;
        for (float value : background.corners) corner = Math.max(corner, value);
        return Math.clamp(corner, 0, Math.min(background.width, background.height) * 0.5f);
    }

    private record Shadow(float sigma, int color) {
    }

    private record Region(float left, float top, float right, float bottom) {
        void subtract(Region cutout, List<Region> result) {
            float x1 = Math.max(left, cutout.left);
            float y1 = Math.max(top, cutout.top);
            float x2 = Math.min(right, cutout.right);
            float y2 = Math.min(bottom, cutout.bottom);
            if (x1 >= x2 || y1 >= y2) {
                result.add(this);
                return;
            }
            if (top < y1) result.add(new Region(left, top, right, y1));
            if (y2 < bottom) result.add(new Region(left, y2, right, bottom));
            if (left < x1) result.add(new Region(left, y1, x1, y2));
            if (x2 < right) result.add(new Region(x2, y1, right, y2));
        }
    }
}
