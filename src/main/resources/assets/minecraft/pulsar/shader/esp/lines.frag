#version 120

uniform sampler2D textureIn;
uniform vec2 texelSize;
uniform float radius;
uniform float phase;
uniform float speed;
uniform float param2; // line trail length (0-1)

void main() {
    vec4 center = texture2D(textureIn, gl_TexCoord[0].st);
    center.rgb *= center.a;
    if (center.a <= 0.0) discard;

    // Lines: discrete gradient segments moving along contour
    float segments = speed * 12.0 + 2.0;
    float pos = (gl_TexCoord[0].st.x * segments + phase) % 1.0;
    float band = 1.0 / segments;
    float inBand = (pos >= fract(phase) && pos < (fract(phase) + band * param2)) ? 1.0 : 0.2f;
    
    // Edge detection
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

    vec3 themePrimary = vec3(0.0, 1.0, 0.5);
    vec3 themeSecondary = vec3(0.0, 0.5, 1.0);
    
    // Lines effect: moving gradient segments along edges
    vec3 lineColor = mix(themeSecondary, themePrimary, fract(pos));
    vec3 result = mix(center.rgb, lineColor, inBand * center.a);
    
    gl_FragColor = vec4(result, center.a);
}
