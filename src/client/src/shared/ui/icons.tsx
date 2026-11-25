import type { ReactNode } from 'react'

/** Taille et trait d'un pictogramme : chaque écran de la maquette les règle, le tracé reste le même. */
interface IconProps {
  readonly size?: number
  readonly strokeWidth?: number
  readonly className?: string
}

function StrokeIcon({
  size,
  strokeWidth,
  className,
  children,
}: Required<Pick<IconProps, 'size' | 'strokeWidth'>> & {
  readonly className: string | undefined
  readonly children: ReactNode
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      aria-hidden="true"
      className={className}
    >
      {children}
    </svg>
  )
}

/** « + » de « Nouvelle conversation » et des options du sélecteur (Conv-Ordinateur, Nouvelle-Conversation, Etats). */
export function PlusIcon({ size = 18, strokeWidth = 1.8, className }: IconProps) {
  return (
    <StrokeIcon size={size} strokeWidth={strokeWidth} className={className}>
      <path d="M12 5v14M5 12h14" />
    </StrokeIcon>
  )
}

/** Loupe des champs de recherche (Conv-Ordinateur, Nouvelle-Conversation, Amis-Ordinateur). */
export function SearchIcon({ size = 18, strokeWidth = 1.7, className }: IconProps) {
  return (
    <StrokeIcon size={size} strokeWidth={strokeWidth} className={className}>
      <circle cx="11" cy="11" r="6.5" />
      <path d="M20 20l-4.3-4.3" />
    </StrokeIcon>
  )
}

/** Croix de « Fermer », « Retirer » et « Effacer la recherche » (Conv-Ordinateur, Nouvelle-Conversation, Etats). */
export function CrossIcon({ size = 20, strokeWidth = 1.7, className }: IconProps) {
  return (
    <StrokeIcon size={size} strokeWidth={strokeWidth} className={className}>
      <path d="M6.5 6.5l11 11M17.5 6.5l-11 11" />
    </StrokeIcon>
  )
}
