import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Relative assets let the static bundle work from a mounted sub-path (and make
// accidental local file previews fail gracefully instead of loading no assets).
export default defineConfig({ base: "./", plugins: [react()] });
