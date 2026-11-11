#version 120
uniform int uKind; // 0 halo core, 1 exact ray marker, 2 dissipating halo wake
uniform float uGlow, uPhase, uHit;

void main() {
    vec2 uv=gl_TexCoord[0].xy;
    vec3 ink=gl_Color.rgb;
    float alpha;
    if(uKind==1) {
        float r=length(uv*2.0-1.0);
        if(r>=1.0)discard;
        float core=exp(-r*r*145.0)*mix(.2,1.0,uHit);
        float shoulder=exp(-r*r*24.0);
        float rim=exp(-pow((r-.40)/.048,2.0));
        float aura=exp(-r*r*5.0)*(1.0-smoothstep(.78,1.0,r));
        ink=mix(ink,vec3(1.0),core*.58);
        alpha=core*.85+shoulder*.50+rim*.32+aura*.27*uGlow;
    } else {
        float d=abs(uv.y*2.0-1.0);
        float core=exp(-d*d*210.0);
        float shoulder=exp(-d*d*28.0);
        float aura=exp(-d*d*5.8)*(1.0-smoothstep(.76,1.0,d));
        // One luminous head and a long, gradually dissipating angular wake.
        float angle=6.28318530718*(uv.x-uPhase);
        float head=pow(.5+.5*cos(angle),9.0);
        float wake=pow(.5+.5*cos(angle-.55),2.5);
        float orbit=.48+.38*wake+.30*head;
        if(uKind==2) {core*=.24;shoulder*=.65;}
        ink=mix(ink,vec3(1.0),core*(.25+.34*head));
        alpha=(core*.78+shoulder*.42+aura*.33*uGlow)*orbit;
    }
    gl_FragColor=vec4(ink,clamp(alpha*gl_Color.a,0.0,1.0));
}
