import { describe, expect, it } from 'vitest'

// ADR 0027, point 6 : les tests *.browser.test.ts tournent dans Chromium, avec IndexedDB et WebCrypto réels.
describe('ADR 0027 : environnement des tests du trousseau', () => {
  it('KEY-40 : IndexedDB et WebCrypto du navigateur sont disponibles', () => {
    expect(typeof indexedDB.open).toBe('function')
    expect(typeof crypto.subtle.generateKey).toBe('function')
    expect(navigator.userAgent).toContain('Chrome')
  })
})
