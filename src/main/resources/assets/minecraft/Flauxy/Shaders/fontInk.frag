#version 120
// UI_GRADIENT
// CONTOUR_EFFECTS
uniform sampler2D glyph;
uniform vec4 first, second;
uniform bool themed, haloTheme;
uniform float haloStrength, haloRadius;

float coverage(vec2 uv) {
    vec4 box=gl_TexCoord[1];
    if(uv.x<box.x || uv.y<box.y || uv.x>box.z || uv.y>box.w)return 0.0;
    return texture2D(glyph,uv).a;
}
float haloCoverage(vec2 uv,vec2 d){
    float h=coverage(uv)*.16;
    h+=(coverage(uv+vec2(d.x,0.0))+coverage(uv-vec2(d.x,0.0))
       +coverage(uv+vec2(0.0,d.y))+coverage(uv-vec2(0.0,d.y)))*.08;
    h+=(coverage(uv+d*.7071)+coverage(uv-d*.7071)
       +coverage(uv+vec2(d.x,-d.y)*.7071)+coverage(uv+vec2(-d.x,d.y)*.7071))*.08;
    h+=(coverage(uv+d*.35)+coverage(uv-d*.35)
       +coverage(uv+vec2(d.x,-d.y)*.35)+coverage(uv+vec2(-d.x,d.y)*.35))*.05;
    return h;
}
void main(){
    vec2 uv=gl_TexCoord[0].st;
    float ink=texture2D(glyph,uv).a;
    vec4 palette=themed?mix(first,second,uiPosition(uiText)):vec4(1.0);
    float chroma=max(gl_Color.r,max(gl_Color.g,gl_Color.b))-min(gl_Color.r,min(gl_Color.g,gl_Color.b));
    vec3 sharp=chroma>.08?gl_Color.rgb:gl_Color.rgb*palette.rgb;
    float alpha=ink*gl_Color.a*palette.a;
    float halo=0.0;
    if(haloStrength>0.0){
        vec2 d=gl_TexCoord[2].st*haloRadius;
        // Bounded taps inside this glyph's padded allocation; adjacent atlas cells cannot bleed.
        halo=haloCoverage(uv,d);
        halo*=haloStrength*gl_Color.a;
    }
    vec4 haloPalette=haloTheme?mix(first,second,uiPosition(uiGlow)):vec4(sharp,palette.a);
    vec3 haloInk=haloPalette.rgb;
    halo*=haloPalette.a;
    if(fxMode>0&&haloStrength>0.0){
        vec2 screen=vec2(gl_FragCoord.x,uiResolution.y-gl_FragCoord.y);
        vec2 delta=fxWarp(screen)-screen;
        vec2 sampleUv=uv+dFdx(uv)*delta.x-dFdy(uv)*delta.y;
        float mask=coverage(sampleUv);
        // A compact coverage-gradient distance is sufficient for the bounded
        // glyph halo; the actual atlas cell remains the final support mask.
        float derivative=max(length(vec2(dFdx(mask),dFdy(mask))),.125);
        float spread=haloCoverage(sampleUv,gl_TexCoord[2].st*haloRadius);
        float sampleRadiusPx=length(gl_TexCoord[2].st*haloRadius)
                /max(length(dFdx(uv))+length(dFdy(uv)),.000001)*1.41421356;
        float haloDistance=(1.0-clamp(spread*4.0,0.0,1.0))*sampleRadiusPx;
        float distancePx=min(max(0.0,(.5-mask)/derivative),haloDistance);
        vec4 effect=haloTheme?fxShade(distancePx,screen)
                :fxShadePalette(distancePx,screen,sharp,sharp);
        float support=clamp(spread*5.0,0.0,1.0);
        halo=effect.a*support*haloStrength*gl_Color.a*haloPalette.a*(1.0-ink);
        haloInk=effect.rgb;
    }
    float outAlpha=alpha+halo*(1.0-alpha);
    gl_FragColor=vec4((sharp*alpha+haloInk*halo*(1.0-alpha))/max(outAlpha,.00001),outAlpha);
}
