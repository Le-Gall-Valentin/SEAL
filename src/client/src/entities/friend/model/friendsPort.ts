import { createPort } from '@/shared/lib'
import type { IFriendsApi } from '../api/IFriendsApi'

// ADR 0024, point 7 : port IFriendsApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IFriendsApi>('FriendsApiProvider')

export const FriendsApiProvider = port.Provider
export const useFriendsApi = port.usePort
