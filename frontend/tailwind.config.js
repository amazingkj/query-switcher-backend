/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        'sans': ['Plus Jakarta Sans', 'Noto Sans KR', 'system-ui', 'sans-serif'],
        'mono': ['Monaco', 'Menlo', 'Ubuntu Mono', 'Courier New', 'monospace'],
      },
      colors: {
        // Modernize Primary Colors
        primary: {
          50: '#ECF2FF',
          100: '#D9E5FF',
          200: '#B3CBFF',
          300: '#8DB1FF',
          400: '#6697FF',
          500: '#5D87FF', // Main primary
          600: '#4A6CD9',
          700: '#3751B3',
          800: '#24378C',
          900: '#121C66',
        },
        // Modernize Secondary Colors
        secondary: {
          50: '#E6F7FF',
          100: '#CCF0FF',
          200: '#99E1FF',
          300: '#66D2FF',
          400: '#49BEFF', // Main secondary
          500: '#33B5FF',
          600: '#2991CC',
          700: '#1F6D99',
          800: '#144966',
          900: '#0A2433',
        },
        // Modernize Success
        success: {
          50: '#E6FDF7',
          100: '#CCFBEF',
          200: '#99F7DF',
          300: '#66F3CF',
          400: '#39EDBF',
          500: '#13DEB9', // Main success
          600: '#0FB294',
          700: '#0B856F',
          800: '#08594A',
          900: '#042C25',
        },
        // Modernize Warning
        warning: {
          50: '#FFF8E6',
          100: '#FFF1CC',
          200: '#FFE399',
          300: '#FFD566',
          400: '#FFC633',
          500: '#FFAE1F', // Main warning
          600: '#CC8B19',
          700: '#996813',
          800: '#66460C',
          900: '#332306',
        },
        // Modernize Error
        error: {
          50: '#FFF1ED',
          100: '#FFE3DA',
          200: '#FFC7B6',
          300: '#FFAB91',
          400: '#FF8F6D',
          500: '#FA896B', // Main error
          600: '#C86E56',
          700: '#965240',
          800: '#64372B',
          900: '#321B15',
        },
        // Modernize Info
        info: {
          50: '#E6F4FF',
          100: '#CCE9FF',
          200: '#99D3FF',
          300: '#66BDFF',
          400: '#33A7FF',
          500: '#539BFF',
          600: '#427CCC',
          700: '#325D99',
          800: '#213E66',
          900: '#111F33',
        },
        // Light/Dark backgrounds
        light: {
          DEFAULT: '#FFFFFF',
          100: '#F5F7FA',
          200: '#EAEFF4',
          300: '#DFE5EB',
        },
        dark: {
          DEFAULT: '#2A3447',
          100: '#333F55',
          200: '#3D4A61',
          300: '#465670',
          400: '#1C2536',
          500: '#171C28',
        },
      },
      boxShadow: {
        'card': '0 1px 3px 0 rgba(0, 0, 0, 0.08)',
        'card-hover': '0 4px 12px 0 rgba(0, 0, 0, 0.12)',
        'dropdown': '0 4px 24px 0 rgba(0, 0, 0, 0.15)',
        'button': '0 2px 4px 0 rgba(93, 135, 255, 0.25)',
        'button-hover': '0 4px 8px 0 rgba(93, 135, 255, 0.35)',
      },
      borderRadius: {
        'card': '12px',
        'button': '8px',
        'input': '8px',
      },
      transitionDuration: {
        'fast': '150ms',
        'normal': '250ms',
        'slow': '350ms',
      },
    },
  },
  plugins: [],
}
