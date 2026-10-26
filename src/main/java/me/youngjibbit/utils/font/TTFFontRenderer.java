package me.youngjibbit.utils.font;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.utils.render.RenderDiagnostics;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

public class TTFFontRenderer implements AutoCloseable {

    private final boolean antiAlias;
    /**
     * The font to be drawn.
     */
    private Font font;

    /**
     * If fractional metrics should be used in the font renderer.
     */
    private boolean fractionalMetrics = false;

    /**
     * All the character data information (regular).
     */
    private volatile CharacterData[] regularData;

    /**
     * All the character data information (bold).
     */
    private volatile CharacterData[] boldData;

    /**
     * All the character data information (italics).
     */
    private volatile CharacterData[] italicsData;

    /**
     * All the color codes used in minecraft.
     */
    private int[] colorCodes = new int[32];

    /**
     * The margin on each texture.
     */
    private static final int MARGIN = 4;

    /**
     * The character that invokes color in a string when rendered.
     */
    private static final char COLOR_INVOKER = '\247';

    /**
     * The random offset in obfuscated text.
     */
    private static int RANDOM_OFFSET = 1;

    private Thread renderThread = Thread.currentThread();
    private final Set<Integer> ownedTextures = new LinkedHashSet<>();
    private final List<Future<?>> preparation = new ArrayList<>();
    private ConcurrentLinkedQueue<TextureData> pendingTextures;
    private boolean lazyStyles;
    private volatile boolean closed;

    /** Production path: only drawing allocates textures, using the legacy rasterizer. */
    public TTFFontRenderer(Font font) {
        this(font, Thread.currentThread());
    }

    public TTFFontRenderer(Font font, Thread owner) {
        this.font = font;
        renderThread = owner;
        fractionalMetrics = true;
        antiAlias = true;
        lazyStyles = true;
        generateColors();
        if (RenderDiagnostics.enabled) RenderDiagnostics.fontRendererCreates++;
    }

    private void checkThread() {
        if (closed) throw new IllegalStateException("Font renderer is closed");
        if (Thread.currentThread() != renderThread) throw new IllegalStateException("Glyph upload requires the render thread");
    }

