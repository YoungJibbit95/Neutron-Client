// Neutron contour lighting, GLSL 1.20. No textures, particles or iteration-sized loops.
// All points/distances are physical viewport pixels, with Y increasing downwards.
uniform int fxMode;
uniform float fxScale, fxTime;
uniform vec4 fxShape;       // origin and dimensions
uniform vec4 fxStyle;       // extent, core width, contour count, contour spacing
uniform vec4 fxTuning;      // flame height, flame rate, neon aura, travelling light length
uniform vec4 fxFlow;        // counter-wind xy, movement energy, response amount
uniform vec3 fxPrimary, fxSecondary, fxGradient;
uniform vec2 fxOrbit; // rounded contour radius, travelling-light trail
// CONTOUR_COORDINATE

float fxHash(vec2 p) {
    return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);
}
float fxNoise(vec2 p) {
    vec2 cell=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(fxHash(cell),fxHash(cell+vec2(1.0,0.0)),f.x),
               mix(fxHash(cell+vec2(0.0,1.0)),fxHash(cell+vec2(1.0)),f.x),f.y);
}
float fxFlowNoise(vec2 p,float t) {
    // Two coherent scales advect upwards at different rates instead of frame-wise randomness.
    return .68*fxNoise(p*vec2(.075,.038)+vec2(-t*.32,t*.90))
          +.32*fxNoise(p*vec2(.164,.081)+vec2(t*.24,t*1.64));
}
vec2 fxWarp(vec2 point) {
    if(fxMode!=1)return point;
    float scale=max(.25,fxScale);
    vec2 p=(point-fxShape.xy)/scale;
    float t=fxTime*(.25+fxTuning.y*4.0);
    float noise=fxFlowNoise(p,t);
    float tongues=.5+.5*sin(p.x*.255+noise*6.0-t*.82);
    float rise=fxTuning.x*(.14+.86*pow(tongues,2.4))*(.78+.22*noise);
    rise*=1.0+.22*fxFlow.z*fxFlow.w;
    float curl=sin(p.y*.09+t*1.55+noise*5.0)*fxTuning.x*.11;
    vec2 tail=fxFlow.xy*fxFlow.w*rise*.80;
    // Reverse tracing samples the source below the plume and against its tail displacement.
    return point+vec2(curl-tail.x,rise-tail.y*.45);
}
float fxPosition(vec2 point) {
#ifdef NEUTRON_UI_GRADIENT
    // UI consumers already own the correct inherited palette and component-orbit domains.
    if(uiEnabled)return uiPosition(uiGlow);
#endif
    vec2 p=(point-fxShape.xy)/max(fxShape.zw,vec2(1.0));
    float t=abs(fxGradient.x)>1.5?p.y:p.x;if(fxGradient.x<0.0)t=1.0-t;
    if(fxGradient.y<.5)return 0.0;
    if(fxGradient.y<1.5)return clamp(t,0.0,1.0);
    if(fxGradient.y<2.5)return .5-.5*cos(6.28318530718*(t-fxGradient.z));
    if(fxGradient.y<3.5){float travel=.5-.5*cos(6.28318530718*fxGradient.z);return .5-.5*cos(6.28318530718*(t-travel));}
    if(fxGradient.y<4.5)return .5+(clamp(t,0.0,1.0)-.5)*cos(6.28318530718*fxGradient.z);
    float contour=uiContour(point-fxShape.xy,fxShape.zw,fxOrbit.x);
    float travel=fxGradient.x<0.0?-fxGradient.z:fxGradient.z;
    float sample=.5+.5*cos(6.28318530718*(contour-travel));
    return pow(clamp(sample,0.0,1.0),1.0+6.0*(1.0-clamp(fxOrbit.y,0.0,1.0)));
}
vec3 fxPalette(vec2 point,vec3 first,vec3 second) {
    return mix(first,second,fxPosition(point));
}
vec3 fxSecondaryStop(vec3 first,vec3 second) {
#ifdef NEUTRON_UI_GRADIENT
    if(uiEnabled)return uiGlow.y<.5?first:second;
#endif
    return fxGradient.y<.5?first:second;
}
vec4 fxShadePalette(float distancePx,vec2 point,vec3 first,vec3 second) {
    second=fxSecondaryStop(first,second);
    float scale=max(.25,fxScale),extent=max(scale*.5,fxStyle.x);
    float d=max(0.0,distancePx);
    vec3 palette=fxPalette(point,first,second);
    vec2 local=(point-fxShape.xy)/scale;
    float along=atan((point.y-fxShape.y-fxShape.w*.5)/max(1.0,fxShape.w),
                     (point.x-fxShape.x-fxShape.z*.5)/max(1.0,fxShape.z));
    if(fxMode==2) {
        float core=exp(-pow(d/max(scale*.45,fxStyle.y*.77),2.0));
        float shoulder=exp(-d/max(scale,extent*.22))*.38;
        float breathe=.92+.08*sin(along*2.0-fxTime*1.3);
        float aura=exp(-d/max(scale,extent*.62))*.40*fxTuning.z*breathe;
        float white=.98*(1.0-smoothstep(fxStyle.y*.30,fxStyle.y*1.10,d));
        vec3 color=mix(palette,vec3(1.0),white);
        return vec4(color,clamp(core+shoulder+aura,0.0,1.0)*(1.0-smoothstep(extent*2.15,extent*2.7,d)));
    }
    if(fxMode==3) {
        float spacing=max(2.0*scale,fxStyle.w),count=clamp(floor(fxStyle.z+.5),1.0,16.0);
        float index=clamp(floor((d-.75*scale)/spacing+.5),0.0,count-1.0);
        float ring=abs(d-(.75*scale+index*spacing));
        float core=1.0-smoothstep(.25*scale,.85*scale,ring);
        float glow=exp(-ring/(scale*1.1))*.19;
        float travel=.5+.5*cos(along-fxTime*.9-index*.38);
        float light=.72+.22*pow(travel,mix(5.0,1.2,fxTuning.w));
        float envelope=1.0-smoothstep((count-1.0)*spacing+2.2*scale,(count-1.0)*spacing+4.5*scale,d);
        vec3 ink=mix(palette,mix(second,first,.5+.5*sin(index*.6+along-fxTime*.18)),.22);
        return vec4(ink,clamp((core*light+glow)*envelope,0.0,1.0));
    }
    if(fxMode==1) {
        float t=fxTime*(.25+fxTuning.y*4.0);
        float noise=fxFlowNoise(local,t);
        float filament=exp(-abs(distancePx)/max(.55*scale,extent*.10));
        float mantle=exp(-max(distancePx,0.0)/max(scale,extent*.35))
                    *exp(-max(-distancePx,0.0)/max(scale,extent*.65));
        float detail=.65+.35*sin(local.x*.44+local.y*.15+noise*7.0-t*2.2);
        float energy=filament*.87+mantle*(.16+.14*noise);
        vec3 fire=mix(palette,mix(first,second,noise),.32);
        vec3 color=mix(fire,vec3(1.0),clamp(filament*.80+detail*.07,0.0,.91));
        return vec4(color,clamp(energy*(.84+.16*detail),0.0,1.0));
    }
    return vec4(0.0);
}
vec4 fxShade(float distancePx,vec2 point) {return fxShadePalette(distancePx,point,fxPrimary,fxSecondary);}
// Colored fuel fills the rising volume; the narrow tip and attached root emit light.
vec4 fxShadeContourPalette(float originalDistance,float warpedDistance,vec2 point,vec3 first,vec3 second) {
    second=fxSecondaryStop(first,second);
    vec4 fire=fxShadePalette(warpedDistance,point,first,second);
    if(fxMode!=1)return fire;
    float sourceDistance=max(0.0,originalDistance);
    float fill=clamp(-warpedDistance/max(fxScale,fxStyle.x*.35),0.0,1.0)
              *exp(-sourceDistance/max(fxScale,fxTuning.x*.85))*(.46+.10*fxFlow.z*fxFlow.w);
    vec3 fuel=mix(fxPalette(point,first,second),mix(first,second,
            clamp(sourceDistance/max(fxScale,fxTuning.x),0.0,1.0)),.20);
    float a=fire.a+fill*(1.0-fire.a);
    fire=vec4((fire.rgb*fire.a+fuel*fill*(1.0-fire.a))/max(a,.00001),a);
    float root=exp(-sourceDistance/max(.6*fxScale,fxStyle.x*.095))*.88;
    vec3 color=mix(fxPalette(point,first,second),vec3(1.0),.62);
    a=root+fire.a*(1.0-root);
    return vec4((color*root+fire.rgb*fire.a*(1.0-root))/max(a,.00001),a);
}
vec4 fxShadeContour(float originalDistance,float warpedDistance,vec2 point) {
    return fxShadeContourPalette(originalDistance,warpedDistance,point,fxPrimary,fxSecondary);
}
