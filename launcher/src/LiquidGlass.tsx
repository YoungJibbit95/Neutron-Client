import { useEffect, useRef } from 'react';

// A static refracted light field: no perpetual render loop or text distortion.
export default function LiquidGlass({ color, strength }: { color: string; strength: number }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const element = canvas.current; if (!element) return;
    const gl = element.getContext('webgl', { alpha: true, antialias: false, powerPreference: 'low-power' });
    if (!gl) return;
    const shaders: WebGLShader[] = []; let program: WebGLProgram | null = null; let buffer: WebGLBuffer | null = null;
    const lost = (event: Event) => { event.preventDefault(); element.style.opacity = '0'; };
    element.addEventListener('webglcontextlost', lost);
    try {
      const compile = (type: number, source: string) => {
        const shader = gl.createShader(type); if (!shader) throw new Error('Shader unavailable'); shaders.push(shader);
        gl.shaderSource(shader, source); gl.compileShader(shader);
        if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error('Shader compilation failed'); return shader;
      };
      program = gl.createProgram(); if (!program) throw new Error('Program unavailable');
      gl.attachShader(program, compile(gl.VERTEX_SHADER, 'attribute vec2 p; varying vec2 uv; void main(){uv=p*.5+.5;gl_Position=vec4(p,0.,1.);}'));
      gl.attachShader(program, compile(gl.FRAGMENT_SHADER, `precision mediump float; varying vec2 uv; uniform vec3 tint;
        void main(){ vec2 p=(uv-.5)*vec2(2.8,1.); vec2 q=abs(p)-vec2(1.25,.32);
        float d=length(max(q,0.))+min(max(q.x,q.y),0.)-.075;
        vec2 normal=normalize(p*vec2(.35,1.)+vec2(.001)); vec2 refracted=uv+normal*.045*exp(-abs(d)*22.);
        float light=.5+.5*sin(refracted.x*5.+refracted.y*3.); float rim=exp(-abs(d)*70.);
        float inside=1.-smoothstep(-.01,.015,d); vec3 surface=mix(tint,vec3(.9,.6,.75),light*.35);
        float a=(.035*light*inside+.24*rim)*(.5+.5*uv.x); gl_FragColor=vec4(surface*a,a); }`));
      gl.linkProgram(program); if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error('Shader link failed');
      element.width = 768; element.height = 256; gl.viewport(0, 0, 768, 256); gl.useProgram(program);
      buffer = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buffer); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]), gl.STATIC_DRAW);
      const location = gl.getAttribLocation(program, 'p'); gl.enableVertexAttribArray(location); gl.vertexAttribPointer(location, 2, gl.FLOAT, false, 0, 0);
      gl.uniform3f(gl.getUniformLocation(program, 'tint'), ...[1,3,5].map(offset => parseInt(color.slice(offset, offset + 2), 16) / 255) as [number, number, number]);
      gl.drawArrays(gl.TRIANGLES, 0, 6); element.style.opacity = String(strength / 100);
    } catch { element.style.opacity = '0'; }
    return () => { element.removeEventListener('webglcontextlost', lost); if (buffer) gl.deleteBuffer(buffer); if (program) gl.deleteProgram(program); for (const shader of shaders) gl.deleteShader(shader); };
  }, [color, strength]);
  return <canvas ref={canvas} className="liquid-glass" aria-hidden="true" />;
}
