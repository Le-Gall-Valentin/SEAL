import { useMessages } from '@/shared/i18n'
import type { FormFailure } from '@/shared/lib'
import { Alert, FormAlert } from '@/shared/ui'
import { answerFriendRequestMessages } from '../i18n'

interface AnswerAlertsProps {
  readonly gone: boolean
  readonly failure: FormFailure | null
}

/** ADR 0038 (point 2) : invitation close ailleurs, ou échec réseau ou inattendu d'une réponse. */
export function AnswerAlerts({ gone, failure }: AnswerAlertsProps) {
  const t = useMessages(answerFriendRequestMessages)
  return gone ? <Alert variant="error">{t('gone')}</Alert> : <FormAlert failure={failure} />
}
