/* tslint:disable */
/* eslint-disable */

/**
 * One component on one canvas.
 */
export class EguiComponent {
    private constructor();
    free(): void;
    [Symbol.dispose](): void;
    /**
     * Stops drawing and lets go of the canvas's event listeners.
     */
    destroy(): void;
    /**
     * Makes the component `name` with its first `props` (JSON) and starts drawing it on `canvas`; each event is
     * passed to `on_event` as a JSON string. Fails for an unknown name or bad props.
     */
    static mount(canvas: HTMLCanvasElement, name: string, props: string, on_event: Function, dark: boolean): Promise<EguiComponent>;
    /**
     * The components this module can make.
     */
    static names(): string[];
    /**
     * How much of egui-4j's log reaches the console (and Java): "off", "error", "warn", "info" (at first),
     * "debug" (props given) or "trace" (also each event, and each point streamed in).
     */
    static set_log_level(level: string): void;
    /**
     * Light or dark, the accent colour, and the background (`#rrggbb`; empty or left out: transparent, the page's).
     */
    set_theme(dark: boolean, accent?: string | null, background?: string | null): void;
    /**
     * New props (JSON): what is given replaces what was there. Throws, changing nothing, on bad props.
     */
    update(props: string): void;
}

export type InitInput = RequestInfo | URL | Response | BufferSource | WebAssembly.Module;

export interface InitOutput {
    readonly memory: WebAssembly.Memory;
    readonly __wbg_eguicomponent_free: (a: number, b: number) => void;
    readonly eguicomponent_destroy: (a: number) => void;
    readonly eguicomponent_mount: (a: number, b: number, c: number, d: number, e: number, f: number, g: number) => number;
    readonly eguicomponent_names: (a: number) => void;
    readonly eguicomponent_set_log_level: (a: number, b: number, c: number) => void;
    readonly eguicomponent_set_theme: (a: number, b: number, c: number, d: number, e: number, f: number, g: number) => void;
    readonly eguicomponent_update: (a: number, b: number, c: number, d: number) => void;
    readonly __wasm_bindgen_func_elem_6176: (a: number, b: number, c: number, d: number) => void;
    readonly __wasm_bindgen_func_elem_6196: (a: number, b: number, c: number, d: number) => void;
    readonly __wasm_bindgen_func_elem_1011: (a: number, b: number, c: number) => void;
    readonly __wasm_bindgen_func_elem_1011_9: (a: number, b: number, c: number) => void;
    readonly __wasm_bindgen_func_elem_1013: (a: number, b: number, c: number) => void;
    readonly __wbindgen_export: (a: number, b: number) => number;
    readonly __wbindgen_export2: (a: number, b: number, c: number, d: number) => number;
    readonly __wbindgen_export3: (a: number) => void;
    readonly __wbindgen_export4: (a: number, b: number, c: number) => void;
    readonly __wbindgen_export5: (a: number, b: number) => void;
    readonly __wbindgen_add_to_stack_pointer: (a: number) => number;
}

export type SyncInitInput = BufferSource | WebAssembly.Module;

/**
 * Instantiates the given `module`, which can either be bytes or
 * a precompiled `WebAssembly.Module`.
 *
 * @param {{ module: SyncInitInput }} module - Passing `SyncInitInput` directly is deprecated.
 *
 * @returns {InitOutput}
 */
export function initSync(module: { module: SyncInitInput } | SyncInitInput): InitOutput;

/**
 * If `module_or_path` is {RequestInfo} or {URL}, makes a request and
 * for everything else, calls `WebAssembly.instantiate` directly.
 *
 * @param {{ module_or_path: InitInput | Promise<InitInput> }} module_or_path - Passing `InitInput` directly is deprecated.
 *
 * @returns {Promise<InitOutput>}
 */
export default function __wbg_init (module_or_path?: { module_or_path: InitInput | Promise<InitInput> } | InitInput | Promise<InitInput>): Promise<InitOutput>;
