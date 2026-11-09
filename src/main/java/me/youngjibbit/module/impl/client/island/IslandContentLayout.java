package me.youngjibbit.module.impl.client.island;

/** One logical content canvas scaled inside the current morph frame; no offscreen text or symbols. */
public final class IslandContentLayout {
    public final float x,y,width,height,scale;
    public static float width(IslandStateMachine.State state){return state==IslandStateMachine.State.POSSIBLE_SETBACK?254:
            state==IslandStateMachine.State.INVENTORY_ACTIVITY?236:206;}
    public static float height(IslandStateMachine.State state){return state==IslandStateMachine.State.POSSIBLE_SETBACK?72:
            state==IslandStateMachine.State.INVENTORY_ACTIVITY?42:38;}
    private IslandContentLayout(IslandLayout.Frame frame,IslandStateMachine.State state){
        width=width(state);height=height(state);
        scale=Math.min(1,Math.min(frame.width/width,frame.height/height));
        x=frame.x+(frame.width-width*scale)/2;y=frame.y+(frame.height-height*scale)/2;
    }
    public static IslandContentLayout forFrame(IslandLayout.Frame frame,IslandStateMachine.State state){
        if(frame==null||(state!=IslandStateMachine.State.MODULE_TOAST&&state!=IslandStateMachine.State.POSSIBLE_SETBACK
                &&state!=IslandStateMachine.State.INVENTORY_ACTIVITY))
            throw new IllegalArgumentException("notice frame required");
        return new IslandContentLayout(frame,state);
    }
}
