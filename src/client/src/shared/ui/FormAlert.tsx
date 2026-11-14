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
