package me.youngjibbit.utils.render.shader;

/**
 * Canonical shader facade for the shader-render stack.
 * This class intentionally delegates to the shared implementation in utils.render.
 */
public class ShaderUtil extends me.youngjibbit.utils.render.ShaderUtil {

    public ShaderUtil(String fragmentShaderLoc, String vertexShaderLoc) {
        super(fragmentShaderLoc, vertexShaderLoc);
    }

    public ShaderUtil(String fragmentShadersrc, boolean notUsed) {
        super(fragmentShadersrc, notUsed);
    }

    public ShaderUtil(String fragmentShaderLoc) {
        super(fragmentShaderLoc);
    }
}
