#version 120
// Original Neutron effect implementation; no third-party shader source.
uniform sampler2D textureIn;
uniform int rectangleMask;
uniform int useTheme;
uniform vec2 rectSize;
uniform vec3 primaryColor;
uniform vec3 secondaryColor;
uniform float phase;
uniform float speed;
uniform float param2;
uniform float intensity;
void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec4 source;
    if (rectangleMask != 0) {
        vec2 edges = min(uv, vec2(1.0) - uv) * max(rectSize, vec2(1.0));
        float alpha = 1.0 - smoothstep(1.0, 2.0, min(edges.x, edges.y));
        source = vec4(1.0, 1.0, 1.0, alpha);
    } else {
        source = texture2D(textureIn, uv);
    }
    if (source.a <= 0.0) discard;
    float flicker = 0.5 + 0.5 * sin(6.2831853 * (uv.x * 3.0 + phase));
    float flutter = 0.75 + 0.25 * sin(6.2831853 * (uv.y * 7.0 - phase * 2.0) + 1.3);
    float pattern = clamp(flicker * flutter * (0.4 + 0.6 * param2), 0.0, 1.0);
    vec3 color = useTheme != 0 ? mix(secondaryColor, primaryColor, pattern) : source.rgb;
    float alpha = clamp(source.a * intensity * (0.65 + 0.35 * pattern), 0.0, 1.0);
    gl_FragColor = vec4(color, alpha);
}
