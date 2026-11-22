package cc.swelink.renderer;

import cc.swelink.Paradox;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.ARGB;

public final class CapeTexture {
    public static final int FULL_BRIGHT = 0xF000F0;

    private static final int SCALE = 12;
    private static final int WIDTH = 64 * SCALE;
    private static final int HEIGHT = 32 * SCALE;
    private static final int PAINT_SCALE = 32;
    private static final int PAINT_WIDTH = 64 * PAINT_SCALE;
    private static final int PAINT_HEIGHT = 32 * PAINT_SCALE;
    private static final int MIP_LEVELS = 6;
    private static final int TEXTURE_USAGE = 5;
    private static final int GLOW_RADIUS = PAINT_SCALE;
    private static final int GLOW_PADDING = GLOW_RADIUS * 3;
    private static final int RIM_RADIUS = PAINT_SCALE / 6;
    private static final float GLOW_STRENGTH = 1.3f;
    private static final String LOGO = "icons/paradox-logo.png";
    private static final int LOGO_SIZE = 1024;
    private static final int LOGO_X = 92;
    private static final int LOGO_Y = 253;
    private static final int LOGO_WIDTH = 840;
    private static final int LOGO_HEIGHT = 518;
    private static final float LOGO_FILL = 0.78f;
    private static final Map<Theme, ClientAsset.Texture> TEXTURES = new EnumMap<>(Theme.class);
    private static final Map<String, ClientAsset.Texture> IMAGES = new HashMap<>();
    private static final Map<String, Clip> CLIPS = new HashMap<>();
    private static final int FACE_WIDTH = 10 * SCALE;
    private static final int FACE_HEIGHT = 16 * SCALE;
    private static final int CLIP_BACKGROUND = 0xFF1A1A1A;

    private CapeTexture() {
    }

    public static ClientAsset.Texture texture(Theme theme) {
        return TEXTURES.computeIfAbsent(theme, CapeTexture::create);
    }

    public static ClientAsset.Texture image(String name) {
        return IMAGES.computeIfAbsent(name, CapeTexture::load);
    }

    public static ClientAsset.Texture clip(String name, int columns, int frames, long frameNanos) {
        Clip clip = CLIPS.computeIfAbsent(name, key -> loadClip(key, columns, frames));
        clip.show(System.nanoTime() % (frameNanos * frames) / (double) frameNanos);
        return clip.asset;
    }

    public static boolean owns(Identifier texture) {
        return texture != null && texture.getNamespace().equals(Paradox.id("cape").getNamespace());
    }

    private static ClientAsset.Texture create(Theme theme) {
        int[] pixels = paint(theme, logoPixels());
        Identifier id = Paradox.id("cape/" + theme.name().toLowerCase(Locale.ROOT));
        Minecraft.getInstance().getTextureManager().register(id, new MippedTexture(id, pixels, PAINT_WIDTH, PAINT_HEIGHT));
        return new ClientAsset.ResourceTexture(id, id);
    }

