package me.youngjibbit.gui;

import me.youngjibbit.module.impl.client.ClientSettings;

/** Per-control presentation only. The screen owns dt, geometry, focus, capture and actions. */
public final class UiSpring {
    private final MenuSpring spring=new MenuSpring();
    private MenuMotion preset=MenuMotion.BALANCED;
    private float amount;
    private boolean focused,pressed,enabled=true;

    public float step(float seconds,boolean hover,boolean focus,boolean held,boolean enabled,ClientSettings settings){
        this.enabled=enabled;focused=enabled&&focus;pressed=enabled&&held;
        preset=MenuMotion.resolve(settings);
        // Disabled must not retain a visually active spring tail.
        if(!enabled){spring.reset();amount=0;return 0;}
        float target=pressed?-.45f:hover?1:focused?.55f:0;
        amount=spring.step(target,seconds,settings==null||settings.animations.getValue(),settings!=null&&settings.reducedMotion.getValue(),preset);
        return amount;
    }
    public float amount(){return amount;}
    public float lift(){return preset.lift(amount);}
    public float scale(){return preset.scale(amount);}
    public float glow(){return preset.glow(amount);}
    public boolean focused(){return focused;}
    public boolean pressed(){return pressed;}
    public boolean enabled(){return enabled;}
    public void reset(){spring.reset();amount=0;focused=pressed=false;enabled=true;}
}
