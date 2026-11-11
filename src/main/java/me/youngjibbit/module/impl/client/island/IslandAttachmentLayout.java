package me.youngjibbit.module.impl.client.island;

/** One continuous upper capsule and lower sheet. The top anchor never moves during extension. */
public final class IslandAttachmentLayout {
    public final IslandLayout.Frame shell,base,detail;
    public final float reveal,joinY;
    public IslandAttachmentLayout(IslandLayout.Frame frame,float logicalBaseWidth,IslandStateMachine.State kind){
        this(frame,logicalBaseWidth,kind,1);
    }
    public IslandAttachmentLayout(IslandLayout.Frame frame,float logicalBaseWidth,IslandStateMachine.State kind,float unitScale){
        shell=frame;
        float detailWidth=kind==IslandStateMachine.State.TARGET?176:IslandContentLayout.width(kind);
        float detailHeight=kind==IslandStateMachine.State.TARGET?48:IslandContentLayout.height(kind);
        float scale=Math.min(Float.isFinite(unitScale)?Math.max(.0001f,Math.min(1,unitScale)):1,
                frame.width/logicalBaseWidth);
        float header=Math.min(frame.height,34*scale);
        float baseWidth=Math.min(frame.width,logicalBaseWidth*scale);
        base=frame.region(frame.x+(frame.width-baseWidth)/2,frame.y,baseWidth,header,header/2);
        float visible=Math.max(0,frame.height-header);
        reveal=Math.max(0,Math.min(1,visible/Math.max(.01f,detailHeight*scale)));
        float bodyWidth=Math.max(baseWidth*.52f,Math.min(baseWidth*.74f,detailWidth*scale));
        detail=frame.region(frame.x+(frame.width-bodyWidth)/2,frame.y+header,bodyWidth,
                Math.max(.01f,detailHeight*scale),14*scale);
        joinY=frame.y+Math.max(.01f,header-Math.min(7*scale,header*.25f));
    }
    public static boolean attached(IslandStateMachine.State state){
        return state==IslandStateMachine.State.TARGET||state==IslandStateMachine.State.NOTIFICATION
                ||state==IslandStateMachine.State.POSSIBLE_SETBACK;
    }
    public me.youngjibbit.utils.render.ArrayListSurface.Shape silhouette(){
        if(reveal<=.001f||shell.height<.1f||joinY>=shell.y+shell.height-.001f)
            return me.youngjibbit.utils.render.ArrayListSurface.Shape.roundedRectangle(
                shell.x,shell.y,shell.width,shell.height,shell.radius);
        return new me.youngjibbit.utils.render.ArrayListSurface.Shape(new float[]{base.x,detail.x},
                new float[]{shell.y,joinY},new float[]{joinY,shell.y+shell.height},
                new float[]{base.x+base.width,detail.x+detail.width},shell.radius);
    }
}
