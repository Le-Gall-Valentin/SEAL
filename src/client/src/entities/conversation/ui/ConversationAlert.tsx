import { useMessages } from '@/shared/i18n'
import { isFormFailure } from '@/shared/lib'
import { Alert, FormAlert } from '@/shared/ui'
import { conversationMessages } from '../i18n'
import type { ConversationFailure } from '../model/conversationFailure'

const TEXT = {
  'not-friends': 'notFriends',
  'group-full': 'groupFull',
  forbidden: 'forbidden',
  gone: 'gone',
} as const

/**
 * Refus d'une action sur une conversation (ADR 0041, point 4), annoncé en alerte ; un nom refusé s'affiche sous son
 * champ, pas ici.
 */
export function ConversationAlert({ failure }: { readonly failure: ConversationFailure | null }) {
  const t = useMessages(conversationMessages)
  if (failure === null || failure.kind === 'invalid-name') {
    return null
  }
  if (isFormFailure(failure)) {
    return <FormAlert failure={failure} />
  }
  return <Alert variant="error">{t(TEXT[failure.kind])}</Alert>
}
