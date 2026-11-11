package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.GuiClip;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.gui.LiquidGlass;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.theme.ThemeCatalog;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.impl.*;
import me.zero.alpine.listener.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;
import java.awt.Color;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;

/** A read-only equipment snapshot, laid out once for effects, items, labels and chat dragging. */
@ModuleInfo(name="ArmorHUD", category=Category.Render)
public class ArmorHUD extends Module {
    private final ComboValue layout=new ComboValue("Layout",this,new String[]{"Vertical","Horizontal"},0);
    private final ComboValue durability=new ComboValue("Durability",this,new String[]{"Percent","Value","Both","None"},0);
    private final CheckBoxValue bar=new CheckBoxValue("Durability Bar",this,true);
    private final CheckBoxValue held=new CheckBoxValue("Held Item",this,true);
    private final CheckBoxValue empty=new CheckBoxValue("Empty Slots",this,true);
    private final CheckBoxValue background=new CheckBoxValue("Background",this,true);
    private final CheckBoxValue blur=new CheckBoxValue("Blur",this,false);
    private final CheckBoxValue glow=new CheckBoxValue("Glow",this,true);
    private final SliderValue scale=new SliderValue("Scale",this,1.0,0.65,1.6);
    private final ArrayListSurface surface=new ArrayListSurface(this);
    private final HudSpring[] durabilitySprings=new HudSpring[5];
    private final CompactHudMotion[] slotChanges=new CompactHudMotion[5];
    private final Object[] previousItems=new Object[5];
    private static final String[] SLOT_NAMES={"Head","Body","Legs","Feet","Hand"};
    private long durabilityFrame,sceneEpoch;
    private Object durabilityWorld;
    { for(int i=0;i<durabilitySprings.length;i++){durabilitySprings[i]=new HudSpring();slotChanges[i]=new CompactHudMotion();} }
    private void clearDurabilityMotion(){
        for(HudSpring spring:durabilitySprings)spring.reset();
        for(CompactHudMotion motion:slotChanges)motion.reset();Arrays.fill(previousItems,null);
        durabilityFrame=0;sceneEpoch++;
    }
    private final SliderValue positionX=new SliderValue("Position X",this,100.0,-16384,16384);
    private final SliderValue positionY=new SliderValue("Position Y",this,300.0,-16384,16384);
    // Append the cosmetic choice after every pre-existing Value registration.
    private final ComboValue mode=new ComboValue("Mode",this,new String[]{"Basic","Modern","Creative"},0);
    private double lastPositionX=100,lastPositionY=300;
    @Override public void onValueChange(){if(positionX!=null&&positionX.getValue()!=lastPositionX){lastPositionX=positionX.getValue();x=(float)lastPositionX;}if(positionY!=null&&positionY.getValue()!=lastPositionY){lastPositionY=positionY.getValue();y=(float)lastPositionY;}}
    public void resetPosition(){positionX.reset();positionY.reset();x=100;y=300;dragging=false;me.youngjibbit.utils.render.HudDrag.cancel(this);}

