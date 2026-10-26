package me.youngjibbit.gui;

/** Exact damped oscillator step: keeps position and velocity across target changes. */
final class MenuSpring {
    private double position, velocity;
    float step(float target, float seconds, boolean motion) {
        if (!motion) { position=target; velocity=0; return target; }
        double dt=Math.max(0,Math.min(.25,seconds)), omega=19, damping=.76;
        double decay=Math.exp(-damping*omega*dt), frequency=omega*Math.sqrt(1-damping*damping);
        double offset=position-target, b=(velocity+damping*omega*offset)/frequency;
        double cosine=Math.cos(frequency*dt), sine=Math.sin(frequency*dt);
        position=target+decay*(offset*cosine+b*sine);
        velocity=decay*((-offset*frequency*sine+b*frequency*cosine)-damping*omega*(offset*cosine+b*sine));
        return (float)position;
    }
}
