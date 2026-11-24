import { createPort } from '@/shared/lib'
import type { IAnswerFriendRequestApi } from '../api/IAnswerFriendRequestApi'

// ADR 0024, point 7 : port IAnswerFriendRequestApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IAnswerFriendRequestApi>('AnswerFriendRequestApiProvider')

export const AnswerFriendRequestApiProvider = port.Provider
export const useAnswerFriendRequestApi = port.usePort
