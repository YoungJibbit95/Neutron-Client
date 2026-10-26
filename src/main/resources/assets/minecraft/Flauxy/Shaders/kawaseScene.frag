#version 120
// Full-resolution Kawase scene passes; intentionally RGB, never the alpha-only bloom contract.
uniform sampler2D textureIn;
uniform vec2 stepSize;
void main(){vec2 uv=gl_TexCoord[0].st;vec4 c=texture2D(textureIn,uv+stepSize)+texture2D(textureIn,uv-stepSize)+texture2D(textureIn,uv+vec2(stepSize.x,-stepSize.y))+texture2D(textureIn,uv+vec2(-stepSize.x,stepSize.y));gl_FragColor=vec4(c.rgb*.25,1.0);}
