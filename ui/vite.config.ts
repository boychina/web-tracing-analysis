import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";

export default defineConfig({
  plugins: [react()],
  css: {
    preprocessorOptions: {
      less: {
        javascriptEnabled: true
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      "/login": "http://localhost:17001",
      "/user": "http://localhost:17001",
      "/application": "http://localhost:17001",
      "/webTrack": "http://localhost:17001",
      "/getMenuList": "http://localhost:17001",
      "/getMenuConfig": "http://localhost:17001"
    }
  }
});
