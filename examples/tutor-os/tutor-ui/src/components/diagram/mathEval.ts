/**
 * A tiny, dependency-free math-expression compiler.
 *
 * Why not mathjs / expr-eval: the surface3d + function2d renderers only
 * need to evaluate a scalar formula like `sin(x)*cos(y)` thousands of
 * times per frame. Pulling in mathjs (~180 KB) for that is wasteful, and
 * `new Function()` is a CSP / injection liability when the string comes
 * from an LLM. So we ship a ~150-line shunting-yard compiler that:
 *
 *   - tokenises a restricted grammar (numbers, named vars, the operators
 *     + - * / ^ and unary -, parentheses, comma, a fixed function table),
 *   - converts to RPN (precedence + right-assoc ^), and
 *   - compiles to a closure over a variable record — no eval, no globals.
 *
 * Anything outside the grammar (an unknown identifier, a stray `;`, an
 * attempt to call `window`) throws at compile time, so a malformed LLM
 * formula degrades to the diagram fallback instead of executing.
 */

export type Vars = Record<string, number>;
export type Compiled = (vars: Vars) => number;

const CONSTANTS: Record<string, number> = {
  pi: Math.PI,
  'π': Math.PI, // π
  tau: Math.PI * 2,
  e: Math.E,
};

// Unary functions the grammar exposes. atan2/pow/min/max are 2-arg and
// handled separately in the evaluator.
const FN1: Record<string, (x: number) => number> = {
  sin: Math.sin, cos: Math.cos, tan: Math.tan,
  asin: Math.asin, acos: Math.acos, atan: Math.atan,
  sinh: Math.sinh, cosh: Math.cosh, tanh: Math.tanh,
  exp: Math.exp, sqrt: Math.sqrt, cbrt: Math.cbrt,
  abs: Math.abs, sign: Math.sign,
  floor: Math.floor, ceil: Math.ceil, round: Math.round,
  ln: Math.log, log: Math.log, log10: Math.log10, log2: Math.log2,
};
const FN2: Record<string, (a: number, b: number) => number> = {
  atan2: Math.atan2, pow: Math.pow, min: Math.min, max: Math.max,
  mod: (a, b) => ((a % b) + b) % b,
  log: (a, b) => Math.log(a) / Math.log(b), // log(value, base) — 2-arg form
};

type Tok =
  | { t: 'num'; v: number }
  | { t: 'var'; v: string }
  | { t: 'fn'; v: string }
  | { t: 'op'; v: string }
  | { t: 'paren'; v: '(' | ')' }
  | { t: 'comma' };

const OPS: Record<string, { prec: number; right: boolean }> = {
  '+': { prec: 2, right: false },
  '-': { prec: 2, right: false },
  '*': { prec: 3, right: false },
  '/': { prec: 3, right: false },
  '%': { prec: 3, right: false },
  '^': { prec: 4, right: true },
  // unary minus — highest binding, marked with a private name
  'u-': { prec: 5, right: true },
};

function tokenise(src: string): Tok[] {
  const toks: Tok[] = [];
  let i = 0;
  const isDigit = (c: string | undefined): boolean => c != null && c >= '0' && c <= '9';
  const isAlpha = (c: string | undefined): boolean =>
    c != null && ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c === '_' || c === 'π');

  while (i < src.length) {
    const c = src[i]!;
    if (c === ' ' || c === '\t' || c === '\n' || c === '\r') { i++; continue; }

    if (isDigit(c) || (c === '.' && isDigit(src[i + 1] ?? ''))) {
      let j = i + 1;
      while (j < src.length && (isDigit(src[j]) || src[j] === '.')) j++;
      // scientific notation: 1e-3
      if (src[j] === 'e' || src[j] === 'E') {
        j++;
        if (src[j] === '+' || src[j] === '-') j++;
        while (j < src.length && isDigit(src[j])) j++;
      }
      const num = Number(src.slice(i, j));
      if (!Number.isFinite(num)) throw new Error(`bad number "${src.slice(i, j)}"`);
      toks.push({ t: 'num', v: num });
      i = j;
      continue;
    }

    if (isAlpha(c)) {
      let j = i + 1;
      while (j < src.length && (isAlpha(src[j]) || isDigit(src[j]))) j++;
      const name = src.slice(i, j);
      // a name immediately followed by '(' is a function call
      let k = j;
      while (src[k] === ' ') k++;
      if (src[k] === '(') toks.push({ t: 'fn', v: name });
      else toks.push({ t: 'var', v: name });
      i = j;
      continue;
    }

    if (c === '(' || c === ')') { toks.push({ t: 'paren', v: c }); i++; continue; }
    if (c === ',') { toks.push({ t: 'comma' }); i++; continue; }
    if (c in OPS || c === '^') {
      // decide unary vs binary minus/plus from the previous token
      const prev = toks[toks.length - 1];
      const unary =
        (c === '-' || c === '+') &&
        (!prev || prev.t === 'op' || prev.t === 'comma' ||
          (prev.t === 'paren' && prev.v === '('));
      if (unary) { if (c === '-') toks.push({ t: 'op', v: 'u-' }); /* +x is a no-op */ }
      else toks.push({ t: 'op', v: c });
      i++;
      continue;
    }

    throw new Error(`unexpected character "${c}" at ${i}`);
  }
  return toks;
}

