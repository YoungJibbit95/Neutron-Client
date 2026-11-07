// Optical model adapted from Neutron's active Flauxy/Shaders/uiGlass.glsl:
// bounded lens shoulder, RGB dispersion, adaptive veil and directional edge light.
// The sampled clean/soft scene is authored here; DOM text is never captured/warped.
export const glassFragment = `precision mediump float;
varying vec2 uv;
uniform sampler2D cleanScene;
uniform sampler2D softScene;
uniform vec2 size;
uniform vec4 rect;
uniform vec3 tint;
uniform float effect;
uniform float panel;
float contour(vec2 p){vec2 q=abs(p-rect.xy-rect.zw*.5)-rect.zw*.5+vec2(16.);
return length(max(q,0.))+min(max(q.x,q.y),0.)-16.;}
vec2 safeUv(vec2 p){return clamp(p,vec2(.5)/size,vec2(1.)-vec2(.5)/size);}
void main(){
  if(panel<.5){gl_FragColor=vec4(texture2D(cleanScene,uv).rgb,1.);return;}
  vec2 p=uv*size;float d=contour(p);if(d>1.)discard;
  float depth=max(0.,-d);
  vec2 derivative=vec2(contour(p+vec2(.5,0.))-contour(p-vec2(.5,0.)),
                       contour(p+vec2(0.,.5))-contour(p-vec2(0.,.5)));
  vec2 normal=derivative/max(length(derivative),.0001);
  float shoulder=1.-smoothstep(0.,10.,depth);
  float lens=shoulder*shoulder*(1.-exp(-depth*2.));
  vec2 warped=safeUv(uv+normal*(.35*effect*18.*lens)/size);
  vec2 dispersion=normal*(.35*effect*.28*shoulder)/size;
  vec3 refracted=vec3(texture2D(cleanScene,safeUv(warped+dispersion)).r,
    texture2D(cleanScene,warped).g,texture2D(cleanScene,safeUv(warped-dispersion)).b);
  vec3 color=mix(refracted,texture2D(softScene,warped).rgb,.91-.24*shoulder);
  float luminance=dot(color,vec3(.2126,.7152,.0722));
  color=mix(vec3(luminance),color,1.10);
  color=mix(color,vec3(.025,.03,.04),.12+.18*luminance);
  color=mix(color,tint,.18*effect*.38);
  float facing=dot(normal,normalize(vec2(-.55,.83)));
  float hairline=exp(-depth*2.1),broad=exp(-depth*.34);
  float reflection=(.16+.84*pow(abs(facing),5.))*(hairline*.95+broad*.12);
  color+=.3*effect*1.75*reflection;
  color-=.3*effect*.13*hairline*max(0.,-facing);
  float alpha=1.-smoothstep(-.6,.6,d);
  gl_FragColor=vec4(clamp(color,0.,1.)*alpha,alpha);
}`;
