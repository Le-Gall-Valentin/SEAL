import { useMessages } from '@/shared/i18n'
import { brandMessages } from './brandMessages'
import { SEAL_PATH } from './sealPath'

const SIZES = { 8: 'size-2', 10: 'size-2.5', 18: 'size-4.5' } as const

interface SceauProps {
  /** États du composant Sceau de la maquette ; seul « vérifié » sert avant la messagerie (lot 4). */
  readonly variant: 'verified'
  readonly size: keyof typeof SIZES
  /**
   * ADR 0024, point 10 : sceau décoratif (logo, identifiant libre), masqué aux lecteurs d'écran ; « Signature vérifiée »
   * reste réservé aux signatures.
   */
  readonly decorative?: boolean
}

/** Sceau de vérification, aux couleurs du thème. */
export function Sceau({ size, decorative = false }: SceauProps) {
  const t = useMessages(brandMessages)
  return (
    <svg
      viewBox="0 0 16 16"
      {...(decorative ? { 'aria-hidden': true } : { role: 'img', 'aria-label': t('sealVerified') })}
      className={`block flex-none ${SIZES[size]}`}
    >
      <path d={SEAL_PATH} className="fill-accent" />
      <circle
        cx="8"
        cy="8"
        r="4.9"
        fill="none"
        strokeOpacity="0.35"
        strokeWidth="0.8"
        className="stroke-on-accent"
      />
      <path
        d="M5.5 8.1L7.2 9.8L10.6 6.3"
        fill="none"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="stroke-on-accent"
      />
    </svg>
  )
}
