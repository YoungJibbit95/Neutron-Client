#version 120
uniform vec2 iResolution;
uniform sampler2D iChannel0;
uniform sampler2D iChannel3;
vec3 saturate(vec3 x) {
    return clamp(x, vec3(0.0), vec3(1.0));
}
vec4 cubic(float x) {
    float x2 = x * x;
    float x3 = x2 * x;
    return vec4(-x3 + 3.0*x2 - 3.0*x + 1.0,
                3.0*x3 - 6.0*x2 + 4.0,
               -3.0*x3 + 3.0*x2 + 3.0*x + 1.0,
                x3) / 6.0;
}
vec4 BicubicTexture(sampler2D tex, vec2 coord) {
    vec2 resolution = iResolution.xy;
    coord *= resolution;
    float fx = fract(coord.x);
    float fy = fract(coord.y);
    coord -= vec2(fx, fy);
    fx -= 0.5;
    fy -= 0.5;
    vec4 xcubic = cubic(fx);
    vec4 ycubic = cubic(fy);
    vec4 c = vec4(coord.x - 0.5, coord.x + 1.5, coord.y - 0.5, coord.y + 1.5);
    vec4 s = vec4(xcubic.x + xcubic.y, xcubic.z + xcubic.w, ycubic.x + ycubic.y, ycubic.z + ycubic.w);
    vec4 offset = c + vec4(xcubic.y, xcubic.w, ycubic.y, ycubic.w) / s;
    vec4 sample0 = texture2D(tex, vec2(offset.x, offset.z) / resolution);
    vec4 sample1 = texture2D(tex, vec2(offset.y, offset.z) / resolution);
    vec4 sample2 = texture2D(tex, vec2(offset.x, offset.w) / resolution);
    vec4 sample3 = texture2D(tex, vec2(offset.y, offset.w) / resolution);
    float sx = s.x / (s.x + s.y);
    float sy = s.z / (s.z + s.w);
    return mix(mix(sample3, sample2, sx), mix(sample1, sample0, sx), sy);
}
vec3 ColorFetch(vec2 coord) {
    return texture2D(iChannel0, coord).rgb;
}
vec3 BloomFetch(vec2 coord) {
    return BicubicTexture(iChannel3, coord).rgb;
}
vec3 Grab(vec2 coord, float octave, vec2 offset) {
    float scale = exp2(octave);
    coord = (coord / scale) - offset;
    return BloomFetch(coord);
}
vec2 CalcOffset(float octave) {
    vec2 offset = vec2(0.0);
    vec2 padding = vec2(10.0) / iResolution.xy;
    offset.x = -min(1.0, floor(octave / 3.0)) * (0.25 + padding.x);
    offset.y = -(1.0 - (1.0 / exp2(octave))) - padding.y * octave;
    offset.y += min(1.0, floor(octave / 3.0)) * 0.35;
    return offset;
}
vec3 GetBloom(vec2 coord) {
    vec3 bloom = vec3(0.0);
    bloom += Grab(coord, 1.0, CalcOffset(0.0)) * 1.0;
    bloom += Grab(coord, 2.0, CalcOffset(1.0)) * 1.5;
    bloom += Grab(coord, 3.0, CalcOffset(2.0)) * 1.0;
    bloom += Grab(coord, 4.0, CalcOffset(3.0)) * 1.5;
    bloom += Grab(coord, 5.0, CalcOffset(4.0)) * 1.8;
    bloom += Grab(coord, 6.0, CalcOffset(5.0)) * 1.0;
    bloom += Grab(coord, 7.0, CalcOffset(6.0)) * 1.0;
    bloom += Grab(coord, 8.0, CalcOffset(7.0)) * 1.0;
    return bloom;
}
void main() {
    vec2 uv = gl_FragCoord.xy / iResolution.xy;
    vec3 color = ColorFetch(uv);
    color += GetBloom(uv) * 0.08;
    color *= 200.0;
    color = pow(color, vec3(1.5));
    color = color / (1.0 + color);
    color = pow(color, vec3(1.0 / 1.5));
    color = mix(color, color * color * (3.0 - 2.0 * color), vec3(1.0));
    color = pow(color, vec3(1.3, 1.2, 1.0));
    color = saturate(color * 1.01);
    color = pow(color, vec3(0.7 / 2.2));
    gl_FragColor = vec4(color, 1.0);
}
