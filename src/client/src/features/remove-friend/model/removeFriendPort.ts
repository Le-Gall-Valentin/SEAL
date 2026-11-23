import { createPort } from '@/shared/lib'
import type { IRemoveFriendApi } from '../api/IRemoveFriendApi'

// ADR 0024, point 7 : port IRemoveFriendApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IRemoveFriendApi>('RemoveFriendApiProvider')

export const RemoveFriendApiProvider = port.Provider
export const useRemoveFriendApi = port.usePort
