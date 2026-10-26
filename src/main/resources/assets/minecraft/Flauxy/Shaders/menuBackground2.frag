#version 120
uniform vec2 u_resolution;
uniform float u_time;
uniform vec4 u_mouse;
uniform sampler2D u_textures[16];
const float PI = 3.141592654;
vec2 rot(vec2 v, float a) {
    return mat2(cos(a), -sin(a), sin(a), cos(a)) * v;
}
float psrdnoise(vec2 pos, vec2 uvOffset, float time) {
    return sin(pos.x + time); // Simple sine noise for now
}
float bounceIn(float t) {
    return t * t * (3.0 - 2.0 * t); // A simple bounce function
}
void main() {
    vec2 st = gl_FragCoord.xy / u_resolution.xy;
    st = rot(st, -PI / 8.0);
    vec2 mouse = u_mouse.xy / u_resolution.xy;
    float n = psrdnoise(3.0 * st, vec2(0.0), 1.2 * u_time + mouse.y * PI);
    float lines = cos((st.x + n * 0.1 + mouse.x + 0.2) * PI);
    gl_FragColor = vec4(
        mix(
            vec3(0.949, 0.561, 0.576), // rgb(242, 143, 147)
            vec3(0.494, 0.047, 0.839), // rgb(126, 12, 214)
            bounceIn(lines * 0.5 + 0.5)
        ),
        1.0
    );
}
