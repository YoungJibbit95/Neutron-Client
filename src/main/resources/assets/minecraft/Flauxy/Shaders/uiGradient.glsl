// One coordinate/time contract for theme text, outlines and halos. GLSL 1.20.
uniform bool uiEnabled;
uniform vec2 uiResolution;
uniform vec4 uiGradientBounds; // inherited screen-space domain for ordinary modes
uniform vec4 uiOrbitBounds; // actual component contour, used only by mode 5
uniform vec3 uiText, uiBorder, uiGlow; // signed axis, mode, phase
uniform float uiGradientRadius;
uniform float uiShaderParam2;
// Original Neutron arc-length implementation, matching PerimeterOrbit.contour.
float uiContour(vec2 point, vec2 size, float radius) {
 vec2 wh=max(size,vec2(1.0));
 vec2 p=clamp(point,vec2(0.0),wh);
 float r=clamp(radius,0.0,min(wh.x,wh.y)*.5);
 float top=wh.x-2.0*r, right=wh.y-2.0*r, arc=1.57079632679*r;
 float perimeter=2.0*top+2.0*right+4.0*arc;
 float distance;
 if(r>0.0&&p.x>=wh.x-r&&p.y<=r){
  distance=top+r*(atan(p.y-r,p.x-(wh.x-r))+1.57079632679);
 }else if(r>0.0&&p.x>=wh.x-r&&p.y>=wh.y-r){
  distance=top+arc+right+r*atan(p.y-(wh.y-r),p.x-(wh.x-r));
 }else if(r>0.0&&p.x<=r&&p.y>=wh.y-r){
  distance=2.0*top+right+2.0*arc+r*(atan(p.y-(wh.y-r),p.x-r)-1.57079632679);
 }else if(r>0.0&&p.x<=r&&p.y<=r){
  float angle=atan(p.y-r,p.x-r);if(angle<0.0)angle+=6.28318530718;
  distance=2.0*top+2.0*right+3.0*arc+r*(angle-3.14159265359);
 }else{
  float nearest=min(min(p.y,wh.x-p.x),min(wh.y-p.y,p.x));
  if(nearest==p.y)distance=clamp(p.x-r,0.0,top);
  else if(nearest==wh.x-p.x)distance=top+arc+clamp(p.y-r,0.0,right);
  else if(nearest==wh.y-p.y)distance=top+right+2.0*arc+clamp(wh.x-r-p.x,0.0,top);
  else distance=2.0*top+right+3.0*arc+clamp(wh.y-r-p.y,0.0,right);
 }
 return fract(distance/max(1.0,perimeter));
}
float uiPosition(vec3 channel){
 vec2 p=(vec2(gl_FragCoord.x,uiResolution.y-gl_FragCoord.y)-uiGradientBounds.xy)/max(uiGradientBounds.zw,vec2(1.0));
 float t=abs(channel.x)>1.5?p.y:p.x;
 if(channel.x<0.0)t=1.0-t;
 // Modes 0/1/2 retain Solid/Static/Flow; 3/4 extend the existing vec3 transport.
 if(channel.y<.5)return 0.0;
 if(channel.y<1.5)return clamp(t,0.0,1.0);
 if(channel.y<2.5)return .5-.5*cos(6.28318530718*(t-channel.z));
 if(channel.y<3.5){float travel=.5-.5*cos(6.28318530718*channel.z);return .5-.5*cos(6.28318530718*(t-travel));}
 if(channel.y<4.5)return .5+(clamp(t,0.0,1.0)-.5)*cos(6.28318530718*channel.z);
 if(channel.y<5.5){
  vec2 point=vec2(gl_FragCoord.x,uiResolution.y-gl_FragCoord.y)-uiOrbitBounds.xy;
  float contour=uiContour(point,uiOrbitBounds.zw,uiGradientRadius);
  float travel=channel.x<0.0?-channel.z:channel.z;
  float sample=.5+.5*cos(6.28318530718*(contour-travel));
  return pow(clamp(sample,0.0,1.0),1.0+6.0*(1.0-clamp(uiShaderParam2,0.0,1.0)));
 }
 return clamp(t,0.0,1.0);
}

// Explicit border source; palette stops remain intact when the Accent option is used.
uniform bool uiAccentBorder;
uniform vec4 uiAccentColor;
vec4 uiBorderPalette(vec4 first,vec4 second){return uiAccentBorder?uiAccentColor:mix(first,second,uiPosition(uiBorder));}
