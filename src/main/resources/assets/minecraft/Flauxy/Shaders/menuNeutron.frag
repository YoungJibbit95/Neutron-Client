#version 120
uniform vec2 resolution;
uniform float time;
uniform float quality;
uniform vec3 primary,secondary;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
// FBM material adapted from the user's reference. DS: hornidev
float fbm(vec2 p){float v=0.0,a=.5;for(int i=0;i<6;i++){if(float(i)>quality+1.0)break;v+=a*noise(p);p=mat2(.877,.479,-.479,.877)*p*2.03+13.7;a*=.5;}return v;}
void main(){
 vec2 uv=gl_FragCoord.xy/resolution, p=(gl_FragCoord.xy-resolution*.5)/resolution.y;
 vec3 accent=mix(primary,secondary,.55), ice=mix(vec3(.45,.71,1),accent,.35);
 vec3 c=mix(vec3(.006,.009,.022),vec3(.018,.022,.046),uv.y);
 float cloud=fbm(p*3.2+vec2(time*.009,0));
 float wisps=fbm(p*6.0+cloud*2.5-vec2(0,time*.006));
 c+=mix(secondary,ice,cloud)*pow(wisps,3.0)*.19;
 for(int i=0;i<3;i++){
  if(float(i)>quality)break;
  float layer=float(i),scale=86.0+layer*53.0;
  vec2 grid=p*scale+vec2(time*.017*(layer+1.0),layer*79.0),cell=floor(grid),f=fract(grid)-.5;
  float seed=hash(cell+layer*47.0),star=exp(-dot(f,f)*(500.0+layer*130.0))*step(.978,seed);
  c+=mix(vec3(.35,.48,.7),vec3(.9,.91,1),seed)*star*(.32+layer*.15);
 }
 // Keep the luminous object and sweeping jets beside the quiet menu column.
 vec2 q=p-vec2(-.49,.12);float r=length(q),a=atan(q.y+.000001,q.x+.000001);
 float spin=time*.19;
 vec2 axis=vec2(cos(spin+.95),sin(spin+.95));
 float along=abs(dot(q,axis)),across=abs(q.x*axis.y-q.y*axis.x);
 float jet=exp(-across/(.009+along*.075))*exp(-along*2.0)*smoothstep(.045,.095,along);
 c+=ice*jet*.72;
 float turbulence=fbm(q*29.0+vec2(time*.035,a*.2));
 float halo=exp(-r*9.0)*.32;
 float corona=exp(-abs(r-.066)*49.0)*(.42+.35*turbulence);
 float filaments=pow(.5+.5*sin(a*13.0+time*.18+turbulence*5.0),5.0)*exp(-abs(r-.09)*28.0)*.14;
 c+=ice*(halo+corona+filaments);
 c+=vec3(.78,.89,1)*exp(-pow(r/.043,2.0))*2.5;
 // Smooth exposure, bounded color and opaque alpha; no full-frame pulses.
 c=vec3(1.0)-exp(-max(c,vec3(0.0)));
 float quiet=1.0-.24*exp(-pow(abs(p.x)/.25,4.0));
 c*=quiet*clamp(1.0-dot(p,p)*.24,.45,1.0);
 gl_FragColor=vec4(clamp(c,0.0,1.0),1.0);
}

