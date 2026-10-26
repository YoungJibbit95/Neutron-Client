#version 120
uniform float TIME;
uniform vec2 RESOLUTION;
const float PI = 3.141592654;
const float TAU = 6.283185307;
const float gravity = 1.0;
const float waterTension = 0.01;
const vec3 skyCol1 = vec3(0.3, 0.35, 0.6);
const vec3 skyCol2 = vec3(0.3, 0.3, 1.0);
const vec3 sunCol1 = vec3(1.0, 0.5, 0.4);
const vec3 sunCol2 = vec3(1.0, 0.8, 0.8);
const vec3 seaCol1 = vec3(0.02, 0.1, 0.2);
const vec3 seaCol2 = vec3(0.1, 0.6, 0.9);
float tanh_approx(float x) { return clamp(x * (27.0 + x * x) / (27.0 + 9.0 * x * x), -1.0, 1.0); }
vec2 wave(float t, float a, float w, float p) { return vec2(t, a * sin(t * w + p)); }
vec3 sunDirection() { return normalize(vec3(0.0, 0.06, 1.0)); }
vec3 skyColor(vec3 rd) {
    vec3 sunDir = sunDirection();
    float sunDot = max(dot(rd, sunDir), 0.0);
    vec3 col = mix(skyCol1, skyCol2, rd.y);
    col += 0.5 * sunCol1 * pow(sunDot, 90.0);
    col += 4.0 * sunCol2 * pow(sunDot, 900.0);
    return col;
}
vec3 render(vec3 ro, vec3 rd) {
    vec3 col = skyColor(rd);
    float dsea = (0.0 - ro.y) / rd.y;
    if (dsea > 0.0) {
        vec3 p = ro + dsea * rd;
        vec3 nor = vec3(0.0, 1.0, 0.0);
        float fre = pow(1.0 - dot(-nor, rd), 3.0);
        float dif = mix(0.25, 1.0, max(dot(nor, sunDirection()), 0.0));
        vec3 refl = skyColor(reflect(rd, nor));
        vec3 refr = seaCol1 + dif * sunCol1 * seaCol2 * 0.1;
        col = mix(refr, 0.9 * refl, fre);
        col = mix(col, skyColor(rd), 1.0 - exp(-0.01 * dsea));
    }
    return col;
}
void mainImage(out vec4 fragColor, vec2 fragCoord) {
    vec2 q = fragCoord / RESOLUTION.xy;
    vec2 p = -1.0 + 2.0 * q;
    p.x *= RESOLUTION.x / RESOLUTION.y;
    vec3 ro = vec3(0.0, 10.0, 0.0);
    vec3 rd = normalize(vec3(p.x, p.y, 2.5));
    fragColor = vec4(render(ro, rd), 1.0);
}
void main(void) { mainImage(gl_FragColor, gl_FragCoord.xy); }
