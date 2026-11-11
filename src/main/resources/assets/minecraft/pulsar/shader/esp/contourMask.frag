#version 120
uniform sampler2D nearestField,sourceMask;
uniform vec2 sourceResolution,fieldResolution;
uniform float effectOpacity;
uniform bool useTheme;
// CONTOUR_FIELD
// CONTOUR_EFFECTS
vec2 fieldPoint(vec2 topDown) {return vec2(topDown.x,sourceResolution.y-topDown.y);}
vec4 fieldAt(vec2 point) {
    vec2 p=fieldPoint(point);
    if(p.x<0.0||p.y<0.0||p.x>=sourceResolution.x||p.y>=sourceResolution.y)return vec4(0.0);
    return texture2D(nearestField,p/(fieldResolution*2.0));
}
float maskDistance(vec2 point,vec4 nearest) {
    if(!fieldValid(nearest))return 1.0e5;
    vec2 p=fieldPoint(point),seed=fieldUnpack(nearest)*sourceResolution;
    float d=max(0.0,length(p-seed)-.5);
    return texture2D(sourceMask,p/sourceResolution).a>=.10?-d:d;
}
void main() {
    vec2 pixel=gl_FragCoord.xy,point=vec2(pixel.x,sourceResolution.y-pixel.y);
    float outside=1.0-clamp(texture2D(sourceMask,pixel/sourceResolution).a,0.0,1.0);
    if(outside<=.001)discard;
    vec4 nearest=fieldAt(point);if(!fieldValid(nearest))discard;
    float original=maskDistance(point,nearest);
    vec2 warped=fxWarp(point);vec4 warpedNearest=fieldAt(warped);
    float distance=maskDistance(warped,warpedNearest);
    vec4 effect=fxShadeContour(original,distance,point);
    if(!useTheme) {
        vec3 source=texture2D(sourceMask,fieldUnpack(nearest)).rgb;
        effect=fxShadeContourPalette(original,distance,point,source,source);
    }
    effect.a*=outside*effectOpacity;
    if(effect.a<.001)discard;
    gl_FragColor=effect;
}
