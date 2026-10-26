#version 120

// Uniforms that will be provided by Minecraft's shader pipeline
uniform float time;         // Incremented each frame for animated noise
uniform vec2 screenSize;    // Resolution of the Minecraft screen or texture

// Function to generate pseudo-random noise
float random(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

// Main entry point of the shader
void main() {
    // Normalize the screen coordinates
    vec2 uv = gl_FragCoord.xy / screenSize.xy;

    // Generate noise, modified by time for animation
    float noise = random(uv + time * 0.1);

    // Mix the noise into a grayscale color
    vec3 color = vec3(noise);

    // Set the output color
    gl_FragColor = vec4(color, 1.0);
}
