package me.youngjibbit.gui;

import me.youngjibbit.clickgui.utils.ClickAnimation;
import me.youngjibbit.utils.animations.Direction;
import me.youngjibbit.utils.render.UiEffects;

/** Shared interruptible reveal. Geometry and input consume the same progress. */
public final class GuiMotion {
    private final ClickAnimation animation=new ClickAnimation(220,1,Direction.FORWARDS);
    private boolean closing;
    public void open(){closing=false;animation.setDirection(Direction.FORWARDS);animation.reset();}
    public void close(){closing=true;animation.setDirection(Direction.BACKWARDS);}
    public float progress(){return UiEffects.motion()?Math.max(0,Math.min(1,animation.getOutput().floatValue())):closing?0:1;}
    public boolean finished(){return closing&&progress()<=.001f;}
    public boolean closing(){return closing;}
}
