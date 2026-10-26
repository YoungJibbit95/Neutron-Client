#version 120

// Rotation matrix for wave transformations
#define A(v) mat2(cos(m.v + radians(vec4(0, -90, 90, 0))))  // rotate
// Wave function for line displacement
#define W(v) length(vec3(p.yz - v(p.x + vec2(0, pi_2) + t), 0)) - lt  // wave
// Point function for circular elements
#define P(v) length(p - vec3(0, v(t), v(t + pi_2))) - pt  // point

uniform vec2 iResolution;  // Screen resolution
uniform vec4 iMouse;       // Mouse input
uniform float iTime;       // Time variable for animation

void main() {
    float lt = 0.1;  // Line thickness
    float pt = 0.4;  // Point thickness
    float pi = 3.1416;
    float pi2 = pi * 2.0;
    float pi_2 = pi / 2.0;
    float t = iTime * pi;  // Animated time variable
    float s = 1.0, d = 0.0, i = d;

    vec2 R = iResolution.xy;  // Screen resolution reference
    vec2 m = (iMouse.xy - 0.5 * R) / R.y * 4.0;  // Mouse position normalized

    vec3 o = vec3(0, 0, -7);  // Camera position
    vec3 u = normalize(vec3((gl_FragCoord.xy - 0.5 * R) / R.y, 1));  // Ray direction
    vec3 c = vec3(0), k = c, p;  // Color and object space vectors

    if (iMouse.z < 1.0) m = -vec2(t / 20.0 - pi_2, 0.0);  // Auto-move when not clicking
    mat2 v = A(y), h = A(x);  // Rotation matrices for pitch & yaw

    for (; i++ < 50.0;) {  // Raymarch loop
        p = o + u * d;  // Calculate position along the ray
        p.yz *= v;  // Apply vertical rotation
        p.xz *= h;  // Apply horizontal rotation
        p.x -= 3.0;  // Shift objects to the right
        if (p.y < -1.5) p.y = 2.0 / p.y;  // Reflect into negative y
        k.x = min(max(p.x + lt, W(sin)), P(sin));  // Sine wave
        k.y = min(max(p.x + lt, W(cos)), P(cos));  // Cosine wave
        s = min(s, min(k.x, k.y));  // Blend waveforms
        if (s < 0.001 || d > 100.0) break;  // Termination condition
        d += s * 0.5;  // Step size
    }

    // Scene coloring and shading
    c = max(cos(d * pi2) - s * sqrt(d) - k, 0.0);  // Base color computation
    c.gb += 0.1;  // Add slight green/blue tint
    gl_FragColor = vec4(c * 0.4 + c.brg * 0.6 + c * c, 1.0);  // Final color mix
}
