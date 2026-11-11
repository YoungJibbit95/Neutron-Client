#version 120
uniform sampler2D sourceMask;
uniform vec2 sourceResolution;
// CONTOUR_FIELD
float occupied(vec2 pixel) {
    if(pixel.x<0.0||pixel.y<0.0||pixel.x>=sourceResolution.x||pixel.y>=sourceResolution.y)return 0.0;
    return step(.10,texture2D(sourceMask,pixel/sourceResolution).a);
}
void main() {
    vec2 cell=floor(gl_FragCoord.xy)*2.0;
    vec2 center=cell+vec2(1.0),best=vec2(0.0);float score=1.0e20;
    for(int y=0;y<2;y++)for(int x=0;x<2;x++) {
        vec2 p=cell+vec2(float(x)+.5,float(y)+.5);
        if(occupied(p)>.5) {
            float inner=min(min(occupied(p+vec2(1.0,0.0)),occupied(p-vec2(1.0,0.0))),
                            min(occupied(p+vec2(0.0,1.0)),occupied(p-vec2(0.0,1.0))));
            float d=dot(p-center,p-center);
            if(inner<.5&&d<score){best=p;score=d;}
        }
    }
    gl_FragColor=score<1.0e19?fieldPack(best/sourceResolution):vec4(0.0);
}
