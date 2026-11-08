import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Certificat local de scripts/gen-certs.sh : le serveur de développement sert lui aussi en HTTPS,
// condition des cookies __Host- (AUTH-51). Sans certificat, il retombe sur HTTP.
const certificate = fileURLToPath(new URL('../certs/localhost.pem', import.meta.url))
const privateKey = fileURLToPath(new URL('../certs/localhost-key.pem', import.meta.url))
const https =
  existsSync(certificate) && existsSync(privateKey)
    ? { cert: readFileSync(certificate), key: readFileSync(privateKey) }
    : undefined

// Spring Boot lancé par ./mvnw spring-boot:test-run depuis src/server.
const springBoot = 'https://localhost:8443'

export default defineConfig({
  // ADR 0016 : Tailwind produit une feuille statique au build, compatible avec style-src 'self' (TRN-21).
  plugins: [react(), tailwindcss()],
  // ADR 0012, point 5 : l'alias @/ vient des paths de tsconfig.app.json.
  resolve: { tsconfigPaths: true },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: false,
    // ADR 0012, point 11 : aucun fichier intégré en data: ; une police ainsi intégrée serait bloquée par font-src 'self'.
    assetsInlineLimit: 0,
  },
  server: {
    port: 5173,
    strictPort: true,
    https,
    // TRN-23 : le navigateur ne voit qu'une origine ; Vite relaie l'API et la WebSocket vers Spring.
    // secure: false ne vaut que pour ce relais sur la boucle locale, en développement : Node ignore
    // l'autorité mkcert installée dans le système. La CSP stricte s'applique au build de production.
    proxy: {
      '/api': { target: springBoot, secure: false },
      '/ws': { target: springBoot, ws: true, secure: false },
    },
  },
})
