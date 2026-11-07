import { useEffect, useRef } from 'react';
import { glassFragment } from './glass-material';

// Draw only on layout/theme changes. One context serves all material panels;
// scrolling never captures text or runs an idle animation loop.
export default function LiquidGlass({ color, strength }: { color: string; strength: number }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const element = canvas.current; const host = element?.parentElement;
    if (!element || !host || strength <= 0) return;
    const gl = element.getContext('webgl', { alpha: true, antialias: false, powerPreference: 'low-power' });
    if (!gl) return;
    let program: WebGLProgram | null = null; let buffer: WebGLBuffer | null = null;
    const shaders: WebGLShader[] = []; const textures: WebGLTexture[] = [];
    let stopped = false; let frame = 0;
    const fallback = () => { delete host.dataset.glass; element.style.opacity = '0'; };
    const lost = (event: Event) => { event.preventDefault(); stopped = true; fallback(); };
    element.addEventListener('webglcontextlost', lost);
    const observer = new ResizeObserver(() => schedule());
    const mutations = new MutationObserver(records => {
      const material = (node: Node) => node instanceof Element && (node.matches('.surface') || !!node.querySelector('.surface'));
      if (!records.some(record => [...record.addedNodes, ...record.removedNodes].some(material))) return;
      observe(); schedule();
    });
    const schedule = () => { if (!stopped && !frame) frame = requestAnimationFrame(() => { frame = 0; try { draw(); } catch { stopped = true; fallback(); } }); };
    const observe = () => { observer.disconnect(); observer.observe(host); host.querySelectorAll('.surface').forEach(panel => observer.observe(panel)); };
    const scroll = (event: Event) => { if (!(event.target instanceof Element) || !event.target.closest('.console')) schedule(); };
    let draw = () => {};
    try {
      const compile = (type: number, source: string) => {
        const shader = gl.createShader(type); if (!shader) throw new Error('Shader unavailable'); shaders.push(shader);
        gl.shaderSource(shader, source); gl.compileShader(shader);
        if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error('Shader compilation failed'); return shader;
      };
      program = gl.createProgram(); if (!program) throw new Error('Program unavailable');
      gl.attachShader(program, compile(gl.VERTEX_SHADER, 'attribute vec2 p; varying vec2 uv; void main(){uv=p*.5+.5;gl_Position=vec4(p,0.,1.);}'));
      gl.attachShader(program, compile(gl.FRAGMENT_SHADER, glassFragment));
      gl.linkProgram(program); if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error('Shader link failed');
      buffer = gl.createBuffer(); if (!buffer) throw new Error('Buffer unavailable');
      gl.bindBuffer(gl.ARRAY_BUFFER, buffer); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]), gl.STATIC_DRAW);
      gl.useProgram(program);
      const attribute = gl.getAttribLocation(program, 'p'); gl.enableVertexAttribArray(attribute); gl.vertexAttribPointer(attribute, 2, gl.FLOAT, false, 0, 0);
      const clean = document.createElement('canvas'); const soft = document.createElement('canvas');
      for (let unit = 0; unit < 2; unit++) {
        const texture = gl.createTexture(); if (!texture) throw new Error('Texture unavailable'); textures.push(texture);
        gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, texture);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      }
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
      gl.uniform1i(gl.getUniformLocation(program!, 'cleanScene'), 0); gl.uniform1i(gl.getUniformLocation(program!, 'softScene'), 1);
      gl.uniform3f(gl.getUniformLocation(program!, 'tint'), ...[1,3,5].map(offset => parseInt(color.slice(offset, offset + 2), 16) / 255) as [number, number, number]);
      gl.uniform1f(gl.getUniformLocation(program!, 'effect'), strength / 100);
      gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
      let previous = ''; let previousGeometry = '';
      draw = () => {
        const bounds = host.getBoundingClientRect(); if (!bounds.width || !bounds.height) return;
        // Bound backing-store cost even under very high desktop DPI.
        const ratio = Math.min(devicePixelRatio || 1, 1.5, 1920 / bounds.width, 1600 / bounds.height);
        const width = Math.max(1, Math.round(bounds.width * ratio)); const height = Math.max(1, Math.round(bounds.height * ratio));
        const panels = [...host.querySelectorAll<HTMLElement>('.surface')].map(panel => panel.getBoundingClientRect()).filter(box => box.width && box.height && box.bottom >= bounds.top && box.top <= bounds.bottom);
        const geometry = [width, height, ...panels.flatMap(box => [box.left - bounds.left, bounds.bottom - box.bottom, box.width, box.height])].map(value => Math.round(value * 100) / 100).join(':');
        if (geometry === previousGeometry) return;
        previousGeometry = geometry;
        if (element.width !== width || element.height !== height) { element.width = width; element.height = height; }
        const signature = `${width}:${height}`;
        if (previous !== signature) {
          previous = signature; clean.width = width; clean.height = height; soft.width = width; soft.height = height;
          const ctx = clean.getContext('2d'); const softened = soft.getContext('2d'); if (!ctx || !softened) throw new Error('Scene unavailable');
          const style = getComputedStyle(host); const base = style.getPropertyValue('--background').trim() || '#171719';
          ctx.fillStyle = base; ctx.fillRect(0, 0, width, height);
          const light = ctx.createLinearGradient(0, 0, width, height);
          light.addColorStop(0, color + '06'); light.addColorStop(.58, color + '2e'); light.addColorStop(1, base);
          ctx.fillStyle = light; ctx.beginPath(); ctx.moveTo(-width * .2, height * .17);
          ctx.bezierCurveTo(width * .6, -height * .35, width * .7, height * .6, width * 1.2, height * .34);
          ctx.lineTo(width * 1.2, height); ctx.lineTo(-width * .2, height); ctx.closePath(); ctx.fill();
          softened.filter = 'blur(18px)'; softened.drawImage(clean, 0, 0);
          [clean, soft].forEach((scene, unit) => { gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, textures[unit]); gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, scene); });
        }
        gl.viewport(0, 0, width, height); gl.uniform2f(gl.getUniformLocation(program!, 'size'), bounds.width, bounds.height);
        gl.uniform1f(gl.getUniformLocation(program!, 'panel'), 0); gl.drawArrays(gl.TRIANGLES, 0, 6);
        gl.uniform1f(gl.getUniformLocation(program!, 'panel'), 1);
        panels.forEach(box => {
          gl.uniform4f(gl.getUniformLocation(program!, 'rect'), box.left - bounds.left, bounds.bottom - box.bottom, box.width, box.height);
          gl.drawArrays(gl.TRIANGLES, 0, 6);
        });
        if (gl.isContextLost()) throw new Error('Context lost');
        host.dataset.glass = 'true'; element.style.opacity = '1';
      };
      draw(); observe(); mutations.observe(host, { childList: true, subtree: true });
      host.addEventListener('scroll', scroll, true); window.addEventListener('resize', schedule);
    } catch { stopped = true; fallback(); }
    return () => {
      stopped = true; cancelAnimationFrame(frame); observer.disconnect(); mutations.disconnect();
      host.removeEventListener('scroll', scroll, true); window.removeEventListener('resize', schedule);
      element.removeEventListener('webglcontextlost', lost); fallback();
      if (buffer) gl.deleteBuffer(buffer); if (program) gl.deleteProgram(program);
      shaders.forEach(shader => gl.deleteShader(shader)); textures.forEach(texture => gl.deleteTexture(texture));
    };
  }, [color, strength]);
  return <canvas ref={canvas} className="liquid-glass" aria-hidden="true" />;
}
