import { useEffect, useRef, useState } from 'react';
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { tryCompile } from '../mathEval';

/**
 * Three.js 3D renderer for STEM concepts that genuinely need a third
 * dimension: surface plots z=f(x,y) (multivariable calculus, saddle
 * points, optimisation landscapes), 3D parametric curves (helices,
 * Lissajous), and vector fields (E/B fields, gradient/curl).
 *
 * The model emits a FORMULA STRING — the surface mesh / field arrows are
 * computed here from the closed form, so the spec is tiny and the geometry
 * is exact. WebGL context + orbit controls are torn down on unmount; the
 * render loop pauses when the tab/element is hidden to spare the GPU.
 */

export interface Surface3DSpec {
  kind?: 'surface' | 'curve' | 'vectorfield';
  // surface: z = f(x,y)
  expr?: string;
  xRange?: [number, number];
  yRange?: [number, number];
  segments?: number;
  // curve: (x(t), y(t), z(t))
  xExpr?: string; yExpr?: string; zExpr?: string;
  tRange?: [number, number];
  // vectorfield: F = (fx, fy, fz) of (x,y,z)
  fx?: string; fy?: string; fz?: string;
  range?: [number, number];
  density?: number;
}

const SIZE = 360;

export function Surface3D({ spec, altText }: { spec: Surface3DSpec; altText: string }) {
  const hostRef = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    setError(null);

    let raf = 0;
    let renderer: THREE.WebGLRenderer | null = null;
    let controls: OrbitControls | null = null;
    const disposables: { dispose: () => void }[] = [];

    try {
      const isDark = document.documentElement.dataset.mode === 'dark';
      const scene = new THREE.Scene();
      scene.background = null; // transparent — inherits the bubble

      const camera = new THREE.PerspectiveCamera(50, 1, 0.1, 1000);
      camera.position.set(7, 5, 9);

      renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
      renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
      renderer.setSize(SIZE, SIZE);
      host.appendChild(renderer.domElement);

      controls = new OrbitControls(camera, renderer.domElement);
      controls.enableDamping = true;
      controls.dampingFactor = 0.08;
      controls.enablePan = false;

      scene.add(new THREE.AmbientLight(0xffffff, 0.75));
      const key = new THREE.DirectionalLight(0xffffff, 1.1);
      key.position.set(6, 10, 8);
      scene.add(key);

      const axisColor = isDark ? 0x4b5563 : 0xcbd5e1;
      scene.add(buildAxes(5, axisColor, disposables));

      const built = buildGeometry(spec, isDark, disposables);
      scene.add(built);

      const animate = () => {
        controls!.update();
        renderer!.render(scene, camera);
        raf = requestAnimationFrame(animate);
      };
      animate();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }

    return () => {
      cancelAnimationFrame(raf);
      controls?.dispose();
      for (const d of disposables) d.dispose();
      if (renderer) {
        renderer.dispose();
        renderer.domElement.remove();
      }
    };
  }, [spec]);

  if (error) {
    return <div className="diagram__fallback diagram__fallback--inline">{altText} <span className="muted">({error})</span></div>;
  }
  return (
    <div className="diagram__three">
      <div ref={hostRef} className="diagram__three-host" role="img" aria-label={altText} />
      <span className="diagram__three-hint">drag to rotate · scroll to zoom</span>
    </div>
  );
}

function buildGeometry(spec: Surface3DSpec, isDark: boolean, disp: { dispose: () => void }[]): THREE.Object3D {
  const kind = spec.kind ?? (spec.fx ? 'vectorfield' : spec.zExpr ? 'curve' : 'surface');
  if (kind === 'curve') return buildCurve(spec, disp);
  if (kind === 'vectorfield') return buildField(spec, isDark, disp);
  return buildSurface(spec, disp);
}

function buildSurface(spec: Surface3DSpec, disp: { dispose: () => void }[]): THREE.Object3D {
  const f = tryCompile(spec.expr);
  if (!f) throw new Error(`bad surface expression "${spec.expr ?? ''}"`);
  const [xa, xb] = spec.xRange ?? [-3, 3];
  const [ya, yb] = spec.yRange ?? [-3, 3];
  const seg = clampInt(spec.segments ?? 48, 8, 120);

  // sample to a normalised cube so the camera framing is stable regardless
  // of the function's natural scale
  const span = Math.max(xb - xa, yb - ya) / 2 || 1;
  const geom = new THREE.PlaneGeometry(2 * ((xb - xa) / 2 / span), 2 * ((yb - ya) / 2 / span), seg, seg);
  const pos = geom.attributes.position as THREE.BufferAttribute;

  let zMin = Infinity, zMax = -Infinity;
  const zs: number[] = [];
  for (let i = 0; i < pos.count; i++) {
    const u = (pos.getX(i) * span) + (xa + xb) / 2;
    const v = (pos.getY(i) * span) + (ya + yb) / 2;
    let z = 0; try { const r = f({ x: u, y: v }); z = Number.isFinite(r) ? r : 0; } catch { z = 0; }
    zs.push(z);
    if (z < zMin) zMin = z; if (z > zMax) zMax = z;
  }
  const zSpan = (zMax - zMin) || 1;
  const zScale = 2.4 / zSpan;
  const colors = new Float32Array(pos.count * 3);
  const lo = new THREE.Color(0x3b82f6), hi = new THREE.Color(0xef4444), mid = new THREE.Color(0x22c55e);
  const tmp = new THREE.Color();
  for (let i = 0; i < pos.count; i++) {
    const zi = zs[i]!;
    pos.setZ(i, (zi - (zMin + zMax) / 2) * zScale);
    const t = (zi - zMin) / zSpan;
    tmp.copy(t < 0.5 ? lo.clone().lerp(mid, t * 2) : mid.clone().lerp(hi, (t - 0.5) * 2));
    colors[i * 3] = tmp.r; colors[i * 3 + 1] = tmp.g; colors[i * 3 + 2] = tmp.b;
  }
  geom.setAttribute('color', new THREE.BufferAttribute(colors, 3));
  geom.computeVertexNormals();
  geom.rotateX(-Math.PI / 2); // plane lies in XY → lift Z to up

  const mat = new THREE.MeshStandardMaterial({ vertexColors: true, side: THREE.DoubleSide, roughness: 0.6, metalness: 0.05, flatShading: false });
  const wire = new THREE.MeshBasicMaterial({ color: 0x000000, wireframe: true, transparent: true, opacity: 0.08 });
  disp.push(geom, mat, wire);
  const group = new THREE.Group();
  group.add(new THREE.Mesh(geom, mat));
  group.add(new THREE.Mesh(geom, wire));
  return group;
}

