import type { ReactNode } from 'react'

/** Libellé d'un champ des formulaires de la maquette. */
export function FieldLabel({
  htmlFor,
  children,
}: {
  readonly htmlFor: string
  readonly children: ReactNode
}) {
  return (
    <label htmlFor={htmlFor} className="text-[13px] font-semibold text-ink-2">
      {children}
    </label>
  )
}

/** Texte d'erreur sous un champ, comme « Déjà pris. » d'Identifiant-OAuth (ADR 0024, point 9). */
export function ErrorText({
  id,
  children,
}: {
  readonly id?: string
  readonly children: ReactNode
}) {
  return (
    <span id={id} className="text-[13px] leading-[1.35] font-semibold text-danger">
      {children}
    </span>
  )
}

/** Région annoncée qui porte l'erreur d'un champ ; présente même vide, pour que l'erreur soit annoncée. */
export function FieldError({ id, error }: { readonly id: string; readonly error: string | null }) {
  return (
    <div aria-live="polite" className="empty:hidden">
      {error !== null && <ErrorText id={id}>{error}</ErrorText>}
    </div>
  )
}
