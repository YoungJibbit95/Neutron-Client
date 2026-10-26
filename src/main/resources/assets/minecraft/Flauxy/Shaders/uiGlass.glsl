// Shared optical material for analytic contours and rectangular controls. Distance is in GUI units.
// Original implementation: bounded bevel refraction, dispersion, adaptive veil and directional specular light.
vec2 glassUv(vec2 uv,vec2 size){return clamp(uv,vec2(.5)/size,vec2(1.0)-vec2(.5)/size);}
vec3 liquidGlass(sampler2D cleanScene,sampler2D softScene,vec2 size,vec2 uv,float distance,float scale,
                 vec4 palette,float ink,vec3 settings,bool blurred,bool clearGlass){
 float depth=max(0.0,-distance);
 vec2 derivative=vec2(dFdx(distance),dFdy(distance));
 vec2 normal=derivative/max(length(derivative),.0001);
 // A smooth lens shoulder vanishes at the physical boundary and in the flat central body.
 float shoulder=1.0-smoothstep(0.0,10.0,depth);
 float lens=shoulder*shoulder*(1.0-exp(-depth*2.0));
 vec2 displacement=normal*(settings.x*18.0*lens)*scale/size;
 vec2 warped=glassUv(uv+displacement,size);
 vec2 dispersion=normal*(settings.x*.28*shoulder)*scale/size;
 vec3 refracted=vec3(texture2D(cleanScene,glassUv(warped+dispersion,size)).r,
                     texture2D(cleanScene,warped).g,texture2D(cleanScene,glassUv(warped-dispersion,size)).b);
 float frost=blurred?(clearGlass?.24:.91-.24*shoulder):0.0;
 vec3 color=mix(refracted,texture2D(softScene,warped).rgb,frost);
 float luminance=dot(color,vec3(.2126,.7152,.0722));
 color=mix(vec3(luminance),color,1.10);
 // Regular adapts to its actual background and foreground ink; clear leaves detail visible.
 float lightInk=smoothstep(.35,.7,ink);
 float veil=clearGlass?.035:.12+.18*mix(1.0-luminance,luminance,lightInk);
 color=mix(color,mix(vec3(.94),vec3(.025,.03,.04),lightInk),veil);
 color=mix(color,palette.rgb,settings.y*palette.a*(clearGlass?.55:.38));
 // Neutral optical reflections are independent of the optional colored border and external glow.
 vec2 light=normalize(vec2(-.55,.83));float facing=dot(normal,light);
 float hairline=exp(-depth*2.1),broad=exp(-depth*.34);
 float reflection=(.16+.84*pow(abs(facing),5.0))*(hairline*.95+broad*.12);
 color+=settings.z*1.75*reflection;
 color-=settings.z*.13*hairline*max(0.0,-facing);
 return clamp(color,0.0,1.0);
}
