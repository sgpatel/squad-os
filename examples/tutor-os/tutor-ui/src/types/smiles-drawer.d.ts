/**
 * Minimal ambient types for `smiles-drawer` v2.x.
 *
 * The package ships ESM but no .d.ts. We only use the SvgDrawer + parse
 * pair, so we type just those — keep it tight; the upstream surface is
 * larger but unused here.
 */
declare module 'smiles-drawer' {
  export interface SvgDrawerOptions {
    width?: number;
    height?: number;
    bondThickness?: number;
    bondLength?: number;
    shortBondLength?: number;
    bondSpacing?: number;
    atomVisualization?: 'default' | 'balls' | 'allballs';
    compactDrawing?: boolean;
    fontSizeLarge?: number;
    fontSizeSmall?: number;
    padding?: number;
    terminalCarbons?: boolean;
    explicitHydrogens?: boolean;
  }

  export class SvgDrawer {
    constructor(options?: SvgDrawerOptions);
    draw(tree: unknown, target: SVGElement | string, theme?: 'light' | 'dark' | string, infoOnly?: boolean): void;
  }

  export class Drawer {
    constructor(options?: SvgDrawerOptions);
    draw(tree: unknown, target: HTMLCanvasElement | string, theme?: string, infoOnly?: boolean): void;
  }

  export function parse(
    smiles: string,
    onSuccess: (tree: unknown) => void,
    onError?: (err: unknown) => void
  ): void;

  const SmilesDrawer: {
    SvgDrawer: typeof SvgDrawer;
    Drawer: typeof Drawer;
    parse: typeof parse;
  };
  export default SmilesDrawer;
}
