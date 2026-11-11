package me.youngjibbit.module.impl.render;

import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL13;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;

/** Isolated production painters: item-atlas pixels and card-only material regions, without other HUDs. */
final class CompactHudMaterialCheck {
    private CompactHudMaterialCheck(){}
    static void run(TargetHUD target,SessionInfo session,ArmorHUD armor)throws Exception{
        Minecraft mc=SessionInfoRenderCheck.mc;
        ClientSettings settings=ClientSettings.current();
        String targetMode=target.getModeSetting("Mode").getValue(),sessionMode=session.getModeSetting("Mode").getValue();
        String armorMode=armor.getModeSetting("Mode").getValue(),armorLayout=armor.getModeSetting("Layout").getValue();
        String armorDurability=armor.getModeSetting("Durability").getValue();
        String targetSource=target.getModeSetting("Effects Source").getValue();
        String sessionSource=session.getModeSetting("Effects Source").getValue();
        String armorSource=armor.getModeSetting("Effects Source").getValue();
        String armorSubstance=armor.getModeSetting("Local Rectangle Substance").getValue();
        boolean empty=armor.getBooleanSetting("Empty Slots").getValue(),held=armor.getBooleanSetting("Held Item").getValue();
        String substance=settings.surface.getValue(),motion=settings.gradientMotion.getValue();
        double opacity=settings.surfaceOpacity.getValue(),strength=settings.gradientFillStrength.getValue();
        boolean border=settings.border.getValue(),glow=settings.glow.getValue(),blur=settings.blur.getValue();
        boolean reduced=settings.reducedMotion.getValue();
        try{
            settings.reducedMotion.setValue(true);settings.gradientMotion.setValue("Static");
            settings.border.setValue(false);settings.glow.setValue(false);settings.blur.setValue(true);
            settings.gradientFillStrength.setValue(.65);settings.surfaceOpacity.setValue(1d);
            armor.getModeSetting("Layout").setValue("Horizontal");
            armor.getModeSetting("Durability").setValue("None");
            armor.getBooleanSetting("Held Item").setValue(true);
            armor.getBooleanSetting("Empty Slots").setValue(false);
            armor.getModeSetting("Effects Source").setValue("Local");
            armor.getModeSetting("Local Rectangle Substance").setValue("Legacy");
            for(String design:new String[]{"Modern","Creative"}){
                armor.getModeSetting("Mode").setValue(design);
                itemPixels(armor,mc,design);
            }
            target.getModeSetting("Effects Source").setValue("Global");
            session.getModeSetting("Effects Source").setValue("Global");
            armor.getModeSetting("Effects Source").setValue("Global");
            armor.getBooleanSetting("Empty Slots").setValue(true);
            for(String design:new String[]{"Modern","Creative"}){
                target.getModeSetting("Mode").setValue(design);session.getModeSetting("Mode").setValue(design);
                armor.getModeSetting("Mode").setValue(design);
                ScaledResolution sr=new ScaledResolution(mc);
                TargetHUD.Frame t=target.layout(IngameHudRenderCheck.player,sr.getScaledWidth(),sr.getScaledHeight()).positioned(32,24);
                SessionInfo.Frame s=session.layout(SessionInfoRenderCheck.sample(),"Fixture",sr.getScaledWidth(),sr.getScaledHeight());
                s.x=32;s.y=24;
                ArmorHUD.Frame a=armor.layout(new ItemStack[4],null,sr.getScaledWidth(),sr.getScaledHeight());
                a.x=32;a.y=24;a.background=true;a.blur=true;a.bar=false;
                Runnable[] painters={()->target.paint(t),()->session.paint(s),()->armor.paint(a)};
                // Regions deliberately exclude text, semantic icons, bars, borders and real item pixels.
                float[][] regions={{t.x+58,t.y+13,68,2},
                        {s.x+("Creative".equals(design)?72:38),s.y+15,65,3},
                        {a.x+7*a.scale,a.y+("Creative".equals(design)?17:22)*a.scale,6*a.scale,1*a.scale}};
                String[] names={"target","session","armor"};
                for(int hud=0;hud<painters.length;hud++){
                    SessionInfoRenderCheck.backdrop();BufferedImage clean=SessionInfoRenderCheck.capture();
                    double[] opacities={.25d,1d};
                    BufferedImage[][] materials=new BufferedImage[opacities.length][4];int index=0;
                    for(String material:new String[]{"Clear","Gradient","Blur","Liquid Glass"}){
                        settings.surface.setValue(material);settings.surfaceOpacity.setValue(0d);
                        BufferedImage transparent=render(painters[hud]);
                        int changed=changed(clean,transparent,regions[hud],1);
                        if(changed!=0)failure(transparent,"compact-opacity-"+names[hud]+"-"+design+"-"+material.replace(' ','-')+".png");
                        SessionInfoRenderCheck.require(changed==0,names[hud]+" "+design+" "+material+" ignored surface opacity zero");
                        // At .25, a literal alpha-scaled fallback makes Clear and Gradient identical.
                        // Pairwise card pixels detect that loss without repeating any production material logic.
                        for(int opacityIndex=0;opacityIndex<opacities.length;opacityIndex++){
                            double visibleOpacity=opacities[opacityIndex];
                            settings.surfaceOpacity.setValue(visibleOpacity);
                            BufferedImage visible=render(painters[hud]);
                            SessionInfoRenderCheck.require(changed(clean,visible,regions[hud],1)>0,
                                    names[hud]+" "+design+" "+material+" did not draw its card material at opacity "+visibleOpacity);
                            for(int prior=0;prior<index;prior++)
                                SessionInfoRenderCheck.require(changed(materials[opacityIndex][prior],visible,regions[hud],1)>0,
                                        names[hud]+" "+design+" materials "+prior+" and "+material+" produced the same card pixels at opacity "+visibleOpacity);
                            materials[opacityIndex][index]=visible;
                        }
                        index++;
                    }
                }
            }
            SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR&&UiEffects.scene()==null&&!GuiShaderBloom.isBatching(),
                    "Compact material regressions leaked GL/scene/batch state");
        }finally{
            target.getModeSetting("Mode").setValue(targetMode);session.getModeSetting("Mode").setValue(sessionMode);
            armor.getModeSetting("Mode").setValue(armorMode);armor.getModeSetting("Layout").setValue(armorLayout);
            armor.getModeSetting("Durability").setValue(armorDurability);
            target.getModeSetting("Effects Source").setValue(targetSource);session.getModeSetting("Effects Source").setValue(sessionSource);
            armor.getModeSetting("Effects Source").setValue(armorSource);armor.getModeSetting("Local Rectangle Substance").setValue(armorSubstance);
            armor.getBooleanSetting("Empty Slots").setValue(empty);armor.getBooleanSetting("Held Item").setValue(held);
            settings.surface.setValue(substance);settings.surfaceOpacity.setValue(opacity);
            settings.gradientFillStrength.setValue(strength);settings.gradientMotion.setValue(motion);
            settings.border.setValue(border);settings.glow.setValue(glow);settings.blur.setValue(blur);
            settings.reducedMotion.setValue(reduced);
        }
        System.out.println("PASS: compact armor item-region pixels match textured vanilla; isolated Target/Session/Armor cards honor four substances and opacity 0/.25/1 in Modern/Creative");
    }

    private static void itemPixels(ArmorHUD armor,Minecraft mc,String design)throws Exception{
        ScaledResolution sr=new ScaledResolution(mc);
        ItemStack sword=new ItemStack(Items.diamond_sword);
        ArmorHUD.Frame frame=armor.layout(new ItemStack[4],sword,sr.getScaledWidth(),sr.getScaledHeight());
        frame.x=32;frame.y=24;frame.background=false;frame.blur=false;frame.bar=false;
        SessionInfoRenderCheck.require(frame.cells.size()==1,"Item oracle requires one real held stack");
        ArmorHUD.Cell cell=frame.cells.get(0);
        float ix=cell.x+(cell.w-16)/2,iy=cell.y+1;
        float[] region={frame.x+ix*frame.scale,frame.y+iy*frame.scale,16*frame.scale,16*frame.scale};
        SessionInfoRenderCheck.backdrop();BufferedImage clean=SessionInfoRenderCheck.capture();
        BufferedImage actual=render(()->armor.paint(frame));
        SessionInfoRenderCheck.backdrop();
        try(RenderState state=new RenderState()){
            state.screenSpace();glTranslatef(frame.x,frame.y,0);glScalef(frame.scale,frame.scale,1);
            CompactHudVisuals.begin(frame.scale);
            // Independent vanilla reference explicitly establishes the texture precondition.
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GL13.glActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);
            GlStateManager.enableDepth();GlStateManager.depthFunc(GL_LEQUAL);GlStateManager.depthMask(true);
            GlStateManager.enableAlpha();RenderHelper.enableGUIStandardItemLighting();
            float oldZ=mc.getRenderItem().zLevel;
            try{
                mc.getRenderItem().zLevel=100;
                mc.getRenderItem().renderItemAndEffectIntoGUI(sword,Math.round(ix),Math.round(iy));
            }finally{mc.getRenderItem().zLevel=oldZ;RenderHelper.disableStandardItemLighting();}
        }
        BufferedImage expected=SessionInfoRenderCheck.capture();
        int mismatch=changed(expected,actual,region,1);
        if(mismatch!=0)failure(actual,"compact-untextured-item-"+design+".png");
        SessionInfoRenderCheck.require(changed(clean,expected,region,1)>20&&colors(clean,expected,region)>=3,
                "Vanilla item reference has no textured atlas pixels");
        SessionInfoRenderCheck.require(mismatch==0,design+" armor item-region differs from correctly textured vanilla");
    }
    private static BufferedImage render(Runnable painter){
        SessionInfoRenderCheck.backdrop();GuiShaderBloom.beginBatch();painter.run();GuiShaderBloom.flushBatch(2,3);
        return SessionInfoRenderCheck.capture();
    }
    private static int[] bounds(BufferedImage image,float[] region){
        Minecraft mc=SessionInfoRenderCheck.mc;ScaledResolution sr=new ScaledResolution(mc);
        float sx=mc.displayWidth/(float)sr.getScaledWidth_double(),sy=mc.displayHeight/(float)sr.getScaledHeight_double();
        int x0=Math.max(0,(int)Math.ceil(region[0]*sx-.5f)),y0=Math.max(0,(int)Math.ceil(region[1]*sy-.5f));
        int x1=Math.min(image.getWidth(),(int)Math.ceil((region[0]+region[2])*sx-.5f));
        int y1=Math.min(image.getHeight(),(int)Math.ceil((region[1]+region[3])*sy-.5f));
        SessionInfoRenderCheck.require(x1>x0&&y1>y0,"Material oracle region is empty");
        return new int[]{x0,y0,x1,y1};
    }
    private static int distance(int a,int b){
        return Math.max(Math.abs((a>>16&255)-(b>>16&255)),
                Math.max(Math.abs((a>>8&255)-(b>>8&255)),Math.abs((a&255)-(b&255))));
    }
    private static int changed(BufferedImage a,BufferedImage b,float[] region,int tolerance){
        int[] bounds=bounds(a,region);int count=0;
        for(int y=bounds[1];y<bounds[3];y++)for(int x=bounds[0];x<bounds[2];x++)
            if(distance(a.getRGB(x,y),b.getRGB(x,y))>tolerance)count++;
        return count;
    }
    private static int colors(BufferedImage clean,BufferedImage image,float[] region){
        int[] bounds=bounds(image,region);Set<Integer> colors=new HashSet<>();
        for(int y=bounds[1];y<bounds[3];y++)for(int x=bounds[0];x<bounds[2];x++)
            if(distance(clean.getRGB(x,y),image.getRGB(x,y))>1)colors.add(image.getRGB(x,y));
        return colors.size();
    }
    private static void failure(BufferedImage image,String name)throws Exception{
        ImageIO.write(image,"png",new File(IngameHudRenderCheck.OUT,name));
    }
}
