declare module 'bootstrap-vue-3' {
  import { App, Plugin } from 'vue';

  export interface BootstrapVue3Options {
    components?: boolean;
    directives?: boolean;
  }

  export const BootstrapVue3: Plugin & {
    install(app: App, options?: BootstrapVue3Options): void;
  };

  export default BootstrapVue3;

  // Toast related exports
  export interface ToastOptions {
    title?: string;
    body?: string;
    variant?: string;
    autoHide?: boolean;
    delay?: number;
    noCloseButton?: boolean;
    solid?: boolean;
    toaster?: string;
  }

  export interface BToast {
    show(options?: ToastOptions): void;
    hide(id?: string): void;
    clear(): void;
  }

  export const useToast: () => BToast;

  // Component exports (common ones)
  export const BAlert: any;
  export const BBadge: any;
  export const BButton: any;
  export const BCard: any;
  export const BModal: any;
  export const BToast: any;
  export const BTable: any;
  export const BForm: any;
  export const BFormInput: any;
  export const BFormSelect: any;
  export const BFormTextarea: any;
  export const BFormCheckbox: any;
  export const BFormRadio: any;
  export const BDropdown: any;
  export const BNavbar: any;
  export const BContainer: any;
  export const BRow: any;
  export const BCol: any;
} 