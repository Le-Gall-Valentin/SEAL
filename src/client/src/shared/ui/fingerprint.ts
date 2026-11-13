/** KEY-52 : une case du motif, d'après le composant Empreinte de la maquette. */
export interface FingerprintCell {
  readonly color: 'a' | 'b' | 'c'
  readonly shape: 'top-left' | 'top-right' | 'bottom-right' | 'bottom-left' | 'dot'
}

const CORNERS = ['top-left', 'top-right', 'bottom-right', 'bottom-left'] as const

/** 64 chiffres hexadécimaux en majuscules, complétés par des zéros. */
export function normalizeFingerprint(raw: string): string {
  return raw
    .toUpperCase()
    .replace(/[^0-9A-F]/g, '')
    .padEnd(64, '0')
    .slice(0, 64)
}

/** Chaque case est le XOR des chiffres de rang i modulo 16 : 2 bits de genre, 2 bits d'orientation. */
export function fingerprintCells(raw: string): FingerprintCell[] {
  const hex = normalizeFingerprint(raw)
  const digits = new Array<number>(16).fill(0)
  for (let i = 0; i < hex.length; i++) {
    digits[i % 16] = (digits[i % 16] ?? 0) ^ Number.parseInt(hex.charAt(i), 16)
  }
  return digits.map((digit): FingerprintCell => {
    const rotation = digit & 3
    switch (digit >> 2) {
      case 0:
        return { color: 'a', shape: CORNERS[rotation] ?? 'top-left' }
      case 1:
        return { color: 'b', shape: CORNERS[rotation] ?? 'top-left' }
      case 2:
        return { color: 'a', shape: 'dot' }
      default:
        return { color: 'c', shape: CORNERS[(rotation + 2) % 4] ?? 'top-left' }
    }
  })
}

/** Début et fin de l'empreinte, pour son libellé. */
export function fingerprintLabel(raw: string): string {
  const hex = normalizeFingerprint(raw)
  return `${hex.slice(0, 4)}…${hex.slice(60)}`
}
