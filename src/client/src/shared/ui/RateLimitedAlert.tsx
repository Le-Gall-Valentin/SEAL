import { useMessages } from '@/shared/i18n'
import { Alert } from './Alert'
import { formMessages } from './formMessages'

interface RateLimitedAlertProps {
  /** Délai de Retry-After, arrondi à la minute supérieure (ADR 0015). */
  readonly minutes: number
  /** Phrase ajoutée après le délai, propre à l'écran. */
  readonly hint?: string | undefined
}

/** SEC-10 : « Trop de tentatives », puis « Réessayez dans N minutes. » (Connexion-Telephone, Etats). */
export function RateLimitedAlert({ minutes, hint }: RateLimitedAlertProps) {
  const t = useMessages(formMessages)
  return (
    <Alert variant="rate-limited" title={t('rateLimitedTitle')}>
      {t('retryIn')}{' '}
      <span className="font-semibold text-ink tabular-nums">
        {minutes === 1 ? t('oneMinute') : t('minutes', { count: minutes })}
      </span>
      {t('period')}
      {hint !== undefined && ` ${hint}`}
    </Alert>
  )
}
