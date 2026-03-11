import { defineConfig, Plugin } from 'vite'

const fixForFileProtocol: Plugin = {
  name: 'fix-for-file-protocol',
  transformIndexHtml(html: string) {
    return html
      .replace(/ crossorigin/g, '')
      .replace(/ type="module"/g, '')
  }
}

export default defineConfig({
  base: './',
  plugins: [fixForFileProtocol],
  build: {
    rollupOptions: {
      output: {
        format: 'iife'
      }
    }
  }
})