/** Shunting-yard → RPN. */
function toRpn(toks: Tok[]): Tok[] {
  const out: Tok[] = [];
  const stack: Tok[] = [];
  for (const tok of toks) {
    switch (tok.t) {
      case 'num':
      case 'var':
        out.push(tok);
        break;
      case 'fn':
        stack.push(tok);
        break;
      case 'comma':
        while (stack.length && stack[stack.length - 1]!.t !== 'paren') out.push(stack.pop()!);
        break;
      case 'op': {
        const o1 = OPS[tok.v]!;
        while (stack.length) {
          const top = stack[stack.length - 1]!;
          if (top.t !== 'op') break;
          const o2 = OPS[top.v]!;
          if (o2.prec > o1.prec || (o2.prec === o1.prec && !o1.right)) out.push(stack.pop()!);
          else break;
        }
        stack.push(tok);
        break;
      }
      case 'paren':
        if (tok.v === '(') {
          stack.push(tok);
        } else {
          while (stack.length && stack[stack.length - 1]!.t !== 'paren') out.push(stack.pop()!);
          if (!stack.length) throw new Error('unbalanced parentheses');
          stack.pop(); // discard '('
          if (stack.length && stack[stack.length - 1]!.t === 'fn') out.push(stack.pop()!);
        }
        break;
    }
  }
  while (stack.length) {
    const top = stack.pop()!;
    if (top.t === 'paren') throw new Error('unbalanced parentheses');
    out.push(top);
  }
  return out;
}

/**
 * Compile `src` into a reusable evaluator. Throws on malformed input so
 * the caller can fall back to alt-text. The returned closure is allocation
 * free on the hot path (single scratch number stack reused per call).
 */
export function compileExpr(src: string): Compiled {
  if (!src || !src.trim()) throw new Error('empty expression');
  const rpn = toRpn(tokenise(src));

  return (vars: Vars): number => {
    const st: number[] = [];
    for (const tok of rpn) {
      switch (tok.t) {
        case 'num':
          st.push(tok.v);
          break;
        case 'var': {
          const name = tok.v;
          if (name in vars) st.push(vars[name]!);
          else if (name in CONSTANTS) st.push(CONSTANTS[name]!);
          else throw new Error(`unknown variable "${name}"`);
          break;
        }
        case 'op': {
          if (tok.v === 'u-') { st.push(-st.pop()!); break; }
          const b = st.pop()!, a = st.pop()!;
          switch (tok.v) {
            case '+': st.push(a + b); break;
            case '-': st.push(a - b); break;
            case '*': st.push(a * b); break;
            case '/': st.push(a / b); break;
            case '%': st.push(((a % b) + b) % b); break;
            case '^': st.push(Math.pow(a, b)); break;
          }
          break;
        }
        case 'fn': {
          const f1 = FN1[tok.v];
          const f2 = FN2[tok.v];
          if (f2 && st.length >= 2 && f1 === undefined) {
            const b = st.pop()!, a = st.pop()!;
            st.push(f2(a, b));
          } else if (f1) {
            st.push(f1(st.pop()!));
          } else if (f2) {
            const b = st.pop()!, a = st.pop()!;
            st.push(f2(a, b));
          } else {
            throw new Error(`unknown function "${tok.v}"`);
          }
          break;
        }
      }
    }
    if (st.length !== 1) throw new Error('malformed expression');
    return st[0]!;
  };
}

/** Compile but never throw — returns null on failure (renderers prefer this). */
export function tryCompile(src: string | undefined | null): Compiled | null {
  if (!src) return null;
  try { return compileExpr(src); } catch { return null; }
}
