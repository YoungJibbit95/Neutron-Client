package me.youngjibbit.utils.render;
/**
 * Opt-in render-thread counters. No clock reads, GL queries, allocation or timing estimates.
 * Bind counts are intercepted API requests, including redundant requests, rather
 * than physical state changes. Draw counts are intercepted API calls (one multi-draw
 * or call-lists invocation is one submission); they do not count primitives or GPU
 * completions and may include commands recorded into a display list. Raw GL calls
 * outside the instrumented wrappers and upload/draw paths are not intercepted.
 */
public final class RenderDiagnostics {
    public static boolean enabled;
    public static long framebufferCreates, framebufferDeletes, programCreates, shaderCompiles, shaderDraws, programDeletes, shaderDeletes;
    public static long fontRendererCreates, fontFileLoads, glyphTexturesCreated, glyphTexturesDeleted;
    public static long maskPasses, blurPasses, composites, fontBatches, glyphUploads;
    public static long framebufferBinds, programBinds, drawSubmissions, sceneCopies;
    /** Sum of output width*height for submitted scene-blur passes, excluding halo passes. */
    public static long sceneBlurPixels;
    /** Actually submitted paired Gaussian passes, and requested passes using the safe fallback. */
    public static long pairedGaussianPasses, pairedGaussianFallbacks;
    private RenderDiagnostics() { }
    public static void reset() { framebufferCreates=framebufferDeletes=programCreates=shaderCompiles=shaderDraws=maskPasses=blurPasses=composites=programDeletes=shaderDeletes=fontRendererCreates=fontFileLoads=glyphTexturesCreated=glyphTexturesDeleted=fontBatches=glyphUploads=framebufferBinds=programBinds=drawSubmissions=sceneCopies=sceneBlurPixels=pairedGaussianPasses=pairedGaussianFallbacks=0; }
    public static String snapshot() { return "FBO+="+framebufferCreates+" FBO-="+framebufferDeletes+" programs="+programCreates+" compiles="+shaderCompiles+" shaderDraws="+shaderDraws+" mask="+maskPasses+" blur="+blurPasses+" composite="+composites; }
}
