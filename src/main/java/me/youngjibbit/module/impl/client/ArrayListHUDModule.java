package me.youngjibbit.module.impl.client;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.Wrapper;
import me.youngjibbit.utils.animations.Direction;
import me.youngjibbit.utils.animations.impl.DecelerateAnimation;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.ArrayListSurface;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.GuiClip;
import me.youngjibbit.utils.render.GuiShaderBloom;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ModuleInfo(
        id = "arraylist_hud",
        name = "ArrayList",
        description = "Enabled modules with shared Rectangle Substance; Local (advanced) retains older layouts and effect overrides.",
        category = Category.Client
)
public class ArrayListHUDModule extends Module {

    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"Glass", "Compact", "BasicV2", "Basic", "Rounded", "None"}, 0);
    private final ComboValue font = new ComboValue("Font", this, new String[]{"DmSans", "Product-Sans", "Poppins", "Minecraft", "Inter", "SFPro", "KhmerOureang"}, 0);
    private final ComboValue fontMode = new ComboValue("Font Strength", this, new String[]{"Regular", "Medium", "Bold"}, 1);
    private final CheckBoxValue background = new CheckBoxValue("Background", this, true);
    private final CheckBoxValue blur = new CheckBoxValue("Blur", this, true);
    private final CheckBoxValue glow = new CheckBoxValue("Glow", this, true);
    private final CheckBoxValue showCombat = new CheckBoxValue("Show Combat", this, true);
    private final CheckBoxValue showMovement = new CheckBoxValue("Show Movement", this, true);
    private final CheckBoxValue showPlayer = new CheckBoxValue("Show Player", this, true);
    private final CheckBoxValue showRender = new CheckBoxValue("Show Render", this, false);
    private final CheckBoxValue showWorld = new CheckBoxValue("Show World", this, false);
    private final CheckBoxValue showClient = new CheckBoxValue("Show Client", this, true);
    private final CheckBoxValue showGhost = new CheckBoxValue("Show Ghost", this, true);
    private final CheckBoxValue showExploit = new CheckBoxValue("Show Exploit", this, true);
    private final SliderValue scale = new SliderValue("Scale", this, 1.0, 0.75, 1.35, false);

    private final CheckBoxValue customPadding=new CheckBoxValue("Custom Container Padding",this,false);
    private final SliderValue paddingTop=new SliderValue("Container Padding Top",this,4.0,0,24);
    private final SliderValue paddingBottom=new SliderValue("Container Padding Bottom",this,4.0,0,24);
    private final SliderValue paddingLeft=new SliderValue("Container Padding Left",this,7.0,0,24);
    private final SliderValue paddingRight=new SliderValue("Container Padding Right",this,7.0,0,24);
    private final SliderValue rowSpacing=new SliderValue("Row Spacing",this,8.0,0,20);
    private final CheckBoxValue rightAnchored=new CheckBoxValue("Right Anchored",this,false);
    public ArrayListHUDModule(){mode.setVisibility(m->getModeSetting("Effects Source").getValue().equals("Local")||mode.getValue().equals("None"));for(SliderValue v:new SliderValue[]{paddingTop,paddingBottom,paddingLeft,paddingRight,rowSpacing})v.setVisibility(m->customPadding.getValue());}
    private float pad(SliderValue value){if(customPadding.getValue())return value.getValue().floatValue();boolean compact="Compact".equalsIgnoreCase(mode.getValue());if(value==paddingTop||value==paddingBottom)return compact?3:4;if(value==paddingLeft||value==paddingRight)return compact?5:7;return compact?6:8;}

    private final Map<String, DecelerateAnimation> visibilityAnimations = new HashMap<>();
    private final Map<String, Float> yPositions = new HashMap<>();
    private final ArrayListSurface surface = new ArrayListSurface(this);
    private final me.youngjibbit.utils.render.TextEffects textEffects=new me.youngjibbit.utils.render.TextEffects();
    private final SliderValue positionX = new SliderValue("Position X",this,10.0,-16384,16384);
    private final SliderValue positionY = new SliderValue("Position Y",this,20.0,-16384,16384);
    private float anchorX,anchorY,grabX,grabY;
    private boolean dragging,previousButton;
    private ArrayListSurface.Shape frameShape;
    private float frameDelta;

    private Theme currentTheme = Theme.resolve(Theme.getDefaultTheme());
    private long lastGradientUpdate = System.currentTimeMillis();
    private float gradientOffset = 0.0f;

    @Subscribe
    private final Listener<Event2D> onRender2D = new Listener<>(event -> renderArrayList());

    private void renderArrayList() {
        if (!HUD.visible() || mc.gameSettings.showDebugInfo || "None".equalsIgnoreCase(mode.getValue())) {
            clearTransientState();
            return;
        }
        currentTheme = Theme.resolve(Theme.getDefaultTheme());
        updateGradientOffset();
        net.minecraft.client.gui.ScaledResolution sr=new net.minecraft.client.gui.ScaledResolution(mc);
        anchorX=rightAnchored.getValue()?sr.getScaledWidth()-positionX.getValue().floatValue():positionX.getValue().floatValue();anchorY=positionY.getValue().floatValue();
        List<ArrayRow> rows = buildRows(anchorX,anchorY);
        if (rows.isEmpty()) { stopDragging(); frameShape=null; surface.close();textEffects.close(); return; }
        float left=rows.get(0).rectX,right=left,bottomEdge=anchorY,progress=0;
        for(ArrayRow row:rows){left=Math.min(left,row.rectX);right=Math.max(right,row.rectX+row.rectW);progress=Math.max(progress,row.progress);bottomEdge=Math.max(bottomEdge,row.rectY+row.rectH-pad(rowSpacing)/2*row.progress);}
        bottomEdge+=pad(paddingBottom)*progress;
        float nx=HUD.bounded()?anchorX+Math.max(4-left,Math.min(0,sr.getScaledWidth()-4-right)):anchorX;
        float ny=HUD.bounded()?Math.max(4,Math.min(anchorY,sr.getScaledHeight()-(bottomEdge-anchorY)-4)):anchorY;
        moveRows(rows,nx-anchorX,ny-anchorY);anchorX=nx;anchorY=ny;
        // Freeze layout, metrics, theme and switches before leaving the listener's frame.
        final Theme theme = currentTheme;
        final float phase = gradientOffset;
        final boolean filled = background.getValue(), blurred = filled && blur.getValue(), glowing = glow.getValue();
        float[] tops = new float[rows.size()], bottoms = new float[rows.size()], rights = new float[rows.size()],lefts=new float[rows.size()];
        float top=anchorY,bottom=bottomEdge+(ny-(positionY.getValue().floatValue()));
        rows.sort(Comparator.comparingDouble(r -> r.rectY + r.rectH / 2));
        float intensity = 0;
        for (int i=0;i<rows.size();i++) {
            ArrayRow row=rows.get(i);
            tops[i]=i==0?top:bottoms[i-1];
            bottoms[i]=i==rows.size()-1?Math.max(tops[i]+0.01f,bottom):Math.max(tops[i]+0.01f,
                    (row.rectY+row.rectH/2+rows.get(i+1).rectY+rows.get(i+1).rectH/2)/2);
            lefts[i]=row.rectX;rights[i]=row.rectX+row.rectW;
            row.clip=new ClipRect(row.rectX,tops[i],row.rectW,bottoms[i]-tops[i]);
            intensity=Math.max(intensity,row.progress);
        }
        final float glowStrength=intensity*0.75f;
        ArrayListSurface.Shape initial=new ArrayListSurface.Shape(lefts,tops,bottoms,rights,UiEffects.radius(3,right-left,bottom-top));
        float oldX=anchorX,oldY=anchorY;
        boolean down=org.lwjgl.input.Mouse.isCreated()&&org.lwjgl.input.Mouse.isButtonDown(0);
        float mx=org.lwjgl.input.Mouse.isCreated()?org.lwjgl.input.Mouse.getX()/(float)sr.getScaleFactor():-1;
        float my=org.lwjgl.input.Mouse.isCreated()?(mc.displayHeight-org.lwjgl.input.Mouse.getY()-1)/(float)sr.getScaleFactor():-1;
        interact(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat,down,mx,my,initial,sr.getScaledWidth(),sr.getScaledHeight());
        float dx=anchorX-oldX,dy=anchorY-oldY;moveRows(rows,dx,dy);
        for(ArrayRow row:rows)row.clip=new ClipRect(row.clip.x+dx,row.clip.y+dy,row.clip.width,row.clip.height);
        final ArrayListSurface.Shape shape=initial.translated(dx,dy);frameShape=shape;
        Runnable labels=() -> textEffects.themed(theme,() -> { for (ArrayRow row:rows) drawLabel(row,theme,phase); });
        GuiShaderBloom.afterBatch(() -> {
            if (!surface.inheritsEffects() && !filled && !glowing) surface.text(labels);
            else surface.render(shape,theme,filled,blurred,glowing,glowStrength,labels);
        });
    }

    private List<ArrayRow> buildRows(float startX,float startY) {
        List<FrameLabel> labels=new ArrayList<>();
        Set<String> renderable=new HashSet<>();
        TTFFontRenderer renderer="Minecraft".equalsIgnoreCase(font.getValue())?null:getFontRenderer();
        float textScale=getScale();
        for (Module module:Pulsar.INSTANCE.getMm().getAllModules()) {
            if (!canBeRenderedInArrayList(module)) continue;
            renderable.add(module.getId());
            if (module.isToggled() || visibilityAnimations.containsKey(module.getId())) {
                labels.add(new FrameLabel(module,new Metrics(getLabel(module),renderer,textScale)));
            }
        }
        visibilityAnimations.keySet().removeIf(id -> !renderable.contains(id));
        yPositions.keySet().removeIf(id -> !renderable.contains(id));
        labels.sort(Comparator.comparingDouble((FrameLabel label) -> label.metrics.width).reversed()
                .thenComparing(label -> label.metrics.label,String.CASE_INSENSITIVE_ORDER));
        List<ArrayRow> rows=new ArrayList<>();
        float rowY=startY;
        for (FrameLabel label:labels) {
            Module module=label.module;
            DecelerateAnimation visibility=visibilityAnimations.computeIfAbsent(module.getId(),
                    id -> new DecelerateAnimation(210,1,Direction.FORWARDS));
            visibility.setDirection(module.isToggled()?Direction.FORWARDS:Direction.BACKWARDS);
            if (!module.isToggled() && (!UiEffects.motion() || visibility.finished(Direction.BACKWARDS))) {
                visibilityAnimations.remove(module.getId()); yPositions.remove(module.getId()); continue;
            }
            float progress=UiEffects.motion()?clamp01(visibility.getOutput().floatValue()):1;
            if (progress<=0.001f) continue;
            float rowHeight=Math.max(.1f,label.metrics.height+pad(rowSpacing))*progress;
            float rowWidth=Math.max(.1f,label.metrics.width+pad(paddingLeft)+pad(paddingRight))*progress;
            float oldY=startY+yPositions.getOrDefault(module.getId(),rowY-startY);
            float animatedY=oldY+(rowY-oldY)*UiEffects.response(frameDelta,20);
            yPositions.put(module.getId(),animatedY-startY);
            float rectX=rightAnchored.getValue()?startX-rowWidth:startX;
            float textX=rightAnchored.getValue()?startX-pad(paddingRight)-label.metrics.width:startX+pad(paddingLeft);
            rows.add(new ArrayRow(label.metrics,progress,textX,
                    animatedY+(rowHeight-label.metrics.height)/2,rectX,animatedY,rowWidth,rowHeight));
            rowY+=rowHeight;
        }
        float maxProgress=0;for(ArrayRow row:rows)maxProgress=Math.max(maxProgress,row.progress);
        for(ArrayRow row:rows){float offset=pad(paddingTop)*maxProgress-pad(rowSpacing)/2*row.progress;row.rectY+=offset;row.textY+=offset;}
        return rows;
    }

    private boolean canBeRenderedInArrayList(Module module) {
        if (module == null) {
            return false;
        }
        if (module == this || module instanceof HUD || module instanceof WatermarkHUDModule || module instanceof me.youngjibbit.module.impl.render.ClickGUI) {
            return false;
        }

        switch (module.getCategory()) {
            case Combat:
                return showCombat.getValue();
            case Movement:
                return showMovement.getValue();
            case Player:
                return showPlayer.getValue();
            case Render:
                return showRender.getValue();
            case World:
                return showWorld.getValue();
            case Client:
                return showClient.getValue();
            case Ghost:
                return showGhost.getValue();
            case Exploit:
                return showExploit.getValue();
            default:
                return false;
        }
    }

    private void drawLabel(ArrayRow row,Theme theme,float phase) {
        Metrics metrics=row.metrics;
        try (GuiClip ignored=GuiClip.push(row.clip)) {
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(row.textX,row.textY,0);
                GL11.glScalef(metrics.scale,metrics.scale,1);
                for (int i=0;i<metrics.glyphs.length;i++) {
                    float offset=metrics.glyphs.length<=1?0:(float)i/metrics.glyphs.length;
                    int color=UiEffects.alpha(-1,row.progress);
                    if (metrics.renderer==null) Wrapper.mc.fontRendererObj.drawStringWithShadow(metrics.glyphs[i],metrics.positions[i],0,color);
                    else metrics.renderer.drawString(metrics.glyphs[i],metrics.positions[i],0,color);
                }
            } finally { GL11.glPopMatrix(); }
        }
    }

    /** The same glyph advances drive measurement, sorting, clipping and drawing. No width fudge factor. */
    private static final class Metrics {
        final String label;
        final String[] glyphs;
        final float[] positions;
        final TTFFontRenderer renderer;
        final float scale,width,height;
        Metrics(String label,TTFFontRenderer renderer,float scale) {
            this.label=label; this.renderer=renderer; this.scale=scale;
            glyphs=new String[label.length()]; positions=new float[label.length()];
            float advance=0,extent=0;
            for (int i=0;i<label.length();i++) {
                glyphs[i]=String.valueOf(label.charAt(i)); positions[i]=advance;
                float width=renderer==null?Wrapper.mc.fontRendererObj.getStringWidth(glyphs[i]):renderer.getWidth(glyphs[i]);
                extent=Math.max(extent,advance+width);
                advance+=renderer==null?width:width-1.2f;
            }
            width=(extent+(renderer==null?1:0))*scale;
            height=(renderer==null?Wrapper.mc.fontRendererObj.FONT_HEIGHT:renderer.getHeight("Ay"))*scale;
        }
    }
    private static final class FrameLabel {
        final Module module; final Metrics metrics;
        FrameLabel(Module module,Metrics metrics) { this.module=module; this.metrics=metrics; }
    }

    private String getLabel(Module module) {
        return "| " + module.getName();
    }

    private TTFFontRenderer getFontRenderer() {
        String strength = fontMode.getValue();
        switch (font.getValue()) {
            case "Poppins":
                return Pulsar.INSTANCE.getFontManager().getFont("Poppins-" + toFontWeight(strength) + " 20");
            case "Product-Sans":
                return Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-" + toFontWeight(strength) + " 20");
            case "Inter":
                return Pulsar.INSTANCE.getFontManager().getFont("Inter-" + toFontWeight(strength) + " 20");
            case "SFPro":
                return Pulsar.INSTANCE.getFontManager().getFont("SFProText-" + toFontWeight(strength) + " 20");
            case "KhmerOureang":
                return Pulsar.INSTANCE.getFontManager().getFont("KhmerOureang 20");
            case "DmSans":
            default:
                return Pulsar.INSTANCE.getFontManager().getFont("DmSans" + toDmSansWeight(strength) + " 20");
        }
    }

    private String toFontWeight(String strength) {
        if ("Bold".equalsIgnoreCase(strength)) {
            return "Bold";
        }
        if ("Regular".equalsIgnoreCase(strength)) {
            return "Regular";
        }
        return "Medium";
    }

    private String toDmSansWeight(String strength) {
        if ("Bold".equalsIgnoreCase(strength)) {
            return "Bold";
        }
        if ("Regular".equalsIgnoreCase(strength)) {
            return "Regular";
        }
        return "Medium";
    }

    private void updateGradientOffset() {
        long now = System.currentTimeMillis();
        float delta = Math.min(0.05f, (now - lastGradientUpdate) / 1000.0f);
        frameDelta = delta;
        lastGradientUpdate = now;
        gradientOffset = (gradientOffset + delta * 0.18f) % 1.0f;
    }

    private float getScale() {
        return Math.max(0.75f, Math.min(1.35f, scale.getValue().floatValue()));
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static void moveRows(List<ArrayRow> rows,float dx,float dy){for(ArrayRow row:rows){row.textX+=dx;row.rectX+=dx;row.textY+=dy;row.rectY+=dy;}}
    public void resetPosition(){positionX.reset();positionY.reset();anchorX=positionX.getValue().floatValue();anchorY=positionY.getValue().floatValue();stopDragging();}
    private void stopDragging(){dragging=false;me.youngjibbit.utils.render.HudDrag.cancel(this);}
    private float anchorXBefore(ArrayListSurface.Shape shape){return rightAnchored.getValue()?shape.right:shape.left;}
    void interact(boolean chat,boolean down,float mx,float my,ArrayListSurface.Shape shape,int sw,int sh){
        boolean was=dragging;
        dragging=me.youngjibbit.utils.render.HudDrag.capture(this,chat,down,!previousButton,shape.contains(mx,my));
        if(dragging&&!was){grabX=mx-anchorX;grabY=my-anchorY;}
        if(dragging){anchorX=HUD.snap(mx-grabX);anchorY=HUD.snap(my-grabY);if(HUD.bounded()){anchorX=Math.max(4+(anchorXBefore(shape)-shape.left),Math.min(anchorX,sw-4-(shape.right-anchorXBefore(shape))));anchorY=Math.max(4,Math.min(anchorY,sh-(shape.bottom-shape.top)-4));}positionX.setValue((double)(rightAnchored.getValue()?sw-anchorX:anchorX));positionY.setValue((double)anchorY);}
        previousButton=down;
    }
    @Subscribe private final Listener<me.youngjibbit.event.packet.TickEvent> editorContext=new Listener<>(e->{if(!(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)){stopDragging();previousButton=org.lwjgl.input.Mouse.isCreated()&&org.lwjgl.input.Mouse.isButtonDown(0);}});
    private void clearTransientState() {
        stopDragging();frameShape=null;
        visibilityAnimations.clear();
        yPositions.clear();
        surface.close();textEffects.close();
    }

    @Override public void onDisable() {
        clearTransientState();
        super.onDisable();
    }

    private static final class ArrayRow {
        final Metrics metrics;
        final float progress,rectW,rectH;
        float textX,textY,rectX,rectY;
        ClipRect clip;
        ArrayRow(Metrics metrics,float progress,float textX,float textY,float rectX,float rectY,float rectW,float rectH) {
            this.metrics=metrics; this.progress=progress; this.textX=textX; this.textY=textY;
            this.rectX=rectX; this.rectY=rectY; this.rectW=rectW; this.rectH=rectH;
        }
    }
}
