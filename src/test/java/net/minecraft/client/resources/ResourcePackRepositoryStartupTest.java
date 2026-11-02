package net.minecraft.client.resources;

import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.rendercheck.PbufferTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.resources.data.PackMetadataSection;
import net.minecraft.client.resources.data.PackMetadataSectionSerializer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.src.Config;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class ResourcePackRepositoryStartupTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void leavesUnselectedPackMetadataForSelectionScreen() throws Exception {
        File packs = temporary.newFolder("resourcepacks");
        pack(packs, "Available");
        ResourcePackRepository repository = repository(packs, new GameSettings());
        assertNull(entry(repository, "Available").getResourcePack());
        assertTrue(repository.getRepositoryEntries().isEmpty());
    }

    @Test public void loadsSelectedPackAtStartupAndOtherPacksWhenSelectionOpens() throws Exception {
        File packs = temporary.newFolder("resourcepacks");
        pack(packs, "Selected", 0xFFFF0000);
        pack(packs, "Available", 0xFF0000FF);
        GameSettings settings = new GameSettings();
        settings.resourcePacks.add("Selected");
        ResourcePackRepository repository = repository(packs, settings);
        ResourcePackRepository.Entry selected = entry(repository, "Selected");
        ResourcePackRepository.Entry available = entry(repository, "Available");
        assertNotNull(selected.getResourcePack());
        assertNull(available.getResourcePack());
        assertEquals(1, repository.getRepositoryEntries().size());
        assertNull(icon(selected));

        repository.updateRepositoryEntriesAll();
        assertSame(selected, entry(repository, "Selected"));
        assertSame(available, entry(repository, "Available"));
        assertNotNull(available.getResourcePack());
        assertEquals(1, repository.getRepositoryEntries().size());
        assertFalse(available.getTexturePackDescription().contains("Invalid pack.mcmeta"));
        assertNull(icon(available));
    }

    @Test public void selectedZipReopensForResourceReloadAfterMetadataRead() throws Exception {
        File packs = temporary.newFolder("resourcepacks");
        zipPack(packs, "Selected.zip", 0xFFFF0000);
        GameSettings settings = new GameSettings();
        settings.resourcePacks.add("Selected.zip");
        ResourcePackRepository.Entry selected = entry(repository(packs, settings), "Selected.zip");
        assertNotNull(selected.getResourcePack());
        try (InputStream input = selected.getResourcePack().getInputStream(new ResourceLocation("probe.txt"))) {
            assertEquals(41, input.read());
            assertEquals(42, input.read());
        }
    }

    /** Run explicitly in a separate Java 8 JVM with the LWJGL native path. */
    public static void main(String[] args) throws Exception {
        TemporaryFolder temporary = new TemporaryFolder();
        temporary.create();
        try (PbufferTestContext context = new PbufferTestContext()) {
            File data = temporary.newFolder("data");
            Minecraft mc = new Minecraft(new GameConfiguration(
                    new GameConfiguration.UserInformation(new Session("PackFixture", "00000000000000000000000000000000", "0", "legacy"),
                            new PropertyMap(), new PropertyMap(), Proxy.NO_PROXY),
                    new GameConfiguration.DisplayInformation(1280, 960, false, false),
                    new GameConfiguration.FolderInformation(data, new File(data, "packs"), new File(data, "assets"), null),
                    new GameConfiguration.GameInformation(false, "resource pack icon check"),
                    new GameConfiguration.ServerInformation(null, 0)));
            context.initialize(mc, data);
            File packs = temporary.newFolder("resourcepacks");
            zipPack(packs, "Selected.zip", 0xFFFF0000);
            pack(packs, "Available", 0xFF0000FF);
            mc.gameSettings.resourcePacks.add("Selected.zip");
            ResourcePackRepositoryStartupTest fixture = new ResourcePackRepositoryStartupTest();
            fixture.temporary = temporary;
            ResourcePackRepository repository = fixture.repository(packs, mc.gameSettings);
            ResourcePackRepository.Entry selected = entry(repository, "Selected.zip");
            ResourcePackRepository.Entry available = entry(repository, "Available");
            assertNull(icon(selected));
            assertNull(available.getResourcePack());
            repository.updateRepositoryEntriesAll();
            assertNotNull(available.getResourcePack());
            TextureManager textures = new TextureManager(null);
            try (InputStream activeStream = selected.getResourcePack().getInputStream(new ResourceLocation("probe.txt"))) {
                assertEquals(41, activeStream.read());
                checkGpuIcon(selected, textures, 255, 0, 0);
                assertEquals("An active ZIP stream must survive lazy icon loading", 42, activeStream.read());
            }
            checkGpuIcon(available, textures, 0, 0, 255);
            checkMipmapUpload();
            context.check();
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            System.out.println("PASS: selected metadata, deferred icon decoding, pack refresh, exact GPU icon/mipmap pixels and sampler state");
        } finally {
            temporary.delete();
        }
    }

    private static void checkGpuIcon(ResourcePackRepository.Entry entry, TextureManager textures,
                                     int red, int green, int blue) throws Exception {
        entry.bindTexturePackIcon(textures);
        ResourceLocation location = (ResourceLocation) field(entry, "locationTexturePackIcon");
        assertNotNull(location);
        assertNotNull(icon(entry));
        assertTrue(GL11.glIsTexture(textures.getTexture(location).getGlTextureId()));
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        assertEquals(red, pixel.get(0) & 255);
        assertEquals(green, pixel.get(1) & 255);
        assertEquals(blue, pixel.get(2) & 255);
        textures.deleteTexture(location);
    }

    private static void checkMipmapUpload() {
        int texture = TextureUtil.glGenTextures();
        try {
            TextureUtil.allocateTextureImpl(texture, 2, 4, 4);
            int[][] levels = {new int[16], new int[4], new int[1]};
            for (int level = 0; level < levels.length; level++) {
                for (int pixel = 0; pixel < levels[level].length; pixel++) {
                    levels[level][pixel] = 0xFF000000 | ((level * 47 + pixel * 13) & 255) << 16
                            | ((level * 31 + pixel * 17) & 255) << 8 | ((level * 19 + pixel * 23) & 255);
                }
            }
            for (boolean blur : new boolean[]{true, false}) {
                TextureUtil.uploadTextureMipmap(levels, 4, 4, 0, 0, blur, blur);
                assertEquals(blur ? GL11.GL_LINEAR_MIPMAP_LINEAR : Config.getMipmapType(),
                        GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER));
                assertEquals(blur ? GL11.GL_LINEAR : GL11.GL_NEAREST,
                        GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER));
                int wrap = blur ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT;
                assertEquals(wrap, GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S));
                assertEquals(wrap, GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T));
                for (int level = 0; level < levels.length; level++) {
                    ByteBuffer pixels = BufferUtils.createByteBuffer(levels[level].length * 4);
                    GL11.glGetTexImage(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                    for (int pixel = 0; pixel < levels[level].length; pixel++) {
                        int expected = levels[level][pixel];
                        int offset = pixel * 4;
                        assertEquals((expected >> 16) & 255, pixels.get(offset) & 255);
                        assertEquals((expected >> 8) & 255, pixels.get(offset + 1) & 255);
                        assertEquals(expected & 255, pixels.get(offset + 2) & 255);
                        assertEquals((expected >>> 24) & 255, pixels.get(offset + 3) & 255);
                    }
                }
            }
        } finally {
            TextureUtil.deleteTexture(texture);
        }
    }

    private static BufferedImage icon(ResourcePackRepository.Entry entry) throws Exception {
        return (BufferedImage) field(entry, "texturePackIcon");
    }

    private static Object field(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private ResourcePackRepository repository(File packs, GameSettings settings) throws Exception {
        IMetadataSerializer metadata = new IMetadataSerializer();
        metadata.registerMetadataSectionType(new PackMetadataSectionSerializer(), PackMetadataSection.class);
        return new ResourcePackRepository(packs, temporary.newFolder("server-packs"),
                new DefaultResourcePack(Collections.emptyMap()), metadata, settings);
    }

    private static ResourcePackRepository.Entry entry(ResourcePackRepository repository, String name) {
        for (ResourcePackRepository.Entry entry : repository.getRepositoryEntriesAll())
            if (name.equals(entry.getResourcePackName())) return entry;
        throw new AssertionError("Missing pack: " + name);
    }

    private static void pack(File root, String name) throws Exception {
        pack(root, name, 0xFF000000);
    }

    private static void pack(File root, String name, int color) throws Exception {
        File directory = new File(root, name);
        assertTrue(directory.mkdir());
        Files.write(new File(directory, "pack.mcmeta").toPath(),
                "{\"pack\":{\"pack_format\":1,\"description\":\"Test pack\"}}".getBytes(StandardCharsets.UTF_8));
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color);
        assertTrue(ImageIO.write(image, "png", new File(directory, "pack.png")));
    }

    private static void zipPack(File root, String name, int color) throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", png));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(new File(root, name).toPath()))) {
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));
            zip.write("{\"pack\":{\"pack_format\":1,\"description\":\"Zip pack\"}}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("pack.png"));
            zip.write(png.toByteArray());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("assets/minecraft/probe.txt"));
            zip.write(new byte[]{41, 42});
            zip.closeEntry();
        }
    }
}
