#define NEUTRON_UI_GRADIENT
// One coordinate/time contract for theme text, outlines and halos. GLSL 1.20.
uniform bool uiEnabled;
uniform vec2 uiResolution;
uniform vec4 uiGradientBounds; // inherited screen-space domain for ordinary modes
uniform vec4 uiOrbitBounds; // actual component contour, used only by mode 5
uniform vec3 uiText, uiBorder, uiGlow; // signed axis, mode, phase
uniform float uiGradientRadius;
uniform float uiShaderParam2;
// CONTOUR_COORDINATE
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
