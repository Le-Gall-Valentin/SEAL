interface LockIconProps {
  readonly size: 13 | 14 | 18
  /** Avec un nom, le cadenas se lit (« Identifiant définitif ») ; sans nom, il est décoratif. */
  readonly label?: string | undefined
}

/** Cadenas de la maquette (Parametres-Ordinateur, Parametres-Telephone) : l'identifiant ne change pas. */
export function LockIcon({ size, label }: LockIconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={size === 18 ? 1.7 : 1.9}
      strokeLinecap="round"
      strokeLinejoin="round"
      {...(label === undefined ? { 'aria-hidden': true } : { role: 'img', 'aria-label': label })}
      className="flex-none"
    >
      <rect x="5" y="10.5" width="14" height="10" rx="2.5" />
      <path d="M8 10.5v-3a4 4 0 0 1 8 0v3" />
    </svg>
  )
}
