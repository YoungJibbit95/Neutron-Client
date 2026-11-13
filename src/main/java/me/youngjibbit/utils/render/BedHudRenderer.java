package me.youngjibbit.utils.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.world.BedObservation;
import me.youngjibbit.gui.LiquidGlass;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import java.awt.Color;
import java.util.List;
import java.util.Locale;
import static org.lwjgl.opengl.GL11.*;

/** Shared glass recipe for the break estimate and compact, projected bed labels. */
public final class BedHudRenderer implements AutoCloseable {
    public static final float PROGRESS_WIDTH=208, PROGRESS_HEIGHT=58, WIDGET_WIDTH=158;
    private final ArrayListSurface surface=new ArrayListSurface();

    public static final class Progress {
        public final String phase,detail,time;
        public final float progress;
        public final boolean estimated;
        public Progress(String phase,String detail,String time,float progress,boolean estimated){
            this.phase=clean(phase);this.detail=clean(detail);this.time=clean(time);
            this.progress=unit(progress);this.estimated=estimated;
        }
    }
    public static final class Widget {
        public final String team,exposure,inner,outer,distance;
        public final int teamColor;
        public final boolean open,showTeam,showLayers,showExposure;
        public Widget(String team,String exposure,String inner,String outer,String distance,int teamColor,
                      boolean open,boolean showTeam,boolean showLayers,boolean showExposure){
            this.team=clean(team);this.exposure=clean(exposure);this.inner=clean(inner);this.outer=clean(outer);
            this.distance=clean(distance);this.teamColor=teamColor;this.open=open;
            this.showTeam=showTeam;this.showLayers=showLayers;this.showExposure=showExposure;
        }
        public float height(){return 25+(showLayers?25:0)+(showExposure?12:0);}
        public static Widget from(BedObservation bed,double distance,boolean team,boolean layers,boolean exposure){
            return new Widget(bed.team.label,BedEspLayout.exposure(bed),BedEspLayout.layer(bed.inner),
                    BedEspLayout.layer(bed.outer),String.format(Locale.ROOT,"%.0fm",Math.max(0,distance)),
                    0xff000000|bed.team.colorRGB,!bed.openSides.isEmpty(),team,layers,exposure);
        }
    }
    public static final class PlacedWidget {
        public final Widget widget;
        public final float x,y,scale,alpha;
        public PlacedWidget(Widget widget,float x,float y,float scale,float alpha){
            this.widget=widget;this.x=x;this.y=y;this.scale=scale;this.alpha=alpha;
        }
    }

    public void paintProgress(Progress frame,float x,float y,float scale,float alpha,Theme requested){
        if(frame==null||!valid(x,y,scale,alpha))return;
        Theme theme=Theme.resolve(requested);
        final float opacity=unit(alpha);
        surface.withScene(theme,1,()->surface.styledPanel(x,y,PROGRESS_WIDTH*scale,PROGRESS_HEIGHT*scale,
                11*scale,theme,true,true,.58f,.5f,true,opacity,()->{
                    try(RenderState state=new RenderState()){
                        state.screenSpace();state.restoreClip();glTranslatef(x,y,0);glScalef(scale,scale,1);
                        chrome(PROGRESS_WIDTH,PROGRESS_HEIGHT,11,theme,opacity);
                        TTFFontRenderer title=font("Poppins-Medium 13"),body=font("Product-Sans-Regular 12"),
                                number=font("Product-Sans-Bold 22");
                        bedIcon(12,10,16,theme.getPrimaryGradientRGB(),opacity);
                        title.drawString("BEDBREAKER",34,10,ink(theme,.92f*opacity));
                        String phase=fit(title,frame.phase,66);
                        title.drawString(phase,PROGRESS_WIDTH-12-title.getWidth(phase),10,ink(theme,.54f*opacity));
                        String time=frame.estimated?fit(number,frame.time,70):"--";
                        number.drawString(time,PROGRESS_WIDTH-12-number.getWidth(time),24,ink(theme,opacity));
                        body.drawString(fit(body,frame.detail,Math.min(116,PROGRESS_WIDTH-34-number.getWidth(time))),12,29,ink(theme,.67f*opacity));
                        progressRail(12,44,184,5,frame.progress,theme,opacity);
                    }
                }));
    }

