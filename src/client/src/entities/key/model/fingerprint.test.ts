import { describe, expect, it } from 'vitest'
import { fingerprintCells, fingerprintLabel, normalizeFingerprint } from './fingerprint'

// Empreinte du panneau de marque (Connexion-Ordinateur) ; motif attendu recalculé hors du client.
const PANEL = '29E9C1694564624662CCEA21BAE28389594171A501E7BE963EE4D56DFE10C210'

describe('KEY-52 : motif de l’empreinte, comme le composant Empreinte de la maquette', () => {
  it('KEY-52 : 16 cases, couleur et forme tirées du XOR des chiffres de rang i modulo 16', () => {
    expect(fingerprintCells(PANEL)).toEqual([
      { color: 'a', shape: 'bottom-right' },
      { color: 'c', shape: 'bottom-right' },
      { color: 'a', shape: 'dot' },
      { color: 'a', shape: 'top-left' },
      { color: 'a', shape: 'dot' },
      { color: 'c', shape: 'top-right' },
      { color: 'a', shape: 'dot' },
      { color: 'a', shape: 'top-left' },
      { color: 'a', shape: 'top-left' },
      { color: 'a', shape: 'top-left' },
      { color: 'b', shape: 'bottom-left' },
      { color: 'a', shape: 'top-right' },
      { color: 'a', shape: 'dot' },
      { color: 'c', shape: 'bottom-left' },
      { color: 'b', shape: 'top-left' },
      { color: 'a', shape: 'dot' },
    ])
  })

  it('KEY-52 : l’empreinte est lue en majuscules, sans autre caractère, sur 64 chiffres', () => {
    expect(normalizeFingerprint('29e9 c169-…')).toBe('29E9C169'.padEnd(64, '0'))
    expect(normalizeFingerprint(`${PANEL}FFFF`)).toBe(PANEL)
  })

  it('KEY-52 : le libellé cite les 4 premiers et les 4 derniers chiffres', () => {
    expect(fingerprintLabel(PANEL)).toBe('29E9…C210')
  })
})