    private static int[] logoPixels() {
        try (var input = Minecraft.getInstance().getResourceManager().open(Paradox.id(LOGO));
             var image = NativeImage.read(input)) {
            return image.getPixels();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static ClientAsset.Texture load(String name) {
        Identifier id = Paradox.id("cape/" + name);
        try (var input = Minecraft.getInstance().getResourceManager().open(Paradox.id("cape/" + name + ".png"))) {
            return register(id, NativeImage.read(input));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static Clip loadClip(String name, int columns, int frames) {
        Identifier id = Paradox.id("cape/" + name);
        try (var input = Minecraft.getInstance().getResourceManager().open(Paradox.id("cape/" + name + ".png"))) {
            NativeImage sheet = NativeImage.read(input);
            var image = new NativeImage(WIDTH, HEIGHT, false);
            image.fillRect(0, 0, WIDTH, HEIGHT, CLIP_BACKGROUND);
            ClientAsset.Texture asset = register(id, image);
            var texture = (DynamicTexture) Minecraft.getInstance().getTextureManager().getTexture(id);
            return new Clip(asset, texture, sheet, columns, frames);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static ClientAsset.Texture register(Identifier id, NativeImage image) {
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image) {
            {
                sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            }
        });
        return new ClientAsset.ResourceTexture(id, id);
    }

    static int[] paint(Theme theme, int[] logo) {
        int[] pixels = new int[PAINT_WIDTH * PAINT_HEIGHT];
        Arrays.fill(pixels, theme.header);
        paintFace(pixels, 1, 1, 10, 16, theme, logo);
        for (int y = 0; y < 16 * PAINT_SCALE; y++) {
            int row = (y + PAINT_SCALE) * PAINT_WIDTH;
            System.arraycopy(pixels, row + PAINT_SCALE, pixels, row + 12 * PAINT_SCALE, 10 * PAINT_SCALE);
        }
        return pixels;
    }

    private static void paintFace(int[] pixels, int faceX, int faceY, int faceWidth, int faceHeight,
                                  Theme theme, int[] logo) {
        int width = faceWidth * PAINT_SCALE;
        int height = faceHeight * PAINT_SCALE;
        float[] mark = new float[width * height];
        float logoWidth = width * LOGO_FILL;
        float logoHeight = logoWidth * LOGO_HEIGHT / LOGO_WIDTH;
        drawLogo(mark, width, height, logo, (width - logoWidth) / 2, (height - logoHeight) / 2, logoWidth, logoHeight);
        int paddedWidth = width + GLOW_PADDING * 2;
        int paddedHeight = height + GLOW_PADDING * 2;
        float[] outside = new float[paddedWidth * paddedHeight];
        Arrays.fill(outside, 1);
        for (int y = 0; y < height; y++) {
            Arrays.fill(outside, GLOW_PADDING + (y + GLOW_PADDING) * paddedWidth,
                    GLOW_PADDING + width + (y + GLOW_PADDING) * paddedWidth, 0);
        }
        float[] glow = blur(outside, paddedWidth, paddedHeight, GLOW_RADIUS);
        float[] line = blur(outside, paddedWidth, paddedHeight, RIM_RADIUS);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int padded = x + GLOW_PADDING + (y + GLOW_PADDING) * paddedWidth;
                float rim = Math.clamp((line[padded] - 0.14f) / 0.1f, 0, 1);
                float accent = Math.min(1, Math.max(rim, glow[padded] * GLOW_STRENGTH));
                int wave = ThemeColors.wave(theme.primary, theme.secondary, x * 80f / width, 0);
                int base = ARGB.opaque(ARGB.srgbLerp(accent, theme.header, wave));
                int color = ARGB.opaque(ARGB.srgbLerp(mark[x + y * width], base, wave));
                pixels[(faceX * PAINT_SCALE + x) + (faceY * PAINT_SCALE + y) * PAINT_WIDTH] = color;
            }
        }
    }

    private static void drawLogo(float[] mask, int width, int height, int[] logo, float left, float top,
                                 float logoWidth, float logoHeight) {
        float step = LOGO_WIDTH / logoWidth;
        int endX = Math.min(width, (int) Math.ceil(left + logoWidth));
        int endY = Math.min(height, (int) Math.ceil(top + logoHeight));
        for (int y = Math.max(0, (int) top); y < endY; y++) {
            for (int x = Math.max(0, (int) left); x < endX; x++) {
                mask[x + y * width] = logoCoverage(logo, LOGO_X + (x - left) * step, LOGO_Y + (y - top) * step, step);
            }
        }
    }

    private static float logoCoverage(int[] logo, float x, float y, float size) {
        int endX = Math.min(LOGO_SIZE, (int) Math.ceil(x + size));
        int endY = Math.min(LOGO_SIZE, (int) Math.ceil(y + size));
        float sum = 0;
        int count = 0;
        for (int row = Math.max(0, (int) y); row < endY; row++) {
            for (int column = Math.max(0, (int) x); column < endX; column++) {
                sum += (logo[column + row * LOGO_SIZE] >>> 24) / 255f;
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    private static float[] blur(float[] source, int width, int height, int radius) {
        float[] current = source.clone();
        for (int pass = 0; pass < 3; pass++) {
            float[] next = new float[current.length];
            for (int y = 0; y < height; y++) {
                blurLine(current, next, y * width, 1, width, radius);
            }
            current = new float[next.length];
            for (int x = 0; x < width; x++) {
                blurLine(next, current, x, width, height, radius);
            }
        }
        return current;
    }

    private static void blurLine(float[] source, float[] target, int start, int stride, int length, int radius) {
        double sum = 0;
        for (int offset = -radius; offset <= radius; offset++) {
            sum += source[start + Math.clamp(offset, 0, length - 1) * stride];
        }
        for (int index = 0; index < length; index++) {
            target[start + index * stride] = (float) (sum / (radius * 2 + 1));
            sum += source[start + Math.min(index + radius + 1, length - 1) * stride]
                    - source[start + Math.max(index - radius, 0) * stride];
        }
    }

    private static int[] halve(int[] pixels, int width, int height) {
        int[] half = new int[width / 2 * (height / 2)];
        for (int y = 0; y < height / 2; y++) {
            for (int x = 0; x < width / 2; x++) {
                int first = x * 2 + y * 2 * width;
                int[] corners = {pixels[first], pixels[first + 1], pixels[first + width], pixels[first + width + 1]};
                int color = 0;
                for (int shift = 0; shift < 32; shift += 8) {
                    int sum = 2;
                    for (int corner : corners) sum += corner >>> shift & 255;
                    color |= (sum / 4) << shift;
                }
                half[x + y * (width / 2)] = color;
            }
        }
        return half;
    }

    private static final class MippedTexture extends AbstractTexture {
        private MippedTexture(Identifier id, int[] pixels, int width, int height) {
            var device = RenderSystem.getDevice();
            texture = device.createTexture(id::toString, TEXTURE_USAGE, GpuFormat.RGBA8_UNORM, width, height, 1, MIP_LEVELS);
            textureView = device.createTextureView(texture);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true);
            var encoder = device.createCommandEncoder();
            for (int level = 0; level < MIP_LEVELS; level++) {
                try (var image = new NativeImage(width, height, false)) {
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            image.setPixel(x, y, pixels[x + y * width]);
                        }
                    }
                    encoder.writeToTexture(texture, image, level, 0, 0, 0);
                }
                pixels = halve(pixels, width, height);
                width /= 2;
                height /= 2;
            }
        }
    }

    private static final class Clip {
        private static final int BLEND_STEPS = 32;
        private final ClientAsset.Texture asset;
        private final DynamicTexture texture;
        private final int[] sheet;
        private final int sheetWidth;
        private final int columns;
        private final int frames;
        private int shown = -1;

        private Clip(ClientAsset.Texture asset, DynamicTexture texture, NativeImage sheet, int columns, int frames) {
            this.asset = asset;
            this.texture = texture;
            this.sheet = sheet.getPixels();
            this.sheetWidth = sheet.getWidth();
            this.columns = columns;
            this.frames = frames;
            sheet.close();
        }

        private void show(double position) {
            int frame = (int) position % frames;
            int mix = (int) ((position - Math.floor(position)) * BLEND_STEPS);
            int key = frame * BLEND_STEPS + mix;
            if (key == shown) return;
            shown = key;
            int next = (frame + 1) % frames;
            int fromX = frame % columns * FACE_WIDTH;
            int fromY = frame / columns * FACE_HEIGHT;
            int toX = next % columns * FACE_WIDTH;
            int toY = next / columns * FACE_HEIGHT;
            NativeImage target = texture.getPixels();
            for (int y = 0; y < FACE_HEIGHT; y++) {
                int fromRow = (fromY + y) * sheetWidth + fromX;
                int toRow = (toY + y) * sheetWidth + toX;
                for (int x = 0; x < FACE_WIDTH; x++) {
                    int color = blend(sheet[fromRow + x], sheet[toRow + x], mix);
                    target.setPixel(SCALE + x, SCALE + y, color);
                    target.setPixel(12 * SCALE + x, SCALE + y, color);
                }
            }
            texture.upload();
        }

        private static int blend(int from, int to, int mix) {
            int keep = BLEND_STEPS - mix;
            int result = 0;
            for (int shift = 0; shift < 32; shift += 8) {
                int channel = ((from >>> shift & 0xFF) * keep + (to >>> shift & 0xFF) * mix) / BLEND_STEPS;
                result |= channel << shift;
            }
            return result;
        }
    }

}
