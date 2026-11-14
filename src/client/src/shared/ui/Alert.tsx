import type { ReactNode } from 'react'

interface AlertProps {
  /** « error » : refus simple (Connexion-Tablette) ; « rate-limited » : limite atteinte (Connexion-Telephone). */
  readonly variant: 'error' | 'rate-limited'
  readonly title?: string
  readonly children: ReactNode
}

/** Alerte d'erreur d'un formulaire, annoncée dès son affichage. */
export function Alert({ variant, title, children }: AlertProps) {
  return (
    <div
      role="alert"
      className="flex gap-2.5 rounded-[14px] border border-danger-line bg-danger-soft px-3.5 py-3 text-[13px] leading-[1.4] data-[variant=rate-limited]:gap-3 data-[variant=rate-limited]:leading-[1.35]"
      data-variant={variant}
    >
      <svg
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
        className="mt-px flex-none text-danger"
      >
        <circle cx="12" cy="12" r="8.5" />
        <path d={variant === 'rate-limited' ? 'M12 7.5V12l3 2' : 'M12 7.5v5.2M12 16.2h.01'} />
      </svg>
      <div className="flex flex-col gap-1">
        {title !== undefined && <span className="font-semibold text-danger">{title}</span>}
        <span className="text-ink-2">{children}</span>
      </div>
    </div>
  )
}
