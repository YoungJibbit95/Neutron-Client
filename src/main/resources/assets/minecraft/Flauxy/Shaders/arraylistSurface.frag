#version 120
// Analytic rounded orthogonal contour. One distance field drives every surface/effect mask.
uniform vec4 corners[192]; // circle center, signed radius (convex/concave), quadrant
uniform int cornerCount, maskPass;
uniform vec2 resolution;
uniform float guiScale, fillAlpha, outlineAlpha, glowAlpha;
uniform vec4 primary, secondary, outlineColor, shapeBounds;
uniform vec3 surfaceColor;
uniform bool customTheme, verticalGradient;
uniform float phaseGradient, outlineWidth;
uniform bool blurred, glowing, gradientFill, backgroundEnabled;
uniform bool replaceScene;
uniform int substance;uniform float globalOpacity,gradientStrength;
uniform sampler2D scene, halo, snapshot;
uniform bool glass,glassClear;
uniform float glassInk;
// UI_GLASS
uniform float refraction,tint,edgeLight,surfaceOpacity;
// UI_GRADIENT

vec2 quadrant(float q) {
    return vec2((q < 0.5 || q > 2.5) ? 1.0 : -1.0, q < 1.5 ? 1.0 : -1.0);
}
vec2 tangent(vec4 c, bool start) {
    vec2 q = quadrant(c.w);
    bool horizontal = q.x * q.y * sign(c.z) > 0.0;
    if (!start) horizontal = !horizontal;
    return c.xy + (horizontal ? vec2(q.x * abs(c.z), 0.0) : vec2(0.0, q.y * abs(c.z)));
}
void nearest(vec2 p, vec2 at, vec2 normal, inout float best, inout float side) {
    float d = dot(p-at, p-at);
    if (d < best) { best = d; side = dot(p-at, normal); }
}
float contourDistance(vec2 p) {
    float best = 1.0e20, side = 1.0;
    vec4 previous = corners[cornerCount-1];
    for (int i=0; i<192; i++) {
        if (i >= cornerCount) break;
        vec4 c = corners[i];
        vec2 a = tangent(previous, false), b = tangent(c, true);
        vec2 edge = b-a;
        if (dot(edge,edge) > 0.0000001) {
            vec2 at = a + edge * clamp(dot(p-a,edge)/dot(edge,edge),0.0,1.0);
            nearest(p, at, vec2(edge.y,-edge.x), best, side);
        }
        vec2 v = p-c.xy, q = quadrant(c.w);
        vec2 at;
        if (v.x*q.x >= 0.0 && v.y*q.y >= 0.0 && dot(v,v)>0.0000001) {
            at = c.xy + normalize(v)*abs(c.z);
        } else {
            vec2 ex = c.xy + vec2(q.x*abs(c.z),0.0);
            vec2 ey = c.xy + vec2(0.0,q.y*abs(c.z));
            at = dot(p-ex,p-ex)<dot(p-ey,p-ey) ? ex : ey;
        }
        nearest(p, at, (at-c.xy)*sign(c.z), best, side);
        previous = c;
    }
    return sqrt(best) * (side < 0.0 ? -1.0 : 1.0);
}
void main() {
    vec2 uv = gl_FragCoord.xy / resolution;
    vec2 p = vec2(gl_FragCoord.x, resolution.y-gl_FragCoord.y)/guiScale;
    float d = contourDistance(p);
    float coverage = 1.0-smoothstep(-0.5,0.5,d*guiScale);
    if (maskPass == 1) { gl_FragColor=vec4(1.0,1.0,1.0,coverage); return; }
    float t=clamp((p.y-corners[0].y)/120.0,0.0,1.0);
    if(customTheme){vec2 local=(p-shapeBounds.xy)/max(shapeBounds.zw,vec2(1.0));t=verticalGradient?local.y:local.x;t=phaseGradient<0.0?clamp(t,0.0,1.0):.5-.5*cos(6.2831853*(t+phaseGradient));}
    vec4 accent=mix(primary,secondary,uiEnabled?uiPosition(uiGlow):t);
    vec4 result = vec4(0.0);
    // Exclusion is geometric, independent of the translucent background opacity.
    if (glowing && d > 0.0) {
        float a = texture2D(halo,uv).a * glowAlpha * accent.a;
        result = vec4(accent.rgb*a,a);
    }
    vec3 inside = gradientFill ? mix(primary.rgb,secondary.rgb,(uiEnabled?uiPosition(uiBorder):clamp((p.x-shapeBounds.x)/max(1.0,shapeBounds.z),0.0,1.0))) : surfaceColor;
    float alpha = coverage*fillAlpha;
    if(substance==0)alpha=coverage*fillAlpha*globalOpacity;
    if(substance==1&&backgroundEnabled){vec4 palette=mix(primary,secondary,uiEnabled?uiPosition(uiBorder):t);inside=mix(surfaceColor,palette.rgb,gradientStrength);alpha=coverage*palette.a*globalOpacity;}
    if (blurred) { inside=mix(texture2D(scene,uv).rgb,inside,fillAlpha); alpha=coverage; }
    if(glass){
        vec4 tintColor=mix(primary,secondary,uiEnabled?uiPosition(uiBorder):t);
        inside=liquidGlass(snapshot,scene,resolution,uv,d,guiScale,tintColor,glassInk,vec3(refraction,tint,edgeLight),blurred,glassClear);
        alpha=coverage;
    }
    if(substance>=2)alpha*=globalOpacity;
    result = vec4(inside*alpha,alpha) + result*(1.0-alpha);
    float line = coverage*(1.0-smoothstep(0.7*outlineWidth,1.4*outlineWidth,-d))*outlineAlpha;
    vec4 border=uiEnabled?uiBorderPalette(primary,secondary):(customTheme?outlineColor:accent);
    line*=border.a;
    result = vec4(border.rgb*line,line) + result*(1.0-line);
    if(replaceScene){float gap=max(0.0,coverage-result.a);result.rgb+=texture2D(snapshot,uv).rgb*gap;result.a+=gap;}
    gl_FragColor = vec4(result.rgb/max(result.a,0.00001),result.a*surfaceOpacity);
}