    /** One scene capture/blur for every bed in this eye; masks and buffers are reused. */
    public void paintWidgets(List<PlacedWidget> widgets,Theme requested){
        if(widgets==null||widgets.isEmpty())return;
        Theme theme=Theme.resolve(requested);
        surface.withScene(theme,1,()->{
            for(int i=0;i<Math.min(16,widgets.size());i++){
                PlacedWidget placed=widgets.get(i);
                if(placed==null||placed.widget==null||!valid(placed.x,placed.y,placed.scale,placed.alpha))continue;
                Widget widget=placed.widget;float s=placed.scale,a=unit(placed.alpha),height=widget.height();
                surface.styledPanel(placed.x,placed.y,WIDGET_WIDTH*s,height*s,9*s,theme,true,true,.5f,.32f,true,a,()->{
                    try(RenderState state=new RenderState()){
                        state.screenSpace();state.restoreClip();glTranslatef(placed.x,placed.y,0);glScalef(s,s,1);
                        chrome(WIDGET_WIDTH,height,9,theme,a);
                        TTFFontRenderer title=font("Poppins-Medium 13"),body=font("Product-Sans-Regular 12");
                        bedIcon(9,8,14,widget.showTeam?widget.teamColor:theme.getPrimaryGradientRGB(),a);
                        String distance=fit(body,widget.distance,28);
                        title.drawString(fit(title,widget.showTeam?widget.team:"BED",90),29,7,ink(theme,.96f*a));
                        body.drawString(distance,WIDGET_WIDTH-9-body.getWidth(distance),9,ink(theme,.48f*a));
                        float row=24;
                        if(widget.showLayers){
                            layerRow(body,"1",widget.inner,row,theme,a);
                            layerRow(body,"2",widget.outer,row+12,theme,a);row+=25;
                        }
                        if(widget.showExposure){
                            int status=UiEffects.semanticColor(widget.open?UiEffects.SemanticRole.SUCCESS:UiEffects.SemanticRole.INFO,
                                    theme.getPrimaryGradientRGB(),true);
                            UIUtils.drawExactDecorationRound(10,row+3,3,3,1.5f,color(status,.85f*a));
                            body.drawString(fit(body,widget.exposure,132),19,row,ink(theme,.7f*a));
                        }
                    }
                });
            }
        });
    }

    private static void chrome(float width,float height,float radius,Theme theme,float alpha){
        float material=alpha*UiEffects.options().surfaceOpacity;
        LiquidGlass.highlight(0,0,width,height,UiEffects.radius(radius,width,height),material);
        LiquidGlass.border(0,0,width,height,UiEffects.radius(radius,width,height),theme,false,material);
    }
    private static void layerRow(TTFFontRenderer font,String layer,String material,float y,Theme theme,float alpha){
        UIUtils.drawExactDecorationRound(9,y-1,11,10,3,color(theme.getPrimaryGradientRGB(),.12f*alpha));
        font.drawString(layer,12,y,ink(theme,.66f*alpha));
        font.drawString(fit(font,material,122),27,y,ink(theme,.8f*alpha));
    }
    private static void progressRail(float x,float y,float width,float height,float progress,Theme theme,float alpha){
        UIUtils.drawExactDecorationRound(x,y,width,height,height/2,new Color(255,255,255,Math.round(22*alpha)));
        float filled=width*unit(progress);
        if(filled<.25f)return;
        int primary=theme.getPrimaryGradientRGB(),secondary=theme.getSecondaryGradientRGB();
        if(UiEffects.glowEnabled()){
            float glow=UiEffects.glowAlpha(.15f)*alpha;
            UIUtils.drawLiteralGradient(x-2,y-2,filled+4,height+4,(height+4)/2,color(primary,glow*.32f),color(secondary,glow*.32f));
            UIUtils.drawLiteralGradient(x-1,y-1,filled+2,height+2,(height+2)/2,color(primary,glow),color(secondary,glow));
        }
        UIUtils.drawGradientHorizontal(x,y,filled,height,Math.min(height/2,filled/2),color(primary,alpha),color(secondary,alpha));
        if(filled>4)UIUtils.drawLiteralGradient(x+1,y+.7f,filled-2,.6f,.3f,
                new Color(255,255,255,Math.round(115*alpha)),new Color(255,255,255,Math.round(15*alpha)));
    }
    private static void bedIcon(float x,float y,float width,int tint,float alpha){
        UIUtils.drawExactDecorationRound(x,y+3,width,5,1.7f,color(tint,.88f*alpha));
        UIUtils.drawExactDecorationRound(x+1,y+1,width*.29f,3,1,color(0xfff5f3ff,.9f*alpha));
        UIUtils.drawExactDecorationRound(x,y+7,width,1.3f,.5f,color(tint,.9f*alpha));
        UIUtils.drawExactDecorationRound(x,y+7,1.5f,3,.5f,color(tint,.9f*alpha));
        UIUtils.drawExactDecorationRound(x+width-1.5f,y+7,1.5f,3,.5f,color(tint,.9f*alpha));
    }
    private static TTFFontRenderer font(String key){return Pulsar.INSTANCE.getFontManager().getFont(key);}
    private static int ink(Theme theme,float alpha){return LiquidGlass.ink(theme,Math.round(255*unit(alpha))).getRGB();}
    private static Color color(int color,float alpha){return new Color((color&0xffffff)|(Math.round(255*unit(alpha))<<24),true);}
    private static boolean valid(float x,float y,float scale,float alpha){return Float.isFinite(x+y+scale+alpha)&&scale>0&&alpha>0;}
    private static float unit(float value){return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    private static String clean(String value){return value==null?"":value.replaceAll("\u00a7.","").replaceAll("[\\p{Cntrl}]"," ");}
    private static String fit(TTFFontRenderer font,String text,float width){
        if(font.getWidth(text)<=width)return text;
        int end=text.length();
        while(end>0&&font.getWidth(text.substring(0,end)+"\u2026")>width)end=text.offsetByCodePoints(end,-1);
        return end==0?"":text.substring(0,end)+"\u2026";
    }
    @Override public void close(){surface.close();}
}
