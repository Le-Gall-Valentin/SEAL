import { createPort } from '@/shared/lib'
import type { IConversationsApi } from '../api/IConversationsApi'

// ADR 0024, point 7 : port IConversationsApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IConversationsApi>('ConversationsApiProvider')

export const ConversationsApiProvider = port.Provider
export const useConversationsApi = port.usePort
