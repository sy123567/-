import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#16223A",
        "ink-soft": "#5A6785",
        paper: "#F5F7FA",
        surface: "#FFFFFF",
        coral: "#FF5B4C",
        "coral-deep": "#E8412F",
        mint: "#17C3A2",
        sky: "#2F9BFF",
        sun: "#FFC53D",
        "risk-low": "#17C3A2",
        "risk-med": "#FFC53D",
        "risk-high": "#FF922B",
        "risk-critical": "#E03131",
      },
      fontFamily: {
        display: ["Space Grotesk", "Noto Sans SC", "sans-serif"],
        sans: ["Inter", "Noto Sans SC", "sans-serif"],
        mono: ["Space Mono", "monospace"],
      },
      boxShadow: {
        soft: "0 16px 40px rgba(22, 34, 58, 0.08)",
        coral: "0 14px 28px rgba(255, 91, 76, 0.24)",
      },
      borderRadius: { card: "20px" },
    },
  },
  plugins: [],
} satisfies Config;
