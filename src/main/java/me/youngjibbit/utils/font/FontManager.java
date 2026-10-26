package me.youngjibbit.utils.font;

import me.youngjibbit.Pulsar;
import me.youngjibbit.utils.render.RenderDiagnostics;
import java.awt.Font;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;

/** Owns requested renderer variants and one base Font per bundled file. All GPU work stays on the render thread. */
public class FontManager implements AutoCloseable {
    private static final Map<String, String> FACES;
    private static final Set<Integer> SIZES=new HashSet<>(Arrays.asList(6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,36,40,41,42,43,44));
    static {
        Map<String, String> faces = new LinkedHashMap<>();
        faces.put("Poppins-Regular", "Poppins-Regular.ttf");
        faces.put("Poppins-Medium", "Poppins-Medium.ttf");
        faces.put("Poppins-Bold", "Poppins-Bold.ttf");
        faces.put("MiSansLatinVF", "MiSansLatinVF.ttf");
        faces.put("Product-Sans-Regular", "Product-Sans-Regular.ttf");
        faces.put("Product-Sans-Medium", "Product-Sans-Medium.ttf");
        faces.put("Product-Sans-Bold", "Product-Sans-Bold.ttf");
        faces.put("Inter-Regular", "Inter-Regular.ttf");
        faces.put("Inter-Medium", "Inter-Medium.ttf");
        faces.put("Inter-Bold", "Inter-Bold.ttf");
        faces.put("SFProText-Regular", "SFProRounded-Regular.ttf");
        faces.put("SFProText-Medium", "SFProRounded-Medium.ttf");
        faces.put("SFProText-Bold", "SFProRounded-Bold.ttf");
        faces.put("DmSansRegular", "DmSansRegular.ttf");
        faces.put("DmSansMedium", "DmSansMedium.ttf");
        faces.put("DmSansBold", "DmSansBold.ttf");
        faces.put("TenoriteRegular", "TenoriteRegular.ttf");
        faces.put("TenoriteBold", "TenoriteBold.ttf");
        faces.put("KhmerOureang", "KhmerOureang.ttf");
        faces.put("PoeVeticaUi", "PoeVeticaUi.ttf");
        faces.put("Serif", "Serif.ttf");
        faces.put("VactorySans", "VactorySans.ttf");
        faces.put("Boldena", "Boldena.ttf");
        faces.put("icon", "icon.ttf");
        faces.put("icon1", "icon1.ttf");
        faces.put("iOSIcons", "iOSIcons.ttf");
        FACES = Collections.unmodifiableMap(faces);
    }
    private TTFFontRenderer defaultFont;
    private HashMap<String, TTFFontRenderer> fonts = new HashMap<>();
    private final Map<String, Font> baseFonts = new HashMap<>();
    private final Set<String> failedFiles = new HashSet<>();
    private final Thread renderThread = Thread.currentThread();
    private boolean closed;

    public FontManager() {}

    public FontManager getInstance() {
        return this;
    }

    public static TTFFontRenderer getFont() {
        return Pulsar.INSTANCE.fontManager.getFont("Poppins-Medium 21");
    }

    protected InputStream openFont(String file) {
        return getClass().getResourceAsStream("/assets/minecraft/" + file);
    }

    /** Lookup and metrics are CPU-only; the renderer enforces ownership when drawing. */
    public synchronized TTFFontRenderer getFont(String key) {
        if (closed) throw new IllegalStateException("FontManager is closed");
        TTFFontRenderer cached = fonts.get(key);
        if (cached != null) return cached;
        if ("Verdana Bold 16".equals(key) || "Verdana Bold 20".equals(key)) {
            int size = key.endsWith("16") ? 16 : 20;
            TTFFontRenderer renderer = new TTFFontRenderer(new Font("Verdana Bold", Font.PLAIN, size), renderThread);
            fonts.put(key, renderer);
            return renderer;
        }
        int split = key == null ? -1 : key.lastIndexOf(' ');
        if (split < 1) return fallback();
        String face = key.substring(0, split), number = key.substring(split + 1);
        int size;
        try {
            size = Integer.parseInt(number);
        } catch (NumberFormatException ex) {
            return fallback();
        }
        String file = FACES.get(face);
        if (file == null || !SIZES.contains(size) || !Integer.toString(size).equals(number) || failedFiles.contains(file)) {
            return fallback();
        }
        Font base = baseFonts.get(file);
        if (base == null) {
            try (InputStream stream = openFont(file)) {
                if (stream == null) throw new FileNotFoundException(file);
                if (RenderDiagnostics.enabled) RenderDiagnostics.fontFileLoads++;
                base = Font.createFont(Font.TRUETYPE_FONT, stream);
                baseFonts.put(file, base);
            } catch (Exception ex) {
                failedFiles.add(file);
                System.err.println("Font unavailable: " + file + "; using default font");
                return fallback();
            }
        }
        TTFFontRenderer renderer = new TTFFontRenderer(base.deriveFont(Font.PLAIN, (float) size), renderThread);
        fonts.put(key, renderer);
        return renderer;
    }

    private TTFFontRenderer fallback() {
        if (defaultFont == null) defaultFont = new TTFFontRenderer(new Font("Verdana", Font.PLAIN, 18), renderThread);
        return defaultFont;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        if (Thread.currentThread() != renderThread) throw new IllegalStateException("Font disposal requires the render thread");
        Set<TTFFontRenderer> owned = Collections.newSetFromMap(new IdentityHashMap<TTFFontRenderer, Boolean>());
        owned.addAll(fonts.values());
        if (defaultFont != null) owned.add(defaultFont);
        for (TTFFontRenderer renderer : owned) renderer.close();
        fonts.clear();
        baseFonts.clear();
        failedFiles.clear();
        defaultFont = null;
        closed = true;
    }
}
