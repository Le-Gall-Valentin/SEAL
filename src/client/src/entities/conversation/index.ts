// API publique de la tranche conversation (ADR 0012) : lecture des conversations (14.5, ADR 0039, ADR 0041).
export type {
  Conversation,
  ConversationType,
  IConversationsApi,
  Member,
  MemberRole,
} from './api/IConversationsApi'
export {
  CONVERSATION_RESPONSE,
  conversationOf,
  conversationsApi,
  GROUP_NAME_MAX_LENGTH,
  GROUP_NAME_RAW_MAX_LENGTH,
  MAX_MEMBERS,
} from './api/conversationsApi'
export { ConversationsApiProvider } from './model/conversationsPort'
export {
  canAdd,
  canLeave,
  canRemove,
  canRename,
  counterpartOf,
  isAdmin,
  matchConversation,
  seatsLeft,
  sortConversations,
  titleOf,
} from './model/conversations'
export {
  invalidateConversations,
  storeConversation,
  useConversation,
  useConversations,
  useConversationsRefresh,
} from './model/useConversations'
export { ConversationAvatar } from './ui/ConversationAvatar'
