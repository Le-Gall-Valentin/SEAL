import { useCurrentAccount } from '@/entities/session'
import { ConversationsScreen } from './ConversationsScreen'

/** CNV-04 : écran Conversations du compte de la session ; remplace l'accueil provisoire du lot 1 (ADR 0041). */
export function ConversationsPage() {
  const account = useCurrentAccount().data
  // ActiveRoute ne rend cette page qu'avec un compte actif ; rien à montrer sans lui.
  return account == null ? null : <ConversationsScreen me={account.id} />
}
