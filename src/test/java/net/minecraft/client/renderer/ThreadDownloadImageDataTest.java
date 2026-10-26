package net.minecraft.client.renderer;

import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import org.junit.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Exercises the production HTTP downloader against loopback only; no renderer or account access. */
public class ThreadDownloadImageDataTest {
    public static class Client extends Minecraft {
        public Client() { super(null); }
        @Override public Proxy getProxy() { return Proxy.NO_PROXY; }
    }

    @Test public void undecodableCachedImageRetriesAndReplacesCacheFromLocalServer() throws Exception {
        checkCache(false);
    }

    @Test public void validCachedImageDoesNotRequestTheServer() throws Exception {
        checkCache(true);
    }

    private void checkCache(boolean valid) throws Exception {
        Field singleton = Minecraft.class.getDeclaredField("theMinecraft"); singleton.setAccessible(true);
        Object previous = singleton.get(null);
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        singleton.set(null, ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(Client.class));
        Path cache = Files.createTempFile("pulsar-skin-cache-", ".png");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger(), available = new AtomicInteger();
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png", encoded);
        byte[] image = encoded.toByteArray();
        server.createContext("/skin", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, image.length);
            try { exchange.getResponseBody().write(image); } finally { exchange.close(); }
        });
        server.start();
        try {
            Files.write(cache, valid ? image : new byte[]{1, 2, 3, 4});
            ThreadDownloadImageData download = new ThreadDownloadImageData(cache.toFile(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/skin", null, new ImageBufferDownload() {
                @Override public void skinAvailable() { available.incrementAndGet(); }
            });
            download.loadTexture(null);
            Field workerField = ThreadDownloadImageData.class.getDeclaredField("imageThread"); workerField.setAccessible(true);
            Thread worker = (Thread) workerField.get(download);
            if (!valid) {
                assertNotNull("Undecodable cached image must start a retry", worker);
                worker.join(5000);
                assertFalse("Loopback download did not finish", worker.isAlive());
            } else assertNull(worker);
            assertEquals(valid ? 0 : 1, requests.get());
            assertEquals(Boolean.TRUE, download.imageFound);
            assertEquals(1, available.get());
            assertNotNull("Successful retry must repair the on-disk cache", ImageIO.read(cache.toFile()));
        } finally {
            server.stop(0); Files.deleteIfExists(cache); singleton.set(null, previous);
        }
    }
}
