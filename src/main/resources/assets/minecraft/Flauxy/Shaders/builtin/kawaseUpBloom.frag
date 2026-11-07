#version 120
// UI_GRADIENT
uniform vec4 uiFirst,uiSecond;uniform float uiIntensity;

uniform sampler2D inTexture, textureToCheck;
uniform vec2 halfpixel, offset, iResolution;
uniform int check;

void main() {
  //  if(check && texture2D(textureToCheck, gl_TexCoord[0].st).a > 0.0) discard;
    vec2 uv = vec2(gl_FragCoord.xy / iResolution);

    vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);
    sum.rgb *= sum.a;
    vec4 smpl1 =  texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset);
    smpl1.rgb *= smpl1.a;
    sum += smpl1 * 2.0;
    vec4 smp2 = texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);
    smp2.rgb *= smp2.a;
    sum += smp2;
    vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset);
    smp3.rgb *= smp3.a;
    sum += smp3 * 2.0;
    vec4 smp4 = texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);
    smp4.rgb *= smp4.a;
    sum += smp4;
    vec4 smp5 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);
    smp5.rgb *= smp5.a;
    sum += smp5 * 2.0;
    vec4 smp6 = texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);
    smp6.rgb *= smp6.a;
    sum += smp6;
    vec4 smp7 = texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset);
    smp7.rgb *= smp7.a;
    sum += smp7 * 2.0;
    vec4 result = sum / 12.0;
    if(check==1){if(uiEnabled){vec4 palette=mix(uiFirst,uiSecond,uiPosition(uiGlow));result.rgb=palette.rgb*result.a*palette.a;result.a*=palette.a;}result.rgb*=uiIntensity;result.a*=uiIntensity;}
    gl_FragColor = vec4(result.rgb / max(result.a, 0.00001), mix(result.a, result.a * (1.0 - step(0.00001, texture2D(textureToCheck, gl_TexCoord[0].st).a)),check));
}