    private int[] allocateTextures(int count) {
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            ids[i] = GL11.glGenTextures();
            ownedTextures.add(ids[i]);
            if (RenderDiagnostics.enabled) RenderDiagnostics.glyphTexturesCreated++;
        }
        return ids;
    }

    /** Metrics are CPU-only and safe for worker layout; only drawing materializes glyph textures. */
    private synchronized CharacterData[] data(int style) {
        if (closed) throw new IllegalStateException("Font renderer is closed");
        CharacterData[] existing = style == Font.BOLD ? boldData : style == Font.ITALIC ? italicsData : regularData;
        if (existing != null) return existing;
        if (!lazyStyles) throw new IllegalStateException("Legacy font preparation is incomplete");
        BufferedImage utility = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = utility.createGraphics();
        graphics.setFont(font.deriveFont(style));
        FontMetrics metrics = graphics.getFontMetrics();
        CharacterData[] measured = new CharacterData[256];
        try {
            for (int i = 0; i < measured.length; i++) {
                Rectangle2D bounds = metrics.getStringBounds(String.valueOf((char) i), graphics);
                measured[i] = new CharacterData((char) i,
                        MathHelper.ceiling_double_int((float) bounds.getWidth() + 2 * MARGIN),
                        MathHelper.ceiling_double_int((float) bounds.getHeight()), 0);
            }
        } finally {
            graphics.dispose();
        }
        if (style == Font.BOLD) boldData = measured;
        else if (style == Font.ITALIC) italicsData = measured;
        else regularData = measured;
        return measured;
    }

    private synchronized CharacterData[] drawingData(int style) {
        CharacterData[] existing = data(style);
        if (!lazyStyles || existing[0].textureId != 0) return existing;
        checkThread();
        try (RenderState state = new RenderState()) {
            ConcurrentLinkedQueue<TextureData> queue = new ConcurrentLinkedQueue<>();
            CharacterData[] created = setup(new CharacterData[256], allocateTextures(256), queue, style);
            for (TextureData texture : queue) {
                GlStateManager.bindTexture(texture.getTextureId());
                // Existing glyph drawing also binds directly, so synchronize the actual binding.
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureId());
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, texture.getWidth(), texture.getHeight(),
                        0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texture.getBuffer());
            }
            if (style == Font.BOLD) boldData = created;
            else if (style == Font.ITALIC) italicsData = created;
            else regularData = created;
            return created;
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        checkThread();
        boolean interrupted = false;
        for (Future<?> task : preparation) {
            boolean done = false;
            while (!done) {
                try {
                    task.get();
                    done = true;
                } catch (InterruptedException ex) {
                    interrupted = true;
                } catch (ExecutionException ex) {
                    done = true;
                }
            }
        }
        if (pendingTextures != null) pendingTextures.removeIf(texture -> ownedTextures.contains(texture.getTextureId()));
        for (int texture : ownedTextures) {
            GlStateManager.deleteTexture(texture);
            if (RenderDiagnostics.enabled) RenderDiagnostics.glyphTexturesDeleted++;
        }
        ownedTextures.clear();
        preparation.clear();
        regularData = boldData = italicsData = null;
        closed = true;
        if (interrupted) Thread.currentThread().interrupt();
    }

    public TTFFontRenderer(ExecutorService executorService, ConcurrentLinkedQueue<me.youngjibbit.utils.font.TextureData> textureQueue, Font font) {
        this(executorService, textureQueue, font, 256);
    }

    public TTFFontRenderer(ExecutorService executorService, ConcurrentLinkedQueue<me.youngjibbit.utils.font.TextureData> textureQueue, Font font, int characterCount) {
        this(executorService, textureQueue, font, characterCount, true);
    }

    public TTFFontRenderer(ExecutorService executorService, ConcurrentLinkedQueue<me.youngjibbit.utils.font.TextureData> textureQueue, Font font, boolean antiAlias) {
        this(executorService, textureQueue, font, 256, antiAlias);
    }

    public TTFFontRenderer(ExecutorService executorService, ConcurrentLinkedQueue<me.youngjibbit.utils.font.TextureData> textureQueue, Font font, int characterCount, boolean antiAlias) {
        this.font = font;
        this.fractionalMetrics = true;
        this.antiAlias = antiAlias;

        pendingTextures=textureQueue;if(RenderDiagnostics.enabled)RenderDiagnostics.fontRendererCreates++;
        int[] regularTexturesIds=allocateTextures(characterCount),boldTexturesIds=allocateTextures(characterCount),italicTexturesIds=allocateTextures(characterCount);
        preparation.add(executorService.submit(()->regularData=setup(new CharacterData[characterCount],regularTexturesIds,textureQueue,Font.PLAIN)));
        preparation.add(executorService.submit(()->boldData=setup(new CharacterData[characterCount],boldTexturesIds,textureQueue,Font.BOLD)));
        preparation.add(executorService.submit(()->italicsData=setup(new CharacterData[characterCount],italicTexturesIds,textureQueue,Font.ITALIC)));
    }

    /**
     * Sets up the character data and textures.
     *
     * @param characterData The array of character data that should be filled.
     * @param type          The font type. (Regular, Bold, and Italics)
     */
    private CharacterData[] setup(CharacterData[] characterData, int[] texturesIds, ConcurrentLinkedQueue<me.youngjibbit.utils.font.TextureData> textureQueue, int type) {
        generateColors();

        Font font = this.font.deriveFont(type);

        BufferedImage utilityImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

        Graphics2D utilityGraphics = (Graphics2D) utilityImage.getGraphics();

        utilityGraphics.setFont(font);

        FontMetrics fontMetrics = utilityGraphics.getFontMetrics();

        for (int index = 0; index < characterData.length; index++) {
            char character = (char) index;

            Rectangle2D characterBounds = fontMetrics.getStringBounds(character + "", utilityGraphics);

            float width = (float) characterBounds.getWidth() + (2 * MARGIN);

            float height = (float) characterBounds.getHeight();

            BufferedImage characterImage = new BufferedImage(MathHelper.ceiling_double_int(width), MathHelper.ceiling_double_int(height), BufferedImage.TYPE_INT_ARGB);

            Graphics2D graphics = (Graphics2D) characterImage.getGraphics();

            graphics.setFont(font);

            graphics.setColor(new Color(255, 255, 255, 0));

            graphics.fillRect(0, 0, characterImage.getWidth(), characterImage.getHeight());

            graphics.setColor(Color.WHITE);

            if (antiAlias) {
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, this.fractionalMetrics ? RenderingHints.VALUE_FRACTIONALMETRICS_ON : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            }
            graphics.drawString(character + "", MARGIN, fontMetrics.getAscent());

            int textureId = texturesIds[index];

            createTexture(textureId, characterImage, textureQueue);

            characterData[index] = new CharacterData(character, characterImage.getWidth(), characterImage.getHeight(), textureId);
            graphics.dispose();
        }

        utilityGraphics.dispose();
        return characterData;
    }

    private void createTexture(int textureId, BufferedImage image, ConcurrentLinkedQueue<me.youngjibbit.utils.font.TextureData> textureQueue) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

        ByteBuffer buffer = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4); //4 for RGBA, 3 for RGB

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {

                int pixel = pixels[y * image.getWidth() + x];

                buffer.put((byte) ((pixel >> 16) & 0xFF));
                buffer.put((byte) ((pixel >> 8) & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) ((pixel >> 24) & 0xFF));
            }
        }
        buffer.flip();
        textureQueue.add(new me.youngjibbit.utils.font.TextureData(textureId, image.getWidth(), image.getHeight(), buffer));

    }

    public int drawString(String text, float x, float y, int color) {
        return renderString(text, x, y, color, false);
    }

    public void drawCenteredString(String text, float x, float y, int color) {
        float width = getWidth(text) / 2;
        renderString(text, x - width, y, color, false);
    }

    public void drawStringWithShadow(String text, double x, double y, int color) {
        GL11.glTranslated(0.5, 0.5, 0);
        renderString(text, (float)x, (float)y, color, true);
        GL11.glTranslated(-0.5, -0.5, 0);
        renderString(text, (float)x, (float)y, color, false);
    }

    private int renderString(String text, float x, float y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        checkThread();
        GL11.glPushMatrix();
        GlStateManager.scale(0.5, 0.5, 1.0);
        GlStateManager.enableBlend();
        if(me.youngjibbit.utils.render.TextEffects.isCapturing())
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
        else GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        x = (x - MARGIN / 2 + 0.5f) * 2;
        y = (y - MARGIN / 2 + 0.5f) * 2;

        CharacterData[] characterData = drawingData(Font.PLAIN);
        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;

        double alphaMultiplier = 255.0 * (shadow ? 4 : 1);

        Color c = new Color(color, true);
        GL11.glColor4d(c.getRed() / alphaMultiplier, c.getGreen() / alphaMultiplier, c.getBlue() / alphaMultiplier, (color >> 24 & 0xFF) / 255.0);

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);

            if (i > 0 && text.charAt(i - 1) == COLOR_INVOKER) {
                continue;
            }

            if (character == COLOR_INVOKER && i + 1 < text.length()) {
                int index = "0123456789abcdefklmnor".indexOf(Character.toLowerCase(text.charAt(i + 1)));
                if (index < 16) {
                    obfuscated = false;
                    strikethrough = false;
                    underlined = false;
                    characterData = drawingData(Font.PLAIN);

                    index = Math.max(0, Math.min(index, 15));
                    if (shadow) index += 16;

                    int textColor = colorCodes[index];
                    GL11.glColor4d((textColor >> 16 & 255) / 255.0, (textColor >> 8 & 255) / 255.0, (textColor & 255) / 255.0, (color >> 24 & 0xFF) / 255.0);
                } else if (index == 16) {
                    obfuscated = true;
                } else if (index == 17) {
                    characterData = drawingData(Font.BOLD);
                } else if (index == 18) {
                    strikethrough = true;
                } else if (index == 19) {
                    underlined = true;
                } else if (index == 20) {
                    characterData = drawingData(Font.ITALIC);
                } else if (index == 21) {
                    obfuscated = false;
                    strikethrough = false;
                    underlined = false;
                    characterData = drawingData(Font.PLAIN);
                    GL11.glColor4d(1.0 * (shadow ? 0.25 : 1), 1.0 * (shadow ? 0.25 : 1), 1.0 * (shadow ? 0.25 : 1), (color >> 24 & 0xFF) / 255.0);
                }
                i++; // Skip the next character (color code).
            } else {
                if (character > 255) {
                    continue;
                }

                if (obfuscated) {
                    character = (char) (((int) character) + RANDOM_OFFSET);
                }

                drawChar(character, characterData, x, y);

                CharacterData charData = characterData[character];
                if (strikethrough) {
                    drawLine(new Vector2f(0, charData.height / 2f), new Vector2f(charData.width, charData.height / 2f), 3);
                }
                if (underlined) {
                    drawLine(new Vector2f(0, charData.height - 15), new Vector2f(charData.width, charData.height - 15), 3);
                }

                x += charData.width - (2 * MARGIN);
            }
        }

        GL11.glPopMatrix();
        GlStateManager.disableBlend();
        GlStateManager.bindTexture(0);
        GL11.glColor4d(1.0, 1.0, 1.0, 1.0);

        return (int) x;
    }


    /**
     * Gets the width of the given text.
     *
     * @param text The text to get the width of.
     * @return The width of the given text.
     */
    public float getWidth(String text) {
        // The width of the string.
        float width = 0;

        // The character texture set to be used. (Regular by default)
        CharacterData[] characterData = data(Font.PLAIN);

        // The length of the text.
        int length = text.length();

        // Loops through the text.
        for (int i = 0; i < length; i++) {
            // The character at the index of 'i'.
            char character = text.charAt(i);

            // The previous character.
            char previous = i > 0 ? text.charAt(i - 1) : '.';

            // Continues if the previous color was the color invoker.
            if (previous == COLOR_INVOKER) continue;

            // Sets the color if the character is the color invoker and the character index is less than the length - 1.
            if (character == COLOR_INVOKER && i + 1 < length) { // Fix: Added bounds check here.
                // The color index of the character after the current character.
                int index = "0123456789abcdefklmnor".indexOf(text.toLowerCase(Locale.ENGLISH).charAt(i + 1));

                if (index < 16 || index == 21)
                    characterData = data(Font.PLAIN);
                else if (index == 17)
                    // Sets the character data to the bold type.
                    characterData = data(Font.BOLD);
                else if (index == 20)
                    // Sets the character data to the italics type.
                    characterData = data(Font.ITALIC);
                else if (index == 21)
                    // Sets the character data to the regular type.
                    characterData = data(Font.PLAIN);

                // Skip the next character since it's part of the color code.
                continue;
            } else {
                // Continues to not crash!
                if (character > 255) continue;

                // The character data for the given character.
                CharacterData charData = characterData[character];

                // Adds to the offset.
                width += (charData.width - (2 * MARGIN)) / 2;
            }
        }

        // Returns the width.
        return width + MARGIN / 2;
    }

    /**
     * Gets the height of the given text.
     *
     * @param text The text to get the height of.
     * @return The height of the given text.
     */
    public float getHeight(String text) {

        // The height of the string.
        float height = 0;

        // The character texture set to be used. (Regular by default)
        CharacterData[] characterData = data(Font.PLAIN);

        // The length of the text.
        int length = text.length();

        // Loops through the text.
        for (int i = 0; i < length; i++) {
            // The character at the index of 'i'.
            char character = text.charAt(i);

            // The previous character.
            char previous = i > 0 ? text.charAt(i - 1) : '.';

            // Continues if the previous color was the color invoker.
            if (previous == COLOR_INVOKER) continue;

            // Sets the color if the character is the color invoker and the character index is less than the length.
            if (character == COLOR_INVOKER && i + 1 < length) {

                // The color index of the character after the current character.
                int index = "0123456789abcdefklmnor".indexOf(text.toLowerCase(Locale.ENGLISH).charAt(i + 1));

                if (index < 16 || index == 21)
                    characterData = data(Font.PLAIN);
                else if (index == 17)
                    // Sets the character data to the bold type.
                    characterData = data(Font.BOLD);
                else if (index == 20)
                    // Sets the character data to the italics type.
                    characterData = data(Font.ITALIC);
                else if (index == 21)
                    // Sets the character data to the regular type.
                    characterData = data(Font.PLAIN);
            } else {
                // Continues to not crash!
                if (character > 255) continue;

                // The character data for the given character.
                CharacterData charData = characterData[character];

                // Sets the height if its bigger.
                height = Math.max(height, charData.height);
            }
        }

        // Returns the height.
        return height / 2 - MARGIN / 2;
    }

    /**
     * Draws the character.
     *
     * @param character     The character to be drawn.
     * @param characterData The character texture set to be used.
     */
    private void drawChar(char character, CharacterData[] characterData, float x, float y) {
        // The char data that stores the character data.
        CharacterData charData = characterData[character];

        // Binds the character data texture.
        charData.bind();

        // Begins drawing the quad.
        GL11.glBegin(GL11.GL_QUADS);
        {
            // Maps out where the texture should be drawn.
            GL11.glTexCoord2f(0, 0);
            GL11.glVertex2d(x, y);
            GL11.glTexCoord2f(0, 1);
            GL11.glVertex2d(x, y + charData.height);
            GL11.glTexCoord2f(1, 1);
            GL11.glVertex2d(x + charData.width, y + charData.height);
            GL11.glTexCoord2f(1, 0);
            GL11.glVertex2d(x + charData.width, y);
        }
        // Ends the quad.
        GL11.glEnd();
    }

    /**
     * Draws a line from start to end with the given width.
     *
     * @param start The starting point of the line.
     * @param end   The ending point of the line.
     * @param width The thickness of the line.
     */
    private void drawLine(Vector2f start, Vector2f end, float width) {
        // Disables textures so we can draw a solid line.
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        // Sets the width.
        GL11.glLineWidth(width);

        // Begins drawing the line.
        GL11.glBegin(GL11.GL_LINES);
        {
            GL11.glVertex2f(start.x, start.y);
            GL11.glVertex2f(end.x, end.y);
        }
        // Ends drawing the line.
        GL11.glEnd();

        // Enables texturing back on.
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /**
     * Generates all the colors.
     */
    private void generateColors() {
        // Iterates through 32 colors.
        for (int i = 0; i < 32; i++) {
            // Not sure what this variable is.
            int thingy = (i >> 3 & 1) * 85;

            // The red value of the color.
            int red = (i >> 2 & 1) * 170 + thingy;

            // The green value of the color.
            int green = (i >> 1 & 1) * 170 + thingy;

            // The blue value of the color.
            int blue = (i >> 0 & 1) * 170 + thingy;

            // Increments the red by 85, not sure why does this in minecraft's font renderer.
            if (i == 6) red += 85;

            // Used to make the shadow darker.
            if (i >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }

            // Sets the color in the color code at the index of 'i'.
            this.colorCodes[i] = (red & 255) << 16 | (green & 255) << 8 | blue & 255;
        }
    }

    /**
     * Class that holds the data for each character.
     */
    class CharacterData {

        /**
         * The character the data belongs to.
         */
        public char character;

        /**
         * The width of the character.
         */
        public float width;

        /**
         * The height of the character.
         */
        public float height;

        /**
         * The id of the character texture.
         */
        private int textureId;

        public CharacterData(char character, float width, float height, int textureId) {
            this.character = character;
            this.width = width;
            this.height = height;
            this.textureId = textureId;
        }

        /**
         * Binds the texture.
         */
        public void bind() {
            // Binds the opengl texture by the texture id.
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        }

    }

}
