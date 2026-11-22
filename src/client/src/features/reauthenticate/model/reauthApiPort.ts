import { createPort } from '@/shared/lib'
import type { IReauthApi } from '../api/IReauthApi'

// ADR 0024, point 7 : port IReauthApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IReauthApi>('ReauthApiProvider')

export const ReauthApiProvider = port.Provider
export const useReauthApi = port.usePort
