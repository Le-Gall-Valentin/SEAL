import { createPort } from '@/shared/lib'
import type { IRemoveMemberApi } from '../api/IRemoveMemberApi'

// ADR 0024, point 7 : port IRemoveMemberApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IRemoveMemberApi>('RemoveMemberApiProvider')

export const RemoveMemberApiProvider = port.Provider
export const useRemoveMemberApi = port.usePort
