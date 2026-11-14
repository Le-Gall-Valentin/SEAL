import { useMessages } from '@/shared/i18n'
import { Alert } from './Alert'
import { formMessages } from './formMessages'

interface FailureAlertProps {
  readonly failure: { readonly kind: 'network' | 'unexpected' }
}

/** ADR 0024, point 8 : serveur injoignable ou erreur inattendue, sur tout formulaire. */
export function FailureAlert({ failure }: FailureAlertProps) {
  const t = useMessages(formMessages)
  return <Alert variant="error">{t(failure.kind)}</Alert>
}