    private float x=100,y=300,width,height,grabX,grabY;
    private boolean dragging,previousButton;
    @Subscribe public final Listener<Event2D> render=new Listener<>(e->{if(isToggled())renderArmorHUD();});
    @Subscribe private final Listener<me.youngjibbit.event.world.WorldEvent> world=new Listener<>(e->{
        clearDurabilityMotion();durabilityWorld=null;surface.close();dragging=false;HudDrag.cancel(this);
    });
    @Override public void onDisable(){dragging=false;clearDurabilityMotion();durabilityWorld=null;me.youngjibbit.utils.render.HudDrag.cancel(this);surface.close();super.onDisable();}
    public void renderArmorHUD(){
        if(!me.youngjibbit.module.impl.client.HUD.visible()){dragging=false;clearDurabilityMotion();HudDrag.cancel(this);surface.close();return;}
        if(mc.thePlayer==null||mc.gameSettings.showDebugInfo){dragging=false;clearDurabilityMotion();HudDrag.cancel(this);if(mc.thePlayer==null){durabilityWorld=null;surface.close();}previousButton=Mouse.isCreated()&&Mouse.isButtonDown(0);return;}
        ScaledResolution sr=new ScaledResolution(mc);
        Frame frame=layout(mc.thePlayer.inventory.armorInventory,mc.thePlayer.getHeldItem(),sr.getScaledWidth(),sr.getScaledHeight());
        boolean down=Mouse.isCreated()&&Mouse.isButtonDown(0);
        int mx=Mouse.isCreated()?Mouse.getX()/sr.getScaleFactor():-1;
        int my=Mouse.isCreated()?(mc.displayHeight-Mouse.getY()-1)/sr.getScaleFactor():-1;
        interact(mc.currentScreen instanceof GuiChat,down,mx,my,sr.getScaledWidth(),sr.getScaledHeight());
        frame.x=x;frame.y=y;paint(frame);
    }
    Frame layout(ItemStack[] armor,ItemStack hand,int sw,int sh){
        if(durabilityWorld!=mc.theWorld){durabilityWorld=mc.theWorld;clearDurabilityMotion();surface.close();}
        long now=System.nanoTime();
        float dt=durabilityFrame==0||now<=durabilityFrame?0:
                Math.min(.05f,(now-durabilityFrame)/1_000_000_000f);
        durabilityFrame=now;
        ClientSettings settings=ClientSettings.current();
        boolean animations=settings==null||settings.animations.getValue();
        boolean reduced=settings!=null&&settings.reducedMotion.getValue();
        String intensity=settings==null?"Balanced":settings.motionIntensity.getValue();
        String design=mode.getValue();
        boolean compact=ThemeMode.APPROACH_TWO.equals(ThemeMode.selected())||"Modern".equals(design)||"Creative".equals(design);
        TTFFontRenderer font=Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Bold "+(compact?"14":"16"));
        Frame f=new Frame(font);
        boolean horizontal=layout.getValue().equals("Horizontal");
        f.design=design;
        f.modern=ThemeMode.APPROACH_TWO.equals(ThemeMode.selected())||"Modern".equals(design)||"Creative".equals(design);
        for(int i=0;i<5;i++){
            if(i==4&&!held.getValue())continue;
            ItemStack item=i==4?hand:armor.length>3-i?armor[3-i]:null;
            if(item==null&&!empty.getValue())continue;
            ItemStack copy=item==null?null:item.copy();
            Object itemType=copy==null?null:copy.getItem();
            if(previousItems[i]!=itemType){durabilitySprings[i].reset();previousItems[i]=itemType;}
            long observed=copy==null?0:((long)net.minecraft.item.Item.getIdFromItem(copy.getItem())<<32)
                    ^((long)copy.getItemDamage()<<8)^copy.stackSize;
            float pulse=slotChanges[i].step(observed,dt,animations,reduced,intensity);
            float displayRatio=1;
            if(copy!=null&&copy.getMaxDamage()>0){
                int max=copy.getMaxDamage();
                float actual=Math.max(0,Math.min(1,(max-copy.getItemDamage())/(float)max));
                displayRatio=Math.max(0,Math.min(1,
                        durabilitySprings[i].step(actual,dt,animations,reduced,intensity)));
            }else durabilitySprings[i].reset();
            String label=copy==null?SLOT_NAMES[i]:formatDurability(copy,displayRatio);
            f.cells.add(new Cell(copy,label,displayRatio,i,pulse));
        }
        if(f.modern)return compactLayout(f,horizontal,sw,sh);
        // Occupied vertical rows keep the equipment order. Empty slots share one compact strip.
        float cursor=6,max=0,emptyWidth=0;
        boolean hasLabels=false,hasBars=false;
        for(Cell cell:f.cells){
            TTFFontRenderer labelFont=cell.item==null?f.emptyFont:font;
            float textWidth=labelFont.getWidth(cell.label);
            cell.w=horizontal||cell.item==null?Math.max(f.modern?44:24,textWidth+(f.modern?13:8)):
                    Math.max(f.modern?52:42,30+textWidth);
            hasLabels|=!cell.label.isEmpty();hasBars|=bar.getValue()&&cell.item!=null&&cell.item.getMaxDamage()>0;
            if(horizontal){cell.x=cursor;cell.y=6;cursor+=cell.w+(f.modern?3:0);}
            else if(cell.item!=null){cell.x=6;cell.y=cursor;
                cursor+=f.modern?28:bar.getValue()&&cell.item.getMaxDamage()>0?24:20;
                max=Math.max(max,cell.w);}
            else emptyWidth+=cell.w;
        }
        if(horizontal){f.width=cursor+6;f.height=f.modern?52:12+16+(hasLabels?11:0)+(hasBars?4:0);}
        else{
            max=Math.max(max,emptyWidth);float emptyX=6;
            for(Cell cell:f.cells)if(cell.item==null){cell.x=emptyX;cell.y=cursor;cell.mini=true;emptyX+=cell.w;}else cell.w=max;
            f.width=max+12;f.height=cursor+6+(emptyWidth>0?27:0);
        }
        if(f.cells.isEmpty()){f.width=0;f.height=0;}
        f.scale=Math.min(scale.getValue().floatValue(),Math.min((sw-8)/Math.max(1,f.width),(sh-8)/Math.max(1,f.height)));
        f.scale=Math.max(.01f,f.scale);width=f.width*f.scale;height=f.height*f.scale;
        clamp(sw,sh);f.x=x;f.y=y;f.horizontal=horizontal;
        f.theme=Theme.resolve(f.modern?ThemeCatalog.global().current():Theme.getDefaultTheme());
        f.background=background.getValue();f.blur=blur.getValue();
        if("Basic".equals(design))f.glow=!f.modern&&glow.getValue();
        else f.glow=glow.getValue();
        f.bar=bar.getValue();return f;
    }
    private Frame compactLayout(Frame f,boolean horizontal,int sw,int sh){
        boolean[] occupied=new boolean[f.cells.size()];float[] labels=new float[occupied.length];
        for(int i=0;i<occupied.length;i++){
            Cell c=f.cells.get(i);occupied[i]=c.item!=null;
            labels[i]=(occupied[i]?f.font:f.emptyFont).getWidth(c.label);
        }
        CreativeHudLayout.Armor geometry=new CreativeHudLayout.Armor(horizontal,occupied,labels);
        float padding="Creative".equals(f.design)?0:3;
        for(int i=0;i<occupied.length;i++){
            CreativeHudLayout.Rect r=geometry.cells[i];Cell c=f.cells.get(i);
            c.x=r.x+padding;c.y=r.y+padding;c.w=r.width;c.h=r.height;c.mini=!occupied[i];
        }
        f.width=occupied.length==0?0:geometry.width+padding*2;
        f.height=occupied.length==0?0:geometry.height+padding*2;f.horizontal=horizontal;
        f.scale=Math.min(scale.getValue().floatValue(),Math.min(Math.max(1,sw-8)/Math.max(1,f.width),
                Math.max(1,sh-8)/Math.max(1,f.height)));
        width=f.width*f.scale;height=f.height*f.scale;
        clamp(sw,sh);f.x=x;f.y=y;
        f.theme=Theme.resolve(ThemeCatalog.global().current());
        f.background=background.getValue();f.blur=blur.getValue();f.glow=false;f.bar=bar.getValue();
        return f;
    }

