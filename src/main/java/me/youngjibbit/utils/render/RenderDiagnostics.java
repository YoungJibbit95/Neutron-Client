package me.youngjibbit.utils.render;
/** Opt-in deterministic counters for the OpenGL harness. No timing/FPS estimates. */
public final class RenderDiagnostics {
    public static boolean enabled;
    public static long framebufferCreates, framebufferDeletes, programCreates, shaderCompiles, shaderDraws, programDeletes, shaderDeletes;
    public static long fontRendererCreates, fontFileLoads, glyphTexturesCreated, glyphTexturesDeleted;
    public static long maskPasses, blurPasses, composites;
    private RenderDiagnostics() { }
    public static void reset() { framebufferCreates=framebufferDeletes=programCreates=shaderCompiles=shaderDraws=maskPasses=blurPasses=composites=programDeletes=shaderDeletes=fontRendererCreates=fontFileLoads=glyphTexturesCreated=glyphTexturesDeleted=0; }
    public static String snapshot() { return "FBO+="+framebufferCreates+" FBO-="+framebufferDeletes+" programs="+programCreates+" compiles="+shaderCompiles+" shaderDraws="+shaderDraws+" mask="+maskPasses+" blur="+blurPasses+" composite="+composites; }
}
