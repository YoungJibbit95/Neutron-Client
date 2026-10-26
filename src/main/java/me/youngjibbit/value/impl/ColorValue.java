package me.youngjibbit.value.impl;

import com.google.gson.JsonObject;
import lombok.Getter;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.render.ColorUtil;
import me.youngjibbit.value.Value;

import java.awt.*;

@Getter
public class ColorValue extends Value<Color>
{
    private float hue = 0;
    private float saturation = 1;
    private float brightness = 1;
    private Rainbow rainbow = null;

    public ColorValue(String name,Module parent, Color defaultColor) {
        super(name,parent, defaultColor);
        this.setColor(defaultColor);
    }

    public Color getColor() {
        Color color = isRainbow() ? getRainbow().getColor() : getValue();
        return new Color((color.getRGB() & 0x00FFFFFF) | (getValue().getAlpha() << 24), true);
    }

    public Color getAltColor() {
        Color color = isRainbow() ? getRainbow().getColor(40) : ColorUtil.darker(getColor(), .6f);
        return new Color((color.getRGB() & 0x00FFFFFF) | (getValue().getAlpha() << 24), true);
    }


    public void setColor(Color color) {
        setValue(color);
    }

    private void updateHSB(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
    }

    public void setColor(float hue, float saturation, float brightness) {
        Color color = Color.getHSBColor(hue, saturation, brightness);
        // Keep the editor's hue even for gray/black colors, where RGB cannot encode it.
        this.hue = hue;
        this.saturation = saturation;
        this.brightness = brightness;
        super.setValue(new Color((color.getRGB() & 0x00FFFFFF) | (getValue().getAlpha() << 24), true));
    }

    public double getHue() {
        return hue;
    }

    public void setHue(float hue) {
        setColor(hue, saturation, brightness);
    }

    public double getSaturation() {
        return saturation;
    }

    public void setSaturation(float saturation) {
        setColor(hue, saturation, brightness);
    }

    public double getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        setColor(hue, saturation, brightness);
    }

    public String getHexCode() {
        Color color = getColor();
        return String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    public Rainbow getRainbow() {
        return rainbow;
    }

    public void setRainbow(boolean rainbow) {
        if (rainbow == isRainbow()) return;
        if (rainbow) {
            this.rainbow = new Rainbow(this::notifyChanged);
        } else {
            this.rainbow = null;
        }
        notifyChanged();
    }

    public boolean isRainbow() {
        return rainbow != null;
    }

    public Object getConfigValue() {
        return isRainbow() ? getRainbow().getJsonObject() : getColor().getRGB();
    }

    @Override
    public void setValue(Color value) {
        if (value == null) throw new IllegalArgumentException("Color cannot be null.");
        updateHSB(value);
        super.setValue(value);
    }

    public void setConfigState(Color color, boolean animated, float saturation, int speed) {
        boolean animationChanged = animated != isRainbow() || (animated &&
                (rainbow.getSaturation() != saturation || rainbow.getSpeed() != speed));
        boolean colorChanged = !getValue().equals(color);
        if (animated) {
            rainbow = new Rainbow(this::notifyChanged);
            rainbow.saturation = saturation;
            rainbow.speed = speed;
        } else rainbow = null;
        setValue(color);
        if (animationChanged && !colorChanged) notifyChanged();
    }

    public static class Rainbow {
        private float saturation = 1;
        private int speed = 15;
        private final Runnable changed;

        public Rainbow() { this(() -> {}); }
        private Rainbow(Runnable changed) { this.changed = changed; }

        public Color getColor() {
            return getColor(0);
        }

        public Color getColor(int index) {
            return ColorUtil.rainbow(speed, index, saturation, 1, 1);
        }

        public JsonObject getJsonObject() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("saturation", saturation);
            jsonObject.addProperty("speed", speed);
            return jsonObject;
        }

        public float getSaturation() {
            return Math.max(0, Math.min(1, saturation));
        }

        public void setSaturation(float saturation) {
            if (this.saturation == saturation) return;
            this.saturation = saturation;
            changed.run();
        }

        public int getSpeed() {
            return speed;
        }

        public void setSpeed(int speed) {
            if (this.speed == speed) return;
            this.speed = speed;
            changed.run();
        }
    }
}
