#version 120
// UI_GRADIENT
uniform sampler2D logo;
uniform vec4 first, second;
uniform float opacity, haloStrength;
void main() {
    vec4 original = texture2D(logo, gl_TexCoord[0].st);
    float coverage = original.a;
    // The source already contains the glow. Retain its falloff instead of
    // manufacturing a second blurred silhouette or replacing the stroke.
    float stroke = min(coverage, smoothstep(.12, .82, coverage));
    float halo = max(0.0, coverage - stroke);
    vec4 ink = mix(first, second, uiPosition(uiText));
    vec4 light = mix(first, second, uiPosition(uiGlow));
    vec4 palette = mix(light, ink, smoothstep(.12, .72, coverage));
    float alpha = clamp(stroke + halo * haloStrength, 0.0, 1.0) * opacity * palette.a;
    gl_FragColor = vec4(original.rgb * palette.rgb, alpha);
}
