#version 120
uniform sampler2D nearestField;
uniform vec2 fieldResolution, sourceResolution;
uniform float jumpStep;
// CONTOUR_FIELD
void main() {
    vec2 cell=gl_FragCoord.xy,point=cell*2.0;
    vec4 best=vec4(0.0);float score=1.0e20;
    for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++) {
        vec2 at=cell+vec2(float(x),float(y))*jumpStep;
        if(at.x>=0.0&&at.y>=0.0&&at.x<fieldResolution.x&&at.y<fieldResolution.y) {
            vec4 candidate=texture2D(nearestField,at/fieldResolution);
            if(fieldValid(candidate)) {
                vec2 delta=fieldUnpack(candidate)*sourceResolution-point;
                float d=dot(delta,delta);
                if(d<score){score=d;best=candidate;}
            }
        }
    }
    gl_FragColor=best;
}
