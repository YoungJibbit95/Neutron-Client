#version 120
// UI_GRADIENT
uniform sampler2D glyph;
uniform vec4 first, second;
uniform bool themed, haloTheme;
uniform float haloStrength, haloRadius;

float coverage(vec2 uv) {
    vec4 box=gl_TexCoord[1];
    if(uv.x<box.x || uv.y<box.y || uv.x>box.z || uv.y>box.w)return 0.0;
    return texture2D(glyph,uv).a;
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
        halo=coverage(uv)*.16;
        halo+=(coverage(uv+vec2(d.x,0.0))+coverage(uv-vec2(d.x,0.0))
             +coverage(uv+vec2(0.0,d.y))+coverage(uv-vec2(0.0,d.y)))*.08;
        halo+=(coverage(uv+d*.7071)+coverage(uv-d*.7071)
             +coverage(uv+vec2(d.x,-d.y)*.7071)+coverage(uv+vec2(-d.x,d.y)*.7071))*.08;
        halo+=(coverage(uv+d*.35)+coverage(uv-d*.35)
             +coverage(uv+vec2(d.x,-d.y)*.35)+coverage(uv+vec2(-d.x,d.y)*.35))*.05;
        halo*=haloStrength*gl_Color.a;
    }
    vec4 haloPalette=haloTheme?mix(first,second,uiPosition(uiGlow)):vec4(sharp,palette.a);
    vec3 haloInk=haloPalette.rgb;
    halo*=haloPalette.a;
    float outAlpha=alpha+halo*(1.0-alpha);
    gl_FragColor=vec4((sharp*alpha+haloInk*halo*(1.0-alpha))/max(outAlpha,.00001),outAlpha);
}
