import { useMessages } from '@/shared/i18n'
import { keyMessages } from '../i18n'
import { type FingerprintCell, fingerprintCells, fingerprintLabel } from '../model/fingerprint'

// Taille 20 du panneau de marque : marge max(2, 10 %), rayon 22 %, arrondis comme dans la maquette.
const SIZES = { 20: 'size-5 p-0.5 rounded-sm' } as const

const COLORS: Record<FingerprintCell['color'], string> = {
  a: 'bg-fp-a',
  b: 'bg-fp-b',
  c: 'bg-fp-c',
}

const SHAPES: Record<FingerprintCell['shape'], string> = {
  'top-left': 'rounded-tl-[100%]',
  'top-right': 'rounded-tr-[100%]',
  'bottom-right': 'rounded-br-[100%]',
  'bottom-left': 'rounded-bl-[100%]',
  dot: 'rounded-[50%] scale-60',
}

interface EmpreinteProps {
  /** Empreinte en hexadécimal (KEY-52). */
  readonly fingerprint: string
  readonly size: keyof typeof SIZES
}

/** KEY-52 : motif 4 × 4 de l'empreinte d'une clé (composant Empreinte de la maquette, sans les blocs de texte). */
export function Empreinte({ fingerprint, size }: EmpreinteProps) {
  const t = useMessages(keyMessages)
  return (
    <div
      role="img"
      aria-label={t('fingerprint', { fingerprint: fingerprintLabel(fingerprint) })}
      className={`grid flex-none grid-cols-4 grid-rows-4 bg-fp-bg ${SIZES[size]}`}
    >
      {fingerprintCells(fingerprint).map((cell, index) => (
        // Les 16 cases sont fixes et ordonnées : leur rang est leur identité.
        <div key={index} className={`${COLORS[cell.color]} ${SHAPES[cell.shape]}`} />
      ))}
    </div>
  )
}
