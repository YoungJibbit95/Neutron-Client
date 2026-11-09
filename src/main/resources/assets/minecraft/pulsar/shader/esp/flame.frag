#version 120

uniform sampler2D textureIn;
uniform vec2 texelSize;
uniform float radius;
uniform float phase;
uniform float speed;
uniform float param2; // flame height (0-1)

void main() {
    vec4 center = texture2D(textureIn, gl_TexCoord[0].st);
    center.rgb *= center.a;
    if (center.a <= 0.0) discard;

    // Animated flame flicker
    float time = phase;
    float flicker = sin(2.0 * 3.14159 * (gl_TexCoord[0].st.x + time) * speed * 3.0) * 0.5 + 0.5;
    float flutter = sin(2.0 * 3.14159 * (gl_TexCoord[0].st.y + time) * speed * 7.0 + 1.3) * 0.25 + 0.75;
    float intensity = flicker * flutter * param2;

    // Edge detection: sum neighbors within radius
    vec4 sum = vec4(0.0);
    int count = 0;
    for (float r = 1.0; r <= radius; r++) {
        for (float a = 0.0; a < 6.28318; a += 1.0472) { // 60 degree steps
            vec2 offset = vec2(cos(a), sin(a)) * r * texelSize;
            vec4 n = texture2D(textureIn, gl_TexCoord[0].st + offset);
            n.rgb *= n.a;
            sum += n;
            count++;
        }
    }
    if (count > 0) sum /= float(count);

    // Blend original with flame-tinted outline
    vec3 themePrimary = vec3(1.0, 0.5, 0.0); // will be overridden by uniform
    vec3 themeSecondary = vec3(1.0, 0.2, 0.0);
    
    // Use texture color for base, theme colors for glow
    vec3 base = center.rgb;
    vec3 flameColor = mix(themeSecondary, themePrimary, fract(time * speed));
    
    gl_FragColor = vec4(mix(base, flameColor, intensity * center.a), center.a);
}
