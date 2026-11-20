import { type ReactNode, useEffect, useRef } from 'react'

interface DialogProps {
  /** Identifiant du titre de la fenêtre. */
  readonly labelledBy: string
  readonly describedBy?: string | undefined
  /** Change à chaque étape : le focus revient à la fenêtre, qui annonce son titre, si l'étape ne le place pas. */
  readonly stepKey?: string | number | undefined
  readonly children: ReactNode
}

/**
 * Fenêtre de dialogue de la maquette (Dialogue-Cle-Recuperation : 500 px, rayon 18, fond --raised, ombre --shadow-3,
 * voile --overlay). Elle est seule sur le fond : rien d'autre n'est focalisable, et Échap ne la ferme pas ; seuls ses
 * boutons la quittent (ADR 0029). Le focus s'y place à l'ouverture et à chaque étape, sauf si son contenu l'a déjà
 * pris.
 */
export function Dialog({ labelledBy, describedBy, stepKey, children }: DialogProps) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    // Un contenu qui place lui-même le focus (premier champ d'une étape) le garde ; sinon, le bouton qui a changé
    // d'étape a disparu et le focus serait sur body.
    if (ref.current !== null && !ref.current.contains(document.activeElement)) {
      ref.current.focus()
    }
  }, [stepKey])
  return (
    <div className="flex min-h-screen items-start justify-center bg-overlay px-4 py-10 font-sans text-ink">
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        aria-describedby={describedBy}
        tabIndex={-1}
        className="flex w-full max-w-[500px] flex-col overflow-hidden rounded-[18px] bg-raised shadow-dialog outline-none"
      >
        {children}
      </div>
    </div>
  )
}
