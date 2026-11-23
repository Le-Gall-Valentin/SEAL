import { createPort } from '@/shared/lib'
import type { ISendFriendRequestApi } from '../api/ISendFriendRequestApi'

// ADR 0024, point 7 : port ISendFriendRequestApi, fourni par la racine de composition ou par une doublure.
const port = createPort<ISendFriendRequestApi>('SendFriendRequestApiProvider')

export const SendFriendRequestApiProvider = port.Provider
export const useSendFriendRequestApi = port.usePort
