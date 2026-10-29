package me.youngjibbit.gui;

/** Exact damped oscillator step: keeps position and velocity across target changes. */
final class MenuSpring {
    private double position, velocity;
    private float linearTarget=Float.NaN;
    private double linearStart, linearElapsed;
    float step(float target, float seconds, boolean motion) {
        return step(target,seconds,motion,false,MenuMotion.BALANCED);
    }
    float step(float target,float seconds,boolean motion,boolean reduced,MenuMotion preset) {
        if (!motion) { reset();position=target;return target; }
        double dt=Float.isFinite(seconds)?Math.max(0,Math.min(.25,seconds)):0;
        if(reduced){
            if(target!=linearTarget){linearTarget=target;linearStart=position;linearElapsed=0;}
            linearElapsed=Math.min(.12,linearElapsed+dt);
            double fraction=Math.min(1,linearElapsed/.12);
            position=fraction>=1?target:linearStart+(target-linearStart)*fraction;
            velocity=0;
            return (float)position;
        }
        linearTarget=Float.NaN;
        if(dt==0)return (float)position;
        double omega=preset.omega, damping=preset.damping;
        double decay=Math.exp(-damping*omega*dt), frequency=omega*Math.sqrt(1-damping*damping);
        double offset=position-target, b=(velocity+damping*omega*offset)/frequency;
        double cosine=Math.cos(frequency*dt), sine=Math.sin(frequency*dt);
        position=target+decay*(offset*cosine+b*sine);
        velocity=decay*((-offset*frequency*sine+b*frequency*cosine)-damping*omega*(offset*cosine+b*sine));
        return (float)position;
    }
    void reset(){position=velocity=linearStart=linearElapsed=0;linearTarget=Float.NaN;}
}
