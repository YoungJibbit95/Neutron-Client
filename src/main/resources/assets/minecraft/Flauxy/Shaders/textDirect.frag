#version 120
// UI_GRADIENT
uniform sampler2D glyph;
uniform vec4 first,second;
void main(){
 vec4 mask=texture2D(glyph,gl_TexCoord[0].st)*gl_Color;
 float chroma=max(mask.r,max(mask.g,mask.b))-min(mask.r,min(mask.g,mask.b));
 vec4 palette=mix(first,second,uiPosition(uiText));
 gl_FragColor=vec4(chroma>.08?mask.rgb:mask.rgb*palette.rgb,mask.a*palette.a);
}
