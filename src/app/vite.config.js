import {defineConfig} from 'vite'
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [tailwindcss(), vue()],
  server: {
    host: 'localhost',
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080/',
        changeOrigin: false,
        xfwd: true,
        secure: false
      }
    }
  }
})