    /** Shared content geometry: Modern groups the rail; Creative uses detached round tabs. */
    private void paintCompact(Frame f){
        final long epoch=sceneEpoch;
        GuiShaderBloom.afterBatch(()->{
            if(epoch!=sceneEpoch)return;
            surface.withScene(f.theme,f.background,f.blur,3,policy->{
            try(RenderState state=new RenderState()){
                state.screenSpace();
                try(GuiClip clip=GuiClip.push(new ClipRect(f.x,f.y,f.width*f.scale,f.height*f.scale))){
                    glTranslatef(f.x,f.y,0);glScalef(f.scale,f.scale,1);
                    boolean creative="Creative".equals(f.design);
                    if(!creative){
                        if(policy.blur&&(!policy.background||!UiEffects.material()))UiEffects.drawBlurredRect(0,0,f.width,f.height,10,Color.WHITE,3,1);
                        if(policy.background){
                            if(UiEffects.material())UIUtils.drawSceneRound(0,0,f.width,f.height,10);
                            else UIUtils.drawRound(0,0,f.width,f.height,10,new Color(17,23,34,Math.round(232*policy.opacity)));
                            float radius=Math.max(0,UiEffects.radius(10,f.width,f.height)-1);
                            LiquidGlass.highlight(1,1,Math.max(1,f.width-2),Math.max(1,f.height-2),radius,policy.opacity);
                            LiquidGlass.border(1,1,Math.max(1,f.width-2),Math.max(1,f.height-2),radius,f.theme,false,policy.opacity);
                        }
                    }
                    for(Cell cell:f.cells){
                        if(creative){
                            if(policy.blur&&(!policy.background||!UiEffects.material()))UiEffects.drawBlurredRect(cell.x,cell.y,cell.w,cell.h,8,Color.WHITE,3,1);
                            if(policy.background){
                                if(UiEffects.material())UIUtils.drawSceneRound(cell.x,cell.y,cell.w,cell.h,8);
                                else UIUtils.drawRound(cell.x,cell.y,cell.w,cell.h,8,
                                        new Color(17,23,34,Math.round(232*policy.opacity)));
                                float radius=UiEffects.radius(8,cell.w,cell.h);
                                LiquidGlass.highlight(cell.x,cell.y,cell.w,cell.h,radius,policy.opacity);
                                LiquidGlass.border(cell.x,cell.y,cell.w,cell.h,radius,f.theme,false,policy.opacity);
                            }
                        }else if(policy.background)UIUtils.drawDecorationRound(cell.x,cell.y,cell.w,cell.h,7,
                                new Color(133,160,195,Math.round((cell.item==null?12:18)*policy.opacity)));
                    }
                    CompactHudVisuals.begin(f.scale);
                    for(Cell cell:f.cells)if(cell.item==null)
                        CompactHudVisuals.equipment(cell.slot,cell.x+(cell.w-12)/2,cell.y+(cell.h-12)/2,12,0xff8196ae,.85f);
                    float oldZ=mc.getRenderItem().zLevel;
                    try{
                        for(Cell cell:f.cells){
                            if(cell.item==null)continue;
                            float ix=cell.x+(f.horizontal?(cell.w-16)/2:3),iy=cell.y+1;
                            glPushMatrix();
                            try{
                                float squeeze=1-cell.pulse*.07f;
                                glTranslatef(ix+8,iy+8,0);glScalef(squeeze,squeeze,1);glTranslatef(-ix-8,-iy-8,0);
                                // Semantic glyphs are untextured; vanilla RenderItem only binds its atlas.
                                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
                                org.lwjgl.opengl.GL13.glActiveTexture(OpenGlHelper.defaultTexUnit);
                                GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);
                                GlStateManager.enableDepth();GlStateManager.depthFunc(GL_LEQUAL);
                                GlStateManager.depthMask(true);GlStateManager.enableAlpha();
                                RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().zLevel=100;
                                mc.getRenderItem().renderItemAndEffectIntoGUI(cell.item,Math.round(ix),Math.round(iy));
                            }finally{glPopMatrix();}
                            RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.disableAlpha();
                            String label=fitLabel(cell.label,f.font,cell.w-(f.horizontal?6:29));
                            float tx=f.horizontal?cell.x+(cell.w-f.font.getWidth(label))/2:cell.x+26;
                            f.font.drawString(label,tx,cell.y+(f.horizontal?18:3),0xffeaf2fc);
                        }
                        CompactHudVisuals.begin(f.scale);
                        for(Cell cell:f.cells)if(f.bar&&cell.item!=null&&cell.item.getMaxDamage()>0){
                            float bx=cell.x+(f.horizontal?4:26),by=cell.y+(f.horizontal?27:17);
                            float bw=Math.max(0,cell.w-(f.horizontal?8:32));
                            CompactHudVisuals.line(bx,by,bx+bw,by,2,0xff96a9c2,.22f);
                            if(cell.durabilityRatio>0)CompactHudVisuals.line(bx,by,bx+bw*cell.durabilityRatio,by,2,
                                    durabilityColor(f.theme,cell.durabilityRatio),1);
                        }
                    }finally{mc.getRenderItem().zLevel=oldZ;}
                }
            }
            });
        });
    }
    private static String fitLabel(String text,TTFFontRenderer font,float available){
        if(font.getWidth(text)<=available)return text;
        int end=text.length();while(end>0&&font.getWidth(text.substring(0,end)+"...")>available)
            end=text.offsetByCodePoints(end,-1);
        return end==0?"":text.substring(0,end)+"...";
    }

    private String formatDurability(ItemStack item){
        int max=item.getMaxDamage();
        float ratio=max<=0?1:Math.max(0,Math.min(1,(max-item.getItemDamage())/(float)max));
        return formatDurability(item,ratio);
    }
    private String formatDurability(ItemStack item,float shownRatio){
        int max=item.getMaxDamage();if(max<=0)return item.stackSize>1?Integer.toString(item.stackSize):"";
        int remaining=Math.max(0,Math.min(max,Math.round(max*shownRatio)));int percent=(int)(100L*remaining/max);
        switch(durability.getValue()){case "Value":return remaining+"/"+max;case "Both":return remaining+" ("+percent+"%)";case "None":return "";default:return percent+"%";}
    }
    void interact(boolean chat,boolean down,float mx,float my,int sw,int sh){
        boolean was=dragging;
        dragging=HudDrag.capture(this,chat,down,!previousButton,HudDrag.roundedContains(mx,my,x,y,width,height,UiEffects.radius(5,width,height)));
        if(dragging&&!was){grabX=mx-x;grabY=my-y;}
        if(dragging){x=me.youngjibbit.module.impl.client.HUD.snap(mx-grabX);y=me.youngjibbit.module.impl.client.HUD.snap(my-grabY);clamp(sw,sh);positionX.setValue((double)x);positionY.setValue((double)y);}previousButton=down;
    }
    private void clamp(int sw,int sh){
        if(!me.youngjibbit.module.impl.client.HUD.bounded())return;
        float insetX=Math.min(4,Math.max(0,(sw-width)/2));
        float insetY=Math.min(4,Math.max(0,(sh-height)/2));
        x=Math.max(insetX,Math.min(x,sw-width-insetX));y=Math.max(insetY,Math.min(y,sh-height-insetY));
    }
    void paint(Frame f){
        if(f.cells.isEmpty())return;
        final boolean modern="Modern".equals(f.design)||f.modern;
        if(f.modern){paintCompact(f);return;}
        final long epoch=sceneEpoch;
        GuiShaderBloom.afterBatch(()->{
            if(epoch!=sceneEpoch)return;
            surface.panel(f.x,f.y,f.width*f.scale,f.height*f.scale,
                (f.modern?9:5)*f.scale,f.theme,f.background,f.blur,f.glow,
                f.modern?3:4,f.modern?.82f:.7f,
                f.background?(f.modern?.14f:.7f):0,()->{
            try(RenderState state=new RenderState()){
                state.screenSpace();glTranslatef(f.x,f.y,0);glScalef(f.scale,f.scale,1);
                float oldZ=mc.getRenderItem().zLevel;
                try{
                    if(f.background&&modern){
                        UIUtils.drawRound(2,2,Math.max(1,f.width-4),Math.max(1,f.height-4),
                                8,new Color(19,21,31,196));
                    }
                    if(f.background&&modern){
                        UIUtils.drawRoundOutline(2,2,Math.max(1,f.width-4),Math.max(1,f.height-4),
                                8,.5f,new Color(0,0,0,0),
                                UiEffects.color(f.theme.getPrimaryGradientRGB(),.14f));
                    }
                    for(Cell cell:f.cells){
                        float ix=cell.x+(f.horizontal||cell.mini?(cell.w-16)/2:2),iy=cell.y;
                        if(modern){
                            float blockH=f.horizontal?38:cell.mini?23:26;
                            UIUtils.drawRound(cell.x+1,cell.y+1,Math.max(1,cell.w-3),blockH,
                                    6,new Color(33,34,47,225));
                            UIUtils.drawRoundOutline(cell.x+1,cell.y+1,
                                    Math.max(1,cell.w-3),blockH,6,.4f,new Color(0,0,0,0),
                                    UiEffects.color(f.theme.getPrimaryGradientRGB(),.14f));
                            UIUtils.drawRound(cell.x+5,cell.y+blockH-2,
                                    Math.max(0,cell.w-12),1,.5f,
                                    new Color(f.theme.getPrimaryGradientColor().getRed(),
                                            f.theme.getPrimaryGradientColor().getGreen(),
                                            f.theme.getPrimaryGradientColor().getBlue(),44));
                        }
                        if(cell.item!=null){
                            GlStateManager.enableDepth();GlStateManager.depthFunc(GL_LEQUAL);
                            GlStateManager.depthMask(true);GlStateManager.enableAlpha();
                            RenderHelper.enableGUIStandardItemLighting();
                            mc.getRenderItem().zLevel=100;
                            mc.getRenderItem().renderItemAndEffectIntoGUI(cell.item,
                                    Math.round(ix),Math.round(iy));
                            RenderHelper.disableStandardItemLighting();
                            GlStateManager.disableDepth();GlStateManager.disableAlpha();
                        }else UIUtils.drawRound(ix,iy,16,16,3,new Color(255,255,255,48));
                        TTFFontRenderer labelFont=cell.item==null?f.emptyFont:f.font;
                        float tx=f.horizontal||cell.mini?cell.x+(cell.w-labelFont.getWidth(cell.label))/2:cell.x+25;
                        float ty=cell.y+(f.horizontal||cell.mini?19:4);
                        labelFont.drawString(cell.label,tx,ty,
                                cell.item==null?0xffcbd1dc:f.modern?0xfff5f0f8:
                                f.theme.isCustom()?f.theme.getTextColor().getRGB():0xfff0f1f5);
                        if(f.bar&&cell.item!=null&&cell.item.getMaxDamage()>0){
                            float ratio=cell.durabilityRatio;
                            float bx=cell.x+2,bw=cell.w-4,by=cell.y+(f.horizontal?30:20);
                            UIUtils.drawRound(bx,by,bw,f.modern?3:2,1,new Color(255,255,255,26));
                            if(ratio>0)UIUtils.drawRound(bx,by,bw*ratio,f.modern?3:2,
                                    Math.min(1,bw*ratio/2),
                                    new Color(durabilityColor(f.theme,ratio),true));
                        }
                    }
                }finally{mc.getRenderItem().zLevel=oldZ;}
            }
            });
        });
    }
    // Equipment information is a ratio, independent of decorative motion and direction.
    static int durabilityColor(Theme theme,float remaining){
        remaining=Float.isFinite(remaining)?Math.max(0,Math.min(1,remaining)):0;
        int palette=Theme.interpolateRGB(theme.getPrimaryGradientRGB(),theme.getSecondaryGradientRGB(),1-remaining);
        me.youngjibbit.module.impl.client.ClientSettings settings=me.youngjibbit.module.impl.client.ClientSettings.current();
        boolean priority=settings==null||settings.semanticColorPriority.getValue();
        int low=UiEffects.semanticColor(UiEffects.SemanticRole.ERROR,palette,priority);
        int middle=UiEffects.semanticColor(UiEffects.SemanticRole.WARNING,palette,priority);
        int high=UiEffects.semanticColor(UiEffects.SemanticRole.SUCCESS,palette,priority);
        int color=remaining<=.5f?Theme.interpolateRGB(low,middle,remaining*2):Theme.interpolateRGB(middle,high,(remaining-.5f)*2);
        return (palette&0xff000000)|(color&0x00ffffff);
    }
    static final class Cell {
        final ItemStack item;final String label;final float durabilityRatio,pulse;final int slot;
        float x,y,w,h;boolean mini;
        Cell(ItemStack item,String label,float durabilityRatio){
            this(item,label,durabilityRatio,4,0);
        }
        Cell(ItemStack item,String label,float durabilityRatio,int slot,float pulse){
            this.item=item;this.label=label;this.durabilityRatio=durabilityRatio;this.slot=slot;this.pulse=pulse;
        }
    }
    static final class Frame {
        float x,y,width,height,scale;boolean horizontal,background,blur,glow,bar,modern;String design;
        Theme theme;final TTFFontRenderer font;final TTFFontRenderer emptyFont=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 12");final java.util.List<Cell> cells=new ArrayList<>();Frame(TTFFontRenderer font){this.font=font;}
    }
}
