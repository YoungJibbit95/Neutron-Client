#version 120

uniform sampler2D textureIn;
uniform vec2 texelSize;
uniform float radius;
uniform float phase;
uniform float speed;
uniform float param2; // neon core sharpness (0-1)

void main() {
    vec4 center = texture2D(textureIn, gl_TexCoord[0].st);
    center.rgb *= center.a;
    if (center.a <= 0.0) discard;

    // Neon: sharp core with soft outer falloff
    float core = 0.5 - 0.5 * cos(2.0 * 3.14159 * (t + phase));
    float glow = 1.0 - abs(gl_TexCoord[0].st.y - 0.5) * 2.0;
    float intensity = core * glow * (param2 + 0.4 * sin(2.0 * 3.14159 * phase));
    
    // Edge detection for neon outline
    vec4 sum = vec4(0.0);
    int count = 0;
    for (float r = 1.0; r <= radius; r++) {
        vec4 n1 = texture2D(textureIn, gl_TexCoord[0].st + vec2(r * texelSize.x, 0.0));
        vec4 n2 = texture2D(textureIn, gl_TexCoord[0].st - vec2(r * texelSize.x, 0.0));
        vec4 n3 = texture2D(textureIn, gl_TexCoord[0].st + vec2(0.0, r * texelSize.y));
        vec4 n4 = texture2D(textureIn, gl_TexCoord[0].st - vec2(0.0, r * texelSize.y));
        n1.rgb *= n1.a; n2.rgb *= n2.a; n3.rgb *= n3.a; n4.rgb *= n4.a;
        sum += n1 + n2 + n3 + n4;
        count += 4;
    }
    if (count > 0) sum /= float(count);

    vec3 themePrimary = vec3(0.0, 0.8, 1.0);
    vec3 themeSecondary = vec3(1.0, 0.0, 1.0);
    
    // Neon core color with glow falloff
    vec3 neonColor = mix(themeSecondary, themePrimary, fract(time * speed));
    vec3 result = mix(center.rgb, neonColor, intensity * center.a);
    
    gl_FragColor = vec4(result, center.a);
}
