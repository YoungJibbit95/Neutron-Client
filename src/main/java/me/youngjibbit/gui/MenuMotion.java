package me.youngjibbit.gui;

import me.youngjibbit.module.impl.client.ClientSettings;

/** Main-menu presentation only; Values, persistence and frame timing keep their existing owners. */
enum MenuMotion {
    CALM(22, .92, .8f, .010f, .12f),
    BALANCED(19, .76, 1.6f, .020f, .22f),
    EXPRESSIVE(17, .64, 2.6f, .032f, .34f);

    final double omega, damping;
    private final float lift, scale, glow;
    MenuMotion(double omega,double damping,float lift,float scale,float glow) {
        this.omega=omega;this.damping=damping;this.lift=lift;this.scale=scale;this.glow=glow;
    }
    static MenuMotion resolve(ClientSettings settings) {
        String value=settings==null?null:settings.motionIntensity.getValue();
        return "Calm".equals(value)?CALM:"Expressive".equals(value)?EXPRESSIVE:BALANCED;
    }
    private static float bounded(float response) { return Float.isFinite(response)?Math.max(-.6f,Math.min(1.15f,response)):0; }
    float lift(float response) { return bounded(response)*lift; }
    float scale(float response) { return 1+bounded(response)*scale; }
    float glow(float response) { return Math.max(0,bounded(response))*glow; }
}
