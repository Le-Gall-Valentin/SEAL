import { FailureAlert } from './FailureAlert'
import { RateLimitedAlert } from './RateLimitedAlert'

/** Échecs communs aux formulaires : limite atteinte, serveur injoignable, erreur inattendue. */
export type FormFailure =
  | { readonly kind: 'rate-limited'; readonly minutes: number }
  | { readonly kind: 'network' }
  | { readonly kind: 'unexpected' }

interface FormAlertProps {
  readonly failure: FormFailure | null
  /** Phrase ajoutée après le délai d'un 429, propre à l'écran. */
  readonly rateLimitedHint?: string | undefined
}

/** L'échec relève de l'alerte du formulaire, et non d'un champ. */
export function isFormFailure(failure: { readonly kind: string }): failure is FormFailure {
  return (
    failure.kind === 'rate-limited' || failure.kind === 'network' || failure.kind === 'unexpected'
  )
}

/** SEC-10, ADR 0024 (point 8) : l'alerte d'un formulaire, ou rien. */
export function FormAlert({ failure, rateLimitedHint }: FormAlertProps) {
  if (failure === null) {
    return null
  }
  return failure.kind === 'rate-limited' ? (
    <RateLimitedAlert minutes={failure.minutes} hint={rateLimitedHint} />
  ) : (
    <FailureAlert failure={failure} />
  )
}
