/** @type {import('tailwindcss').Config} */
export default {
  darkMode: "class",
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: "var(--color-primary)",
          foreground: "var(--color-primary-foreground)",
        },
        background: {
          DEFAULT: "var(--color-background)",
          alt: "var(--color-background-alt)",
        },
        accent: {
          DEFAULT: "var(--color-accent)",
        }
      },
      borderRadius: {
        xl: "var(--radius-xl)",
      },
      fontFamily: {
        sans: ["Inter", "sans-serif"],
        heading: ["Outfit", "sans-serif"],
      }
    },
  },
  plugins: [],
}
