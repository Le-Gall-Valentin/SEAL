import { createPort } from '@/shared/lib'
import type { ISessionApi } from '../api/ISessionApi'

// ADR 0024, point 7 : port ISessionApi, fourni par la racine de composition ou par une doublure dans les tests.
const port = createPort<ISessionApi>('SessionApiProvider')

export const SessionApiProvider = port.Provider
export const useSessionApi = port.usePort
