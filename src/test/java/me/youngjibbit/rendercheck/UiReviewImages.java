package me.youngjibbit.rendercheck;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Bounded visual-review copies from the known native fixtures; original PNGs remain the evidence. */
public final class UiReviewImages {
    private UiReviewImages() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Native screenshot directory required");
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        List<Path> images;
        try (Stream<Path> paths = Files.walk(root)) {
            images = paths.filter(Files::isRegularFile).filter(path -> selected(root.relativize(path).toString().replace('\\', '/')))
                    .sorted().limit(24).collect(Collectors.toList());
        }
        for (Path path : images) {
            BufferedImage source = ImageIO.read(path.toFile());
            if (source == null) throw new IllegalStateException("Unreadable native screenshot: " + path);
            float scale = Math.min(1, 1280f / source.getWidth());
            BufferedImage preview = new BufferedImage(Math.max(1, Math.round(source.getWidth() * scale)),
                    Math.max(1, Math.round(source.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = preview.createGraphics();
            try {
                g.setColor(new Color(18, 23, 31));
                g.fillRect(0, 0, preview.getWidth(), preview.getHeight());
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(source, 0, 0, preview.getWidth(), preview.getHeight(), null);
            } finally { g.dispose(); }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            ImageWriter writer = writers.next();
            try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(output);
                ImageWriteParam options = writer.getDefaultWriteParam();
                options.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                options.setCompressionQuality(.84f);
                writer.write(null, new IIOImage(preview, null, null), options);
            } finally { writer.dispose(); }
            if (bytes.size() > 600_000) throw new IllegalStateException("Review image exceeded its bounded log budget");
            String name = root.relativize(path).toString().replace('\\', '/');
            System.out.println("UI_REVIEW_JPEG " + name + " " + Base64.getEncoder().encodeToString(bytes.toByteArray()));
        }
        System.out.println("Native visual review copies: " + images.size() + "; originals retained as PNG artifacts.");
    }

    private static boolean selected(String path) {
        if (!path.endsWith(".png")) return false;
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (path.startsWith("bed_visuals/bed-visuals-preview/")) return name.equals("world-glow.png")
                || name.equals("hud-progress-half.png");
        if (path.startsWith("demise_scaffold/demise-scaffold-overlay-preview/")) return name.equals("world-fill.png")
                || name.equals("blur-mask.png") || name.equals("glow-mask.png") || name.equals("composited-effects.png")
                || name.equals("blur-only-before.png") || name.equals("blur-only.png") || name.equals("glow-only.png");
        if (path.startsWith("chat/chat-preview/")) return name.equals("links-scale-1.0.png")
                || name.equals("opening.png") || name.equals("typing.png") || name.equals("closing.png")
                || name.equals("reduced-motion.png") || name.equals("resize-selected-draft.png")
                || name.equals("narrow-wrapped-tail.png") || name.equals("narrow-scrolled-history.png")
                || name.equals("formatted-history-narrow.png") || name.equals("formatted-history-wide-scale.png");
        if (path.startsWith("panel/gui-preview/")) return name.startsWith("compact-");
        if (path.startsWith("session/session-preview/")) return name.startsWith("island-compact-") || name.equals("island-glyph-motion.png");
        if (path.startsWith("session/hud-preview/")) return name.startsWith("compact-") && name.endsWith("-1280x720.png");
        if (path.startsWith("session/armor-preview/")) {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            return (lower.startsWith("modern-") || lower.startsWith("creative-")) && lower.endsWith("-horizontal.png");
        }
        return false;
    }
}
