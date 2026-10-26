package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
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
    private final SliderValue positionX=new SliderValue("Position X",this,100.0,-16384,16384);
    private final SliderValue positionY=new SliderValue("Position Y",this,300.0,-16384,16384);
    private double lastPositionX=100,lastPositionY=300;
    @Override public void onValueChange(){if(positionX!=null&&positionX.getValue()!=lastPositionX){lastPositionX=positionX.getValue();x=(float)lastPositionX;}if(positionY!=null&&positionY.getValue()!=lastPositionY){lastPositionY=positionY.getValue();y=(float)lastPositionY;}}
    public void resetPosition(){positionX.reset();positionY.reset();x=100;y=300;dragging=false;me.youngjibbit.utils.render.HudDrag.cancel(this);}

    private float x=100,y=300,width,height,grabX,grabY;
    private boolean dragging,previousButton;
    @Subscribe public final Listener<Event2D> render=new Listener<>(e->{if(isToggled())renderArmorHUD();});
    @Override public void onDisable(){dragging=false;me.youngjibbit.utils.render.HudDrag.cancel(this);surface.close();super.onDisable();}
    public void renderArmorHUD(){
        if(!me.youngjibbit.module.impl.client.HUD.visible()){dragging=false;HudDrag.cancel(this);surface.close();return;}
        if(mc.thePlayer==null||mc.gameSettings.showDebugInfo){dragging=false;previousButton=Mouse.isCreated()&&Mouse.isButtonDown(0);return;}
        ScaledResolution sr=new ScaledResolution(mc);
        Frame frame=layout(mc.thePlayer.inventory.armorInventory,mc.thePlayer.getHeldItem(),sr.getScaledWidth(),sr.getScaledHeight());
        boolean down=Mouse.isCreated()&&Mouse.isButtonDown(0);
        int mx=Mouse.isCreated()?Mouse.getX()/sr.getScaleFactor():-1;
        int my=Mouse.isCreated()?(mc.displayHeight-Mouse.getY()-1)/sr.getScaleFactor():-1;
        interact(mc.currentScreen instanceof GuiChat,down,mx,my,sr.getScaledWidth(),sr.getScaledHeight());
        frame.x=x;frame.y=y;paint(frame);
    }
    Frame layout(ItemStack[] armor,ItemStack hand,int sw,int sh){
        TTFFontRenderer font=Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Bold 16");
        Frame f=new Frame(font);boolean horizontal=layout.getValue().equals("Horizontal");
        String[] names={"Head","Body","Legs","Feet","Hand"};
        for(int i=0;i<5;i++){
            if(i==4&&!held.getValue())continue;
            ItemStack item=i==4?hand:armor.length>3-i?armor[3-i]:null;
            if(item==null&&!empty.getValue())continue;
            ItemStack copy=item==null?null:item.copy();String label=copy==null?names[i]:formatDurability(copy);
            f.cells.add(new Cell(copy,label));
        }
        // Occupied vertical rows keep the equipment order. Empty slots share one compact strip.
        float cursor=6,max=0,emptyWidth=0;
        boolean hasLabels=false,hasBars=false;
        for(Cell cell:f.cells){
            TTFFontRenderer labelFont=cell.item==null?f.emptyFont:font;
            float textWidth=labelFont.getWidth(cell.label);
            cell.w=horizontal||cell.item==null?Math.max(24,textWidth+8):Math.max(42,30+textWidth);
            hasLabels|=!cell.label.isEmpty();hasBars|=bar.getValue()&&cell.item!=null&&cell.item.getMaxDamage()>0;
            if(horizontal){cell.x=cursor;cell.y=6;cursor+=cell.w;}
            else if(cell.item!=null){cell.x=6;cell.y=cursor;cursor+=bar.getValue()&&cell.item.getMaxDamage()>0?24:20;max=Math.max(max,cell.w);}
            else emptyWidth+=cell.w;
        }
        if(horizontal){f.width=cursor+6;f.height=12+16+(hasLabels?11:0)+(hasBars?4:0);}
        else{
            max=Math.max(max,emptyWidth);float emptyX=6;
            for(Cell cell:f.cells)if(cell.item==null){cell.x=emptyX;cell.y=cursor;cell.mini=true;emptyX+=cell.w;}else cell.w=max;
            f.width=max+12;f.height=cursor+6+(emptyWidth>0?27:0);
        }
        if(f.cells.isEmpty()){f.width=0;f.height=0;}
        f.scale=Math.min(scale.getValue().floatValue(),Math.min((sw-8)/Math.max(1,f.width),(sh-8)/Math.max(1,f.height)));
        f.scale=Math.max(.01f,f.scale);width=f.width*f.scale;height=f.height*f.scale;
        clamp(sw,sh);f.x=x;f.y=y;f.horizontal=horizontal;
        f.theme=Theme.resolve(Theme.getDefaultTheme());f.background=background.getValue();f.blur=blur.getValue();f.glow=glow.getValue();f.bar=bar.getValue();return f;
    }
    private String formatDurability(ItemStack item){
        int max=item.getMaxDamage();if(max<=0)return item.stackSize>1?Integer.toString(item.stackSize):"";
        int remaining=Math.max(0,Math.min(max,max-item.getItemDamage()));int percent=(int)(100L*remaining/max);
        switch(durability.getValue()){case "Value":return remaining+"/"+max;case "Both":return remaining+" ("+percent+"%)";case "None":return "";default:return percent+"%";}
    }
    void interact(boolean chat,boolean down,float mx,float my,int sw,int sh){
        boolean was=dragging;
        dragging=HudDrag.capture(this,chat,down,!previousButton,HudDrag.roundedContains(mx,my,x,y,width,height,UiEffects.radius(5,width,height)));
        if(dragging&&!was){grabX=mx-x;grabY=my-y;}
        if(dragging){x=me.youngjibbit.module.impl.client.HUD.snap(mx-grabX);y=me.youngjibbit.module.impl.client.HUD.snap(my-grabY);clamp(sw,sh);positionX.setValue((double)x);positionY.setValue((double)y);}previousButton=down;
    }
    private void clamp(int sw,int sh){if(!me.youngjibbit.module.impl.client.HUD.bounded())return;x=Math.max(4,Math.min(x,sw-width-4));y=Math.max(4,Math.min(y,sh-height-4));}
    void paint(Frame f){if(f.cells.isEmpty())return;GuiShaderBloom.afterBatch(()->surface.panel(f.x,f.y,f.width*f.scale,f.height*f.scale,5*f.scale,f.theme,f.background,f.blur,f.glow,4,.7f,f.background?.7f:0,()->{
        try(RenderState state=new RenderState()){
            state.screenSpace();glTranslatef(f.x,f.y,0);glScalef(f.scale,f.scale,1);
            float oldZ=mc.getRenderItem().zLevel;
            try{
                for(Cell cell:f.cells){
                    float ix=cell.x+(f.horizontal||cell.mini?(cell.w-16)/2:2),iy=cell.y;
                    if(cell.item!=null){
                        GlStateManager.enableDepth();GlStateManager.depthFunc(GL_LEQUAL);GlStateManager.depthMask(true);GlStateManager.enableAlpha();RenderHelper.enableGUIStandardItemLighting();
                        mc.getRenderItem().zLevel=100;mc.getRenderItem().renderItemAndEffectIntoGUI(cell.item,Math.round(ix),Math.round(iy));
                        RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.disableAlpha();
                    }else UIUtils.drawRound(ix,iy,16,16,3,new Color(255,255,255,48));
                    TTFFontRenderer labelFont=cell.item==null?f.emptyFont:f.font;
                    float tx=f.horizontal||cell.mini?cell.x+(cell.w-labelFont.getWidth(cell.label))/2:cell.x+25;
                    float ty=cell.y+(f.horizontal||cell.mini?19:4);
                    labelFont.drawString(cell.label,tx,ty,cell.item==null?0xffcbd1dc:f.theme.isCustom()?f.theme.getTextColor().getRGB():0xfff0f1f5);
                    if(f.bar&&cell.item!=null&&cell.item.getMaxDamage()>0){
                        float ratio=Math.max(0,Math.min(1,(cell.item.getMaxDamage()-cell.item.getItemDamage())/(float)cell.item.getMaxDamage()));
                        float bx=cell.x+2,bw=cell.w-4,by=cell.y+(f.horizontal?30:20);
                        UIUtils.drawRound(bx,by,bw,2,1,new Color(255,255,255,26));
                        if(ratio>0)UIUtils.drawRound(bx,by,bw*ratio,2,Math.min(1,bw*ratio/2),new Color(durabilityColor(f.theme,ratio),true));
                    }
                }
            }finally{mc.getRenderItem().zLevel=oldZ;}
        }
    }));}
    // Durability is semantic (remaining ratio), while its phase follows shared UI motion.
    static int durabilityColor(Theme theme,float remaining){
        UiEffects.Gradient gradient=UiEffects.gradient(theme,UiEffects.Channel.BORDER);
        float t=UiEffects.options().gradients?gradient.position(1-remaining,1-remaining,1,1):0;
        return Theme.interpolateRGB(theme.getPrimaryGradientRGB(),theme.getSecondaryGradientRGB(),t);
    }
    static final class Cell {final ItemStack item;final String label;float x,y,w;boolean mini;Cell(ItemStack item,String label){this.item=item;this.label=label;}}
    static final class Frame {float x,y,width,height,scale;boolean horizontal,background,blur,glow,bar;Theme theme;final TTFFontRenderer font;final TTFFontRenderer emptyFont=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 12");final java.util.List<Cell> cells=new ArrayList<>();Frame(TTFFontRenderer font){this.font=font;}}
}
