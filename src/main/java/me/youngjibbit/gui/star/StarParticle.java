package me.youngjibbit.gui.star;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import me.youngjibbit.Pulsar;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.GuiShaderBloom;
import me.youngjibbit.utils.render.UiEffects;
import org.lwjgl.opengl.GL11;

public class StarParticle {
    private static final Random RANDOM = new Random();
    private float x, y;
    private float alpha;
    private float size;
    private float alphaChangeRate;
    float velocityY, velocityX;

    Theme currentTheme = applyTheme();
    int[] outlineColors = getStaticOutlineGradientColors(currentTheme);

    public Theme applyTheme()
    {
        return Theme.resolve(Pulsar.INSTANCE.getDefaultTheme());
    }



    public StarParticle(float x, float y, float size, float alphaChangeRate) {
        this.x = x;
        this.y = y;
        this.size = size;
        this.alphaChangeRate = alphaChangeRate;
        this.alpha = RANDOM.nextFloat();
        this.velocityY = (RANDOM.nextFloat() - 0.5f) * RANDOM.nextFloat();
        this.velocityX = (RANDOM.nextFloat() - 0.5f) * RANDOM.nextFloat();
    }

    private void resetPosition(int width, int height) {
        x = RANDOM.nextInt(Math.max(1, width));
        y = height + RANDOM.nextInt(100);
    }

    private long lastUpdate=System.nanoTime();
    public void update(int width, int height) {
        long now=System.nanoTime();float frames=Math.min(.05f,(now-lastUpdate)/1e9f)*60;lastUpdate=now;
        if(!UiEffects.motion())return;
        alpha += alphaChangeRate*frames;
        if (alpha > 1.0f) {
            alpha = 1.0f;
            alphaChangeRate = -alphaChangeRate;
        } else if (alpha < 0.01f) {
            alpha = 0.01f;
            alphaChangeRate = -alphaChangeRate;
        }
        y -= velocityY*frames;
        x -= velocityX*frames;
        if (y < -200 || y >= height + 200 || x <= -200 || x >= width + 200) {
            resetPosition(width, height);
        }
    }

    public void render(float mouseX, float mouseY, List<StarParticle> allParticles) {
        float dx = mouseX - x;
        float dy = mouseY - y;
        float parallaxFactor = UiEffects.motion()?0.04f:0;
        float newX = x + dx * parallaxFactor;
        float newY = y + dy * parallaxFactor;

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha);
        drawStar(newX, newY, size);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private static double distance(float x1, float y1, float x2, float y2) {
        return Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
    }

    private void drawStar(float x, float y, float size) {
        currentTheme = Theme.resolve(Theme.getDefaultTheme());
        int[] outlineColors = getStaticOutlineGradientColors(currentTheme);

        int themeColor1 = outlineColors[0]; // First gradient color
        int themeColor2 = outlineColors[1];
        long cT = (long)(UiEffects.time()*1000);
        float cycleDuration = 1500.0f;
        int interpolatedColor = interpolateTwoColorsSmoothly(themeColor1, themeColor2, cT, cycleDuration);
        float particleSize = Math.max(2.5f, Math.min(8.0f, size * 0.52f));
        GuiShaderBloom.queueRoundedRect(
                x - particleSize / 2.0f,
                y - particleSize / 2.0f,
                particleSize,
                particleSize,
                Math.max(2, (int) (particleSize / 2.0f)),
                UiEffects.alpha(interpolatedColor, alpha * 0.62f)
        );
    }

    public static List<StarParticle> getNearestParticles(float mouseX, float mouseY, List<StarParticle> allParticles, int count) {
        List<StarParticle> sortedParticles = new ArrayList<>(allParticles);
        sortedParticles.sort(Comparator.comparingDouble(p -> distance(mouseX, mouseY, p.x, p.y)));
        return sortedParticles.subList(0, Math.min(count, sortedParticles.size()));
    }

    public static void drawLinesToNearestParticles(float mouseX, float mouseY, List<StarParticle> nearestParticles) {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.4f);

        GL11.glBegin(GL11.GL_LINES);
        for (StarParticle particle : nearestParticles) {
            float distance = (float) Math.sqrt(Math.pow(mouseX - particle.x, 2) + Math.pow(mouseY - particle.y, 2));
            if (distance < 100) {
                float parallaxFactor = UiEffects.motion()?0.04f:0;
                float dx = mouseX - particle.x;
                float dy = mouseY - particle.y;
                float newX = particle.x + dx * parallaxFactor;
                float newY = particle.y + dy * parallaxFactor;
                GL11.glVertex2f(mouseX, mouseY);
                GL11.glVertex2f(newX, newY);
            }
        }
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }
    private static int interpolateTwoColorsSmoothly(int color1, int color2, long currentTime, float cycleDuration) {

        float progress = (currentTime % (long) cycleDuration) / cycleDuration;
        float factor = (float) (Math.sin(progress * 2 * Math.PI) * 0.5 + 0.5);

        int red1 = (color1 >> 16) & 0xFF;
        int green1 = (color1 >> 8) & 0xFF;
        int blue1 = color1 & 0xFF;

        int red2 = (color2 >> 16) & 0xFF;
        int green2 = (color2 >> 8) & 0xFF;
        int blue2 = color2 & 0xFF;

        int red = (int) (red1 + (red2 - red1) * factor);
        int green = (int) (green1 + (green2 - green1) * factor);
        int blue = (int) (blue1 + (blue2 - blue1) * factor);

        return (0xFF << 24) | (red << 16) | (green << 8) | blue;
        //return new Color(red << 16, green << 8, blue);
    }

    private int[] getStaticOutlineGradientColors(Theme theme)
    {
        Theme resolvedTheme = Theme.resolve(theme);
        int color1 = resolvedTheme.getPrimaryGradientRGB();
        int color2 = resolvedTheme.getSecondaryGradientRGB();

        return new int[]{
                color1,
                color2,
                color1,
                color2
        };
    }

}
