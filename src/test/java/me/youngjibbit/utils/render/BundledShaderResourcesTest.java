package me.youngjibbit.utils.render;

import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import static org.junit.Assert.*;

public class BundledShaderResourcesTest {
    @Test public void legacyShaderPathsContainTheCanonicalPrograms() throws IOException {
        for (String name : new String[]{"gradient", "gradientMask", "kawaseDown", "kawaseUp", "outline"}) {
            String filename = "/Shaders/" + name + ".frag";
            assertArrayEquals(name, read("Flauxy" + filename), read("Tenacity" + filename));
        }
    }

    private byte[] read(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/assets/minecraft/" + path)) {
            assertNotNull(path, input);
            return IOUtils.toByteArray(input);
        }
    }
}
