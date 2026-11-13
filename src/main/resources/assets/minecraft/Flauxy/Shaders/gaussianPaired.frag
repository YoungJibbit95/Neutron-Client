#version 120

// Adjacent taps share one linear-filtered lookup. Eligibility is checked by the caller:
// original sources, matching input/output size, linear filtering and edge clamping.
uniform sampler2D textureIn;
uniform vec2 texelSize, direction;
uniform float centerWeight, totalWeight;
uniform int pairCount;
uniform float pairWeights[64], pairOffsets[64];

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec2 stepSize = texelSize * direction;
    vec3 color = texture2D(textureIn, uv).rgb * centerWeight;
    for (int i = 0; i < pairCount; i++) {
        vec2 delta = stepSize * pairOffsets[i];
        color += texture2D(textureIn, uv + delta).rgb * pairWeights[i];
        color += texture2D(textureIn, uv - delta).rgb * pairWeights[i];
    }
    gl_FragColor = vec4(color / totalWeight, 1.0);
}
