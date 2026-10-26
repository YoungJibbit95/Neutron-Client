package me.youngjibbit.utils;

import me.youngjibbit.theme.ThemeCatalog;
import net.minecraft.client.Minecraft;
import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Immutable theme shared by presets and custom definitions. Preset names remain config-compatible. */
public final class Theme {
    public static final Theme BlackLight=preset("BlackLight",0,new Color(183, 0, 255), new Color(0, 0, 0));
    public static final Theme Cotton_candy=preset("Cotton_candy",1,new Color(68, 211, 255), new Color(255, 66, 161));
    public static final Theme SynthWave=preset("SynthWave",2,new Color(0, 119, 255, 255), new Color(255, 0, 167, 255));
    public static final Theme Retro=preset("Retro",3,new Color(0, 255, 225, 255), new Color(255, 0, 203, 255));
    public static final Theme Cherry=preset("Cherry",4,new Color(255, 148, 209), new Color(244, 44, 127));
    public static final Theme BlackWhite=preset("BlackWhite",5,new Color(0, 0, 0), new Color(255, 255, 255));
    public static final Theme PinkLemonade=preset("PinkLemonade",6,new Color(255, 0, 234), new Color(255, 98, 0));
    public static final Theme Purple=preset("Purple",7,new Color(115, 0, 255), new Color(211, 90, 232));
    public static final Theme Void=preset("Void",8,new Color(115, 0, 255), new Color(255, 255, 255));
    public static final Theme Lavender=preset("Lavender",9,new Color(150, 62, 255), new Color(239, 145, 255));
    public static final Theme Berry=preset("Berry",10,new Color(234, 55, 255), new Color(255, 0, 113));
    public static final Theme PurpleHaze=preset("PurpleHaze",11,new Color(7, 255, 0), new Color(93, 0, 255));
    public static final Theme Vine=preset("Vine",12,new Color(28, 154, 0), new Color(48, 255, 4));
    public static final Theme Neon=preset("Neon",13,new Color(14, 255, 0), new Color(0, 180, 255));
    public static final Theme Descriptor=preset("Descriptor",14,new Color(0, 255, 236), new Color(68, 102, 250));
    public static final Theme Grayscale=preset("Grayscale",15,new Color(240, 240, 240), new Color(110, 110, 110));
    public static final Theme Flare=preset("Flare",16,new Color(255, 0, 0), new Color(245, 173, 49));
    public static final Theme Bonfire=preset("Bonfire",17,new Color(255, 128, 0, 255), new Color(255, 0, 0, 255));
    public static final Theme Rainbow=preset("Rainbow",18,new Color(255, 255, 255), new Color(255, 255, 255));
    private static final Theme[] PRESETS={BlackLight,Cotton_candy,SynthWave,Retro,Cherry,BlackWhite,PinkLemonade,Purple,Void,Lavender,Berry,PurpleHaze,Vine,Neon,Descriptor,Grayscale,Flare,Bonfire,Rainbow};
    public static final String[] themes=Arrays.stream(PRESETS).map(Theme::name).toArray(String[]::new);
    private final String id,displayName;
    private final java.util.List<Color> gradients;
    private final Color background,text,accent;
    private final boolean custom,vertical,animated;
    private final float speed,glowStrength,glowRadius,outlineStrength,outlineWidth;
    private final int ordinal;
    private Theme(String id,String displayName,int ordinal,boolean custom,java.util.List<Color> stops,boolean vertical,boolean animated,float speed,Color background,Color text,Color accent,float glowStrength,float glowRadius,float outlineStrength,float outlineWidth){
        this.id=Objects.requireNonNull(id);this.displayName=validName(displayName);this.ordinal=ordinal;this.custom=custom;
        if(stops==null||stops.size()!=2||stops.contains(null))throw new IllegalArgumentException("Exactly two color stops required");
        gradients=Collections.unmodifiableList(new ArrayList<>(stops));this.vertical=vertical;this.animated=animated;
        this.speed=range(speed,.02f,2,"speed");this.background=Objects.requireNonNull(background);this.text=Objects.requireNonNull(text);this.accent=Objects.requireNonNull(accent);
        this.glowStrength=range(glowStrength,0,1.5f,"glow strength");this.glowRadius=range(glowRadius,1,16,"glow radius");this.outlineStrength=range(outlineStrength,0,1,"outline strength");this.outlineWidth=range(outlineWidth,.4f,2.5f,"outline width");
    }
    private static Theme preset(String name,int ordinal,Color...colors){return new Theme(name,name,ordinal,false,Arrays.asList(colors),false,false,.18f,new Color(5,5,7,116),Color.WHITE,colors[0],.75f,9,.8f,1);}
    public static Theme custom(String id,String name,java.util.List<Color> stops,boolean vertical,boolean animated,float speed,Color background,Color text,Color accent,float glowStrength,float glowRadius,float outlineStrength,float outlineWidth){
        if(id==null||!id.startsWith("custom:")||!id.substring(7).equals(UUID.fromString(id.substring(7)).toString()))throw new IllegalArgumentException("Invalid custom theme ID");
        return new Theme(id,name,-1,true,stops,vertical,animated,speed,background,text,accent,glowStrength,glowRadius,outlineStrength,outlineWidth);
    }
    public static String validName(String name){if(name==null||name.trim().isEmpty()||name.trim().length()>48||name.chars().anyMatch(c->Character.isISOControl(c)))throw new IllegalArgumentException("Name must contain 1-48 printable characters");return name.trim();}
    private static float range(float n,float min,float max,String label){if(!Float.isFinite(n)||n<min||n>max)throw new IllegalArgumentException("Invalid "+label);return n;}
    public String name(){return id;}
    public String getDisplayName(){return displayName;}
    public int ordinal(){return ordinal;}
    public boolean isCustom(){return custom;}
    public boolean isVertical(){return vertical;}
    public boolean isAnimated(){return animated;}
    public float getAnimationSpeed(){return speed;}
    public Color getBackground(){return background;}
    public Color getTextColor(){return text;}
    public Color getAccentColor(){return accent;}
    public float getGlowStrength(){return glowStrength;}
    public float getGlowRadius(){return glowRadius;}
    public float getOutlineStrength(){return outlineStrength;}
    public float getOutlineWidth(){return outlineWidth;}
    public float phase(){return me.youngjibbit.utils.render.UiEffects.gradient(this,me.youngjibbit.utils.render.UiEffects.Channel.BORDER).phase;}
    public java.util.List<Color> getGradientColors(){return gradients;}
    public Color getPrimaryGradientColor(){return gradients.get(0);}
    public Color getSecondaryGradientColor(){return gradients.get(1);}
    public int getPrimaryGradientRGB(){return gradients.get(0).getRGB();}
    public int getSecondaryGradientRGB(){return gradients.get(1).getRGB();}
    public int getGradientRGB(float offset){
        if(custom){float t=animated?.5f-.5f*(float)Math.cos(2*Math.PI*(offset+phase())):Math.max(0,Math.min(1,offset));return interpolateRGB(getPrimaryGradientRGB(),getSecondaryGradientRGB(),t);}
        float t=offset-(float)Math.floor(offset);return interpolateRGB(getPrimaryGradientRGB(),getSecondaryGradientRGB(),t<.5f?t*2:2-t*2);
    }
    public int getPulseGradientRGB(long time,float duration,boolean reverse){if(custom&&!animated)return reverse?getSecondaryGradientRGB():getPrimaryGradientRGB();return pulseRGB(reverse?getSecondaryGradientRGB():getPrimaryGradientRGB(),reverse?getPrimaryGradientRGB():getSecondaryGradientRGB(),time,custom?1000/speed:duration);}
    public static Theme[] values(){return PRESETS.clone();}
    public static Theme valueOf(String name){for(Theme preset:PRESETS)if(preset.name().equals(name))return preset;throw new IllegalArgumentException("Unknown preset: "+name);}
    public static Theme resolve(Theme theme){return theme==null?getDefaultTheme():theme;}
    public static Theme getDefaultTheme(){return ThemeCatalog.global().current();}
    public static void setDefaultTheme(Theme theme){if(theme!=null)ThemeCatalog.global().select(theme.name());}
    public static int interpolateRGB(int a,int b,float t){t=Math.max(0,Math.min(1,t));int c=0;for(int shift:new int[]{24,16,8,0})c|=(int)(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t)<<shift;return c;}
    public static int pulseRGB(int a,int b,long time,float duration){long period=Math.max(1,(long)duration);return interpolateRGB(a,b,(float)(Math.sin(time%period/(double)period*Math.PI*2)*.5+.5));}
    public static Color convert(Color a,Color b,double t){return new Color(interpolateRGB(a.getRGB(),b.getRGB(),(float)t),true);}
    // Legacy file is read for migration; the config adapter owns new selection persistence.
    public static File legacyFile(){Minecraft mc=Minecraft.getMinecraft();File data=mc!=null&&mc.mcDataDir!=null?mc.mcDataDir:new File(System.getProperty("user.home"),".minecraft");return new File(data,"pulsar/theme/theme_config.txt");}
    public static boolean isConfigFilePresent(){return legacyFile().isFile();}
    public static void loadDefaultTheme(){try{if(isConfigFilePresent()){String ref=new String(Files.readAllBytes(legacyFile().toPath()),StandardCharsets.UTF_8).trim();ThemeCatalog.global().select(ref);}}catch(IOException ex){System.err.println("Theme: "+ex.getMessage());}}
    public static void saveDefaultTheme(Theme theme){if(theme!=null)me.youngjibbit.theme.ThemeRuntime.select(theme.name());}
    public static void saveCurrentTheme(Theme theme){saveDefaultTheme(theme);}
    public static void applyTheme(Theme theme){saveDefaultTheme(theme);}
    @Override public String toString(){return displayName;}
}
