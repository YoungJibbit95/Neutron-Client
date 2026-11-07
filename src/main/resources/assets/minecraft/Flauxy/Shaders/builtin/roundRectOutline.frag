#version 120

uniform vec2 location, rectSize;
uniform vec4 color, outlineColor;
// UI_GRADIENT
// UI_SURFACE
uniform vec4 uiFirst, uiSecond;uniform float uiAccent;
uniform float radius, outlineThickness;

float roundedSDF(vec2 centerPos, vec2 size, float radius) {
    vec2 q=abs(centerPos)-size+radius;return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
}

void main() {
    float distance = roundedSDF(gl_FragCoord.xy - location - (rectSize * .5), (rectSize * .5) + (outlineThickness *.5) - 1.0, radius);

    float blendAmount = smoothstep(0., 2., abs(distance) - (outlineThickness * .5));

    vec4 surface=uiSurface(color,distance);vec4 insideColor = (distance < 0.) ? surface : vec4(outlineColor.rgb,  0.0);
    vec4 border=uiEnabled?vec4(mix(outlineColor.rgb,uiBorderPalette(uiFirst,uiSecond).rgb,uiAccent),outlineColor.a*mix(1.0,uiBorderPalette(uiFirst,uiSecond).a,uiAccent)):outlineColor;
    gl_FragColor = mix(border, insideColor, blendAmount);

}
