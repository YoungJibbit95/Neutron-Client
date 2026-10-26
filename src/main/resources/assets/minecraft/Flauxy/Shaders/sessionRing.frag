#version 120
uniform vec2 center;
uniform float radius, thickness, progress;
uniform vec3 color;
void main() {
    vec2 p=gl_FragCoord.xy-center;
    float edge=1.0-smoothstep(thickness,thickness+1.0,abs(length(p)-radius));
    float angle=mod(atan(p.x,p.y)+6.28318530718,6.28318530718)/6.28318530718;
    float active=1.0-smoothstep(progress,progress+0.003,angle);
    gl_FragColor=vec4(mix(vec3(1.0),color,active),edge*mix(0.24,1.0,active));
}
