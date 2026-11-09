package me.youngjibbit.module.impl.client.island;

/** Immutable target-only composition from the already frozen Island presentation. */
public final class IslandTargetComposition {
    private static final float TAU=(float)(Math.PI*2);
    public static final Arc HEALTH_TRACK=new Arc(1,17);
    public static final Arc ABSORPTION_TRACK=new Arc(1,12);
    public final String name,healthText;
    public final float healthFraction,absorptionFraction,healthWidth,absorptionWidth,reticleAlpha;
    public final Arc health,absorption;
    private final float[] reticle;

    private IslandTargetComposition(IslandPresentation source,float seconds,boolean motion){
        name=source.title;healthText=source.detail;
        healthFraction=bounded(source.healthFraction);absorptionFraction=bounded(source.absorptionFraction);
        healthWidth=Layout.TEXT_WIDTH*healthFraction;absorptionWidth=Layout.TEXT_WIDTH*absorptionFraction;
        health=new Arc(healthFraction,17);absorption=new Arc(absorptionFraction,12);
        float phase=motion&&Float.isFinite(seconds)?(float)(seconds*.7-Math.floor(seconds*.7)):0;
        float wave=motion?.5f-.5f*(float)Math.cos(phase*TAU):0;
        float gap=18+1.4f*wave;
        reticleAlpha=motion?.8f+.2f*wave:1;
        float x=Layout.CENTER_X,y=Layout.CENTER_Y;
        reticle=new float[]{
                x-22,y,x-gap,y, x+gap,y,x+22,y,
                x,y-22,x,y-gap, x,y+gap,x,y+22,
                x-3,y,x+3,y, x,y-3,x,y+3};
    }

    /** No Target provider, entity, setting, clock or retained prior view is consulted here. */
    public static IslandTargetComposition capture(IslandPresentation source,float seconds,boolean motion){
        if(source==null||source.state!=IslandStateMachine.State.TARGET||!source.hasHealthBar
                ||source.title.isEmpty())return null;
        return new IslandTargetComposition(source,seconds,motion);
    }
    private static float bounded(float value){return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    public int reticleSegments(){return reticle.length/4;}
    public float x1(int segment){return reticle[segment*4];}
    public float y1(int segment){return reticle[segment*4+1];}
    public float x2(int segment){return reticle[segment*4+2];}
    public float y2(int segment){return reticle[segment*4+3];}

    /** Sweep is exactly the frozen fraction. Time affects only the surrounding reticle. */
    public static final class Arc {
        public final float fraction,radius,sweep;
        public final int segments;
        private Arc(float fraction,float radius){
            this.fraction=bounded(fraction);this.radius=radius;sweep=TAU*this.fraction;
            segments=this.fraction==0?0:Math.max(1,(int)Math.ceil(48*this.fraction));
        }
        private float angle(int point){
            if(point<0||point>segments)throw new IndexOutOfBoundsException(Integer.toString(point));
            return -(float)Math.PI/2+(segments==0?0:sweep*point/segments);
        }
        public float x(int point){return Layout.CENTER_X+radius*(float)Math.cos(angle(point));}
        public float y(int point){return Layout.CENTER_Y+radius*(float)Math.sin(angle(point));}
    }

    /** Fit the whole glyph/name/health/absorption canvas inside the existing frame clip. */
    public static final class Layout {
        public static final float WIDTH=266,HEIGHT=52,CENTER_X=28,CENTER_Y=26,
                TEXT_X=58,TEXT_WIDTH=196,NAME_Y=7,NAME_HEIGHT=13,DETAIL_Y=23,DETAIL_HEIGHT=11,
                HEALTH_Y=39,HEALTH_HEIGHT=3,ABSORPTION_Y=45,ABSORPTION_HEIGHT=2;
        public final float x,y,width,height,scale,inset;
        public Layout(IslandLayout.Frame frame){
            inset=Math.min(2,Math.min(frame.width,frame.height)*.08f);
            scale=Math.min(1,Math.min((frame.width-2*inset)/WIDTH,(frame.height-2*inset)/HEIGHT));
            width=WIDTH*scale;height=HEIGHT*scale;
            x=frame.x+(frame.width-width)/2;y=frame.y+(frame.height-height)/2;
        }
        public float screenX(float logical){return x+logical*scale;}
        public float screenY(float logical){return y+logical*scale;}
    }
}

