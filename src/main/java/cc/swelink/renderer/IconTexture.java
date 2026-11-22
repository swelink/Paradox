package cc.swelink.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;

final class IconTexture extends ReloadableTexture {
    private IconTexture(Identifier id) {
        super(id);
    }

    static IconTexture get(Identifier id) {
        var manager = Minecraft.getInstance().getTextureManager();
        var loaded = manager.getTexture(id);
        if (loaded instanceof IconTexture icon) {
            return icon;
        }
        IconTexture icon = new IconTexture(id);
        manager.registerAndLoad(id, icon);
        return icon;
    }

    @Override
    public TextureContents loadContents(ResourceManager resources) throws IOException {
        return TextureContents.load(resources, resourceId());
    }

    static int mipLevels(int width, int height) {
        int divisibleSize = Math.min(Integer.lowestOneBit(width), Integer.lowestOneBit(height));
        return 1 + Integer.numberOfTrailingZeros(divisibleSize);
    }

    @Override
    protected void setSampler(TextureContents contents) {
        NativeImage image = contents.image();
        boolean mipmapped = mipLevels(image.getWidth(), image.getHeight()) > 1;
        sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, mipmapped);
    }

    @Override
    protected void doLoad(NativeImage image) {
        int levelCount = mipLevels(image.getWidth(), image.getHeight());

        NativeImage[] levels = MipmapGenerator.generateMipLevels(resourceId(), new NativeImage[]{image},
                levelCount - 1, MipmapStrategy.MEAN, 0, image.computeTransparency());
        boolean uploaded = false;
        try {
            releaseTextures();
            var device = RenderSystem.getDevice();
            texture = device.createTexture(resourceId().toString(), 5, GpuFormat.RGBA8_UNORM,
                    image.getWidth(), image.getHeight(), 1, levelCount);
            textureView = device.createTextureView(texture);
            var encoder = device.createCommandEncoder();
            for (int level = 0; level < levels.length; level++) {
                encoder.writeToTexture(texture, levels[level], level, 0, 0, 0);
            }
            uploaded = true;
        } finally {
            if (!uploaded) {
                releaseTextures();
            }
            for (int level = 1; level < levels.length; level++) {
                levels[level].close();
            }
        }
    }
}
