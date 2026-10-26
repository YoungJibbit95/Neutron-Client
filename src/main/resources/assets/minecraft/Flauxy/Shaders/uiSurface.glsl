// A child rectangle replaces its part of the root composition using the root's read-only scene.
// This avoids multiplying opacity or blurring an already blurred parent/control.
uniform bool uiMaterialEnabled,uiSceneBlurred;
uniform int uiSubstance;
uniform sampler2D uiCleanScene,uiSoftScene;
uniform vec2 uiSceneResolution;
uniform vec4 uiFillFirst,uiFillSecond,uiFillBackground;
uniform float uiOpacity,uiGradientStrength,uiSceneScale;
uniform vec3 uiGlass;
uniform bool uiGlassClear;uniform float uiGlassInk;
// UI_GLASS
vec4 uiSurface(vec4 fallback,float distance){
 if(!uiMaterialEnabled)return fallback;
 vec2 uv=gl_FragCoord.xy/uiSceneResolution;
 vec3 clean=texture2D(uiCleanScene,uv).rgb;
 vec4 palette=mix(uiFillFirst,uiFillSecond,uiPosition(uiBorder));
 vec3 color=uiFillBackground.rgb;float alpha=uiFillBackground.a*uiOpacity;
 if(uiSubstance==1){color=mix(uiFillBackground.rgb,palette.rgb,uiGradientStrength);alpha=palette.a*uiOpacity;}
 if(uiSubstance==2){color=mix(texture2D(uiSoftScene,uv).rgb,uiFillBackground.rgb,uiFillBackground.a);alpha=uiOpacity;}
 if(uiSubstance==4){color=texture2D(uiSoftScene,uv).rgb;alpha=uiOpacity;}
 if(uiSubstance==3){
  color=liquidGlass(uiCleanScene,uiSoftScene,uiSceneResolution,uv,distance/uiSceneScale,uiSceneScale,palette,uiGlassInk,uiGlass,uiSceneBlurred,uiGlassClear);alpha=uiOpacity;
 }
 return vec4(mix(clean,color,alpha),1.0);
}