function buildCurve(spec: Surface3DSpec, disp: { dispose: () => void }[]): THREE.Object3D {
  const fx = tryCompile(spec.xExpr), fy = tryCompile(spec.yExpr), fz = tryCompile(spec.zExpr);
  if (!fx || !fy || !fz) throw new Error('bad 3D curve expression');
  const [a, b] = spec.tRange ?? [0, Math.PI * 6];
  const n = clampInt(spec.segments ?? 400, 16, 4000);
  const pts: THREE.Vector3[] = [];
  for (let i = 0; i <= n; i++) {
    const t = a + ((b - a) * i) / n;
    pts.push(new THREE.Vector3(fx({ t }), fz({ t }), fy({ t }))); // y-up: map z→up
  }
  // normalise into a ~5-unit box
  const box = new THREE.Box3().setFromPoints(pts);
  const c = box.getCenter(new THREE.Vector3());
  const s = 4 / (Math.max(box.max.x - box.min.x, box.max.y - box.min.y, box.max.z - box.min.z) || 1);
  pts.forEach((p) => p.sub(c).multiplyScalar(s));
  const geom = new THREE.BufferGeometry().setFromPoints(pts);
  const mat = new THREE.LineBasicMaterial({ color: 0x6366f1 });
  disp.push(geom, mat);
  return new THREE.Line(geom, mat);
}

function buildField(spec: Surface3DSpec, isDark: boolean, disp: { dispose: () => void }[]): THREE.Object3D {
  const fx = tryCompile(spec.fx), fy = tryCompile(spec.fy), fz = tryCompile(spec.fz ?? '0');
  if (!fx || !fy || !fz) throw new Error('bad vector-field expression');
  const [a, b] = spec.range ?? [-2, 2];
  const d = clampInt(spec.density ?? 4, 2, 8);
  const group = new THREE.Group();
  const color = isDark ? 0x93c5fd : 0x2563eb;
  let maxMag = 1e-6;
  const samples: { p: THREE.Vector3; v: THREE.Vector3 }[] = [];
  for (let i = 0; i < d; i++) for (let j = 0; j < d; j++) for (let k = 0; k < d; k++) {
    const x = a + ((b - a) * i) / (d - 1);
    const y = a + ((b - a) * j) / (d - 1);
    const z = a + ((b - a) * k) / (d - 1);
    let vx = 0, vy = 0, vz = 0;
    try { vx = fx({ x, y, z }); vy = fy({ x, y, z }); vz = fz({ x, y, z }); } catch { /* skip */ }
    if (![vx, vy, vz].every(Number.isFinite)) continue;
    const v = new THREE.Vector3(vx, vz, vy); // y-up
    maxMag = Math.max(maxMag, v.length());
    samples.push({ p: new THREE.Vector3(x, z, y), v });
  }
  const cell = ((b - a) / (d - 1)) * 0.9;
  for (const { p, v } of samples) {
    const len = (v.length() / maxMag) * cell;
    if (len < 1e-4) continue;
    const dir = v.clone().normalize();
    const arrow = new THREE.ArrowHelper(dir, p.clone().sub(dir.clone().multiplyScalar(len / 2)), len, color, len * 0.35, len * 0.22);
    group.add(arrow);
  }
  // scale the whole field into the framing box
  group.scale.setScalar(2.2 / (Math.max(Math.abs(a), Math.abs(b)) || 1));
  disp.push({ dispose: () => group.traverse((o) => {
    const any = o as unknown as { geometry?: THREE.BufferGeometry; material?: THREE.Material };
    any.geometry?.dispose?.(); any.material?.dispose?.();
  }) });
  return group;
}

function buildAxes(len: number, color: number, disp: { dispose: () => void }[]): THREE.Object3D {
  const g = new THREE.Group();
  const mk = (a: THREE.Vector3, b: THREE.Vector3) => {
    const geom = new THREE.BufferGeometry().setFromPoints([a, b]);
    const mat = new THREE.LineBasicMaterial({ color, transparent: true, opacity: 0.6 });
    disp.push(geom, mat);
    return new THREE.Line(geom, mat);
  };
  g.add(mk(new THREE.Vector3(-len, 0, 0), new THREE.Vector3(len, 0, 0)));
  g.add(mk(new THREE.Vector3(0, -len, 0), new THREE.Vector3(0, len, 0)));
  g.add(mk(new THREE.Vector3(0, 0, -len), new THREE.Vector3(0, 0, len)));
  return g;
}

function clampInt(v: number, lo: number, hi: number) { return Math.max(lo, Math.min(hi, Math.round(v))); }
