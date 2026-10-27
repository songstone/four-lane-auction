import type { Config } from 'tailwindcss';

export default {
  theme: {
    colors: {
      transparent: 'transparent',
      current: 'currentColor',
      canvas: '#f8fafc',
      surface: '#ffffff',
      ink: '#111827',
      muted: '#64748b',
      line: '#e2e8f0',
      brand: '#1d4ed8',
      'brand-hover': '#1e40af',
      'brand-soft': '#eff6ff',
      success: '#166534',
      'success-soft': '#f0fdf4',
      danger: '#b91c1c',
      'danger-soft': '#fef2f2',
      overlay: 'rgb(15 23 42 / 0.45)',
    },
    fontFamily: {
      sans: ['system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'sans-serif'],
    },
    spacing: {
      0: '0px',
      px: '1px',
      1: '0.25rem',
      2: '0.5rem',
      3: '0.75rem',
      4: '1rem',
      5: '1.25rem',
      6: '1.5rem',
      8: '2rem',
      10: '2.5rem',
      12: '3rem',
      16: '4rem',
      20: '5rem',
      24: '6rem',
    },
    extend: {},
  },
} satisfies Config;
