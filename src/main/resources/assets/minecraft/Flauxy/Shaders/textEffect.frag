#version 120
uniform sampler2D glyphs, halo;
uniform vec2 resolution, origin, extent;
uniform vec4 first, second;
uniform int style;
uniform bool useShared;
uniform int globalHalo;
uniform vec4 globalFirst, globalSecond;
// UI_GRADIENT
// CONTOUR_EFFECTS
uniform float phase, strength;
uniform float haloDistanceScale;
void main(){
 vec2 uv=gl_FragCoord.xy/resolution;
 vec4 mask=texture2D(glyphs,uv);
 float originalCoverage=mask.a;
 vec2 p=(vec2(gl_FragCoord.x,resolution.y-gl_FragCoord.y)-origin)/max(extent,vec2(1.0));
 float t=style==2?p.y:p.x;
 t=phase<0.0?clamp(t,0.0,1.0):.5-.5*cos(6.2831853*(t+phase));
 vec4 palette=style==0?first:mix(first,second,uiEnabled&&useShared?uiPosition(uiText):t);
 vec3 tint=palette.rgb;
 vec3 intrinsic=mask.rgb/max(mask.a,.00001);
 // Keep explicit Minecraft colors and shadow luminance; tint neutral glyphs.
 float chroma=max(intrinsic.r,max(intrinsic.g,intrinsic.b))-min(intrinsic.r,min(intrinsic.g,intrinsic.b));
 vec3 sharp=chroma>.08?intrinsic:tint*intrinsic;
 mask.a*=palette.a;
 vec4 glowPalette=uiEnabled&&useShared?mix(first,second,uiPosition(uiGlow)):palette;
 if(globalHalo==1)glowPalette=palette;
 if(globalHalo==2)glowPalette=mix(globalFirst,globalSecond,uiPosition(uiGlow));
 float glow=texture2D(halo,uv).a*strength*glowPalette.a*(1.0-mask.a);
 if(fxMode>0&&strength>0.0){
  vec2 screen=vec2(gl_FragCoord.x,resolution.y-gl_FragCoord.y);
  vec2 warped=fxWarp(screen);
  vec2 warpedUv=vec2(warped.x,resolution.y-warped.y)/resolution;
  float sampleMask=texture2D(glyphs,warpedUv).a;
  float derivative=max(length(vec2(dFdx(sampleMask),dFdy(sampleMask))),.125);
  float spread=texture2D(halo,warpedUv).a;
  float haloDistance=(1.0-clamp(spread*4.0,0.0,1.0))*haloDistanceScale;
  vec4 effect=fxShade(min(max(0.0,(.5-sampleMask)/derivative),haloDistance),screen);
  float support=clamp(spread*5.0,0.0,1.0);
  glow=effect.a*support*strength*glowPalette.a*(1.0-originalCoverage);
  glowPalette.rgb=effect.rgb;
 }
 float alpha=mask.a+glow*(1.0-mask.a);
 gl_FragColor=vec4((sharp*mask.a+glowPalette.rgb*glow*(1.0-mask.a))/max(alpha,.00001),alpha);
}
