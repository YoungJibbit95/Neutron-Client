uniform vec2 effectResolution;
uniform vec4 effectRect;
uniform float effectRadius, effectOpacity;
float effectRectangleDistance(vec2 p) {
    vec2 halfSize=max(vec2(.5),effectRect.zw*.5);
    float radius=clamp(effectRadius,0.0,min(halfSize.x,halfSize.y));
    vec2 q=abs(p-effectRect.xy-halfSize)-halfSize+vec2(radius);
    return length(max(q,vec2(0.0)))+min(max(q.x,q.y),0.0)-radius;
}
void main() {
    vec2 point=vec2(gl_FragCoord.x,effectResolution.y-gl_FragCoord.y);
    float original=effectRectangleDistance(point);
    float outside=smoothstep(-.5,.5,original);
    if(outside<=0.0)discard;
    vec4 effect=fxShadeContour(original,effectRectangleDistance(fxWarp(point)),point);
    effect.a*=outside*effectOpacity;
    if(effect.a<.001)discard;
    gl_FragColor=effect;
}
