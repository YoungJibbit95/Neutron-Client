// One coordinate/time contract for theme text, outlines and halos. GLSL 1.20.
uniform bool uiEnabled;
uniform vec2 uiResolution;
uniform vec3 uiText, uiBorder, uiGlow; // signed axis, mode, phase
float uiPosition(vec3 channel){
 vec2 p=vec2(gl_FragCoord.x,uiResolution.y-gl_FragCoord.y)/max(uiResolution,vec2(1.0));
 float t=abs(channel.x)>1.5?p.y:p.x;
 if(channel.x<0.0)t=1.0-t;
 return channel.y<.5?0.0:channel.y>1.5?.5-.5*cos(6.28318530718*(t-channel.z)):clamp(t,0.0,1.0);
}

// Explicit border source; palette stops remain intact when the Accent option is used.
uniform bool uiAccentBorder;
uniform vec4 uiAccentColor;
vec4 uiBorderPalette(vec4 first,vec4 second){return uiAccentBorder?uiAccentColor:mix(first,second,uiPosition(uiBorder));}
