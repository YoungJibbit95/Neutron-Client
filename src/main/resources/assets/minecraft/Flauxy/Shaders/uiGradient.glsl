// One coordinate/time contract for theme text, outlines and halos. GLSL 1.20.
uniform bool uiEnabled;
uniform vec2 uiResolution;
uniform vec3 uiText, uiBorder, uiGlow; // signed axis, mode, phase
float uiPosition(vec3 channel){
 vec2 p=vec2(gl_FragCoord.x,uiResolution.y-gl_FragCoord.y)/max(uiResolution,vec2(1.0));
 float t=abs(channel.x)>1.5?p.y:p.x;
 if(channel.x<0.0)t=1.0-t;
 // Modes 0/1/2 retain Solid/Static/Flow; 3/4 extend the existing vec3 transport.
 if(channel.y<.5)return 0.0;
 if(channel.y<1.5)return clamp(t,0.0,1.0);
 if(channel.y<2.5)return .5-.5*cos(6.28318530718*(t-channel.z));
 if(channel.y<3.5){float travel=.5-.5*cos(6.28318530718*channel.z);return .5-.5*cos(6.28318530718*(t-travel));}
 return .5+(clamp(t,0.0,1.0)-.5)*cos(6.28318530718*channel.z);
}

// Explicit border source; palette stops remain intact when the Accent option is used.
uniform bool uiAccentBorder;
uniform vec4 uiAccentColor;
vec4 uiBorderPalette(vec4 first,vec4 second){return uiAccentBorder?uiAccentColor:mix(first,second,uiPosition(uiBorder));}
