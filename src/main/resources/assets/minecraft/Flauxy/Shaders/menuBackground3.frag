#version 120
uniform vec2 iResolution;
uniform sampler2D iChannel0;
void main() {
    vec2 uv = gl_FragCoord.xy / iResolution.xy;
    vec4 col = 3.0 * texture2D(iChannel0, uv);
    col = vec4(1.0) - exp(-col);
    gl_FragColor = col * col;
}
