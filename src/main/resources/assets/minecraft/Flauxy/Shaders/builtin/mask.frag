#version 120
uniform sampler2D u_texture,u_texture2,snapshot;uniform bool glass;uniform vec2 resolution;uniform vec3 glassStyle,tintColor;
void main(){vec2 uv=gl_TexCoord[0].st;float a=texture2D(u_texture,uv).a;vec3 c=texture2D(u_texture2,uv).rgb;
if(glass){vec2 d=vec2(5.0)/resolution;vec2 n=vec2(texture2D(u_texture,uv+vec2(d.x,0.0)).a-texture2D(u_texture,uv-vec2(d.x,0.0)).a,texture2D(u_texture,uv+vec2(0.0,d.y)).a-texture2D(u_texture,uv-vec2(0.0,d.y)).a);float edge=length(n);vec2 warped=clamp(uv-n*glassStyle.x*18.0/resolution,vec2(.5)/resolution,vec2(1.0)-vec2(.5)/resolution);c=mix(texture2D(snapshot,warped).rgb,texture2D(u_texture2,warped).rgb,.82);c=mix(c,tintColor,glassStyle.y)+edge*glassStyle.z*.3;}gl_FragColor=vec4(c,a);}
