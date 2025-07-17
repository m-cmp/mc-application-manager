declare module 'bootstrap' {
  export class Modal {
    constructor(element: HTMLElement, options?: any);
    show(): void;
    hide(): void;
    toggle(): void;
    dispose(): void;
    static getInstance(element: HTMLElement): Modal | null;
    static getOrCreateInstance(element: HTMLElement, options?: any): Modal;
  }

  export class Tooltip {
    constructor(element: HTMLElement, options?: any);
    show(): void;
    hide(): void;
    toggle(): void;
    dispose(): void;
    static getInstance(element: HTMLElement): Tooltip | null;
    static getOrCreateInstance(element: HTMLElement, options?: any): Tooltip;
  }

  export class Popover {
    constructor(element: HTMLElement, options?: any);
    show(): void;
    hide(): void;
    toggle(): void;
    dispose(): void;
    static getInstance(element: HTMLElement): Popover | null;
    static getOrCreateInstance(element: HTMLElement, options?: any): Popover;
  }

  export class Dropdown {
    constructor(element: HTMLElement, options?: any);
    show(): void;
    hide(): void;
    toggle(): void;
    dispose(): void;
    static getInstance(element: HTMLElement): Dropdown | null;
    static getOrCreateInstance(element: HTMLElement, options?: any): Dropdown;
  }

  export class Collapse {
    constructor(element: HTMLElement, options?: any);
    show(): void;
    hide(): void;
    toggle(): void;
    dispose(): void;
    static getInstance(element: HTMLElement): Collapse | null;
    static getOrCreateInstance(element: HTMLElement, options?: any): Collapse;
  }
} 