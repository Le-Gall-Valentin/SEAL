import type { ReactNode } from 'react'

interface NoticeProps {
  /** « mail » : lien envoyé (Mot-de-passe-oublie) ; « success » : action réussie. */
  readonly variant: 'mail' | 'success'
  readonly title?: string
  readonly children: ReactNode
}

/** Encart de confirmation de Mot-de-passe-oublie ; à placer dans une région role="status". */
export function Notice({ variant, title, children }: NoticeProps) {
  return (
    <div className="flex gap-3 rounded-[14px] bg-accent-soft px-3.5 py-3 text-[13px] leading-[1.4] inset-ring inset-ring-accent-line">
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
        className="mt-px flex-none text-accent-text"
      >
        {variant === 'mail' ? (
          <>
            <rect x="3.5" y="5.5" width="17" height="13" rx="2.5" />
            <path d="M4.5 7.5l7.5 5.5 7.5-5.5" />
          </>
        ) : (
          <path d="M5 12.5l4.5 4.5L19 7.5" />
        )}
      </svg>
      <div className="flex flex-col gap-1">
        {title !== undefined && <span className="font-semibold text-accent-text">{title}</span>}
        <span className="text-ink-2">{children}</span>
      </div>
    </div>
  )
}
