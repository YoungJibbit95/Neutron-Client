#version 120
// UI_GRADIENT
// UI_SURFACE

uniform vec2 location, rectSize;
uniform vec4 color;
uniform float radius;
uniform bool blur;

float roundSDF(vec2 p, vec2 b, float r) {
    vec2 q=abs(p)-b;return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;
}


void main() {
    vec2 rectHalf = rectSize * .5;
    // Smooth the result (free antialiasing).
    float smoothedAlpha =  (1.0-smoothstep(0.0, 1.0, roundSDF(rectHalf - (gl_TexCoord[0].st * rectSize), rectHalf - radius - 1., radius))) * color.a;
    vec4 surface=uiSurface(color,roundSDF(rectHalf-gl_TexCoord[0].st*rectSize,rectHalf-radius-1.0,radius));float coverage=color.a>0.0?smoothedAlpha/color.a:0.0;gl_FragColor=vec4(surface.rgb,surface.a*coverage);

}
