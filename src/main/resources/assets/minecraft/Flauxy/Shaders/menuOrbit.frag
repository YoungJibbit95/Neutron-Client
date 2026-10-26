#version 120
uniform vec2 resolution;
uniform float time;
uniform vec3 primary,secondary;
void main(){
    vec2 p=(gl_FragCoord.xy-resolution*0.5)/resolution.y;
    vec3 c=mix(vec3(0.018,0.020,0.030),vec3(0.035,0.040,0.060),gl_FragCoord.y/resolution.y);
    vec2 center=vec2(-0.37+0.03*sin(time*0.17),0.08);
    float d=length(p-center);
    for(int i=0;i<3;i++){
        float r=0.24+float(i)*0.16+0.012*sin(time*0.5+float(i));
        float ring=exp(-abs(d-r)*150.0);
        float aura=exp(-abs(d-r)*24.0);
        c+=mix(primary,secondary,float(i)/2.0)*(ring*0.06+aura*0.018);
    }
    vec2 orb=center+vec2(cos(time*0.22),sin(time*0.22))*0.40;
    c+=mix(primary,secondary,0.5)*exp(-length(p-orb)*36.0)*0.14;
    float vignette=clamp(1.0-dot(p,p)*0.32,0.4,1.0);
    gl_FragColor=vec4(c*vignette,1.0);
}
