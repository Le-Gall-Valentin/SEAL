import { createPort } from '@/shared/lib'
import type { ILogoutApi } from '../api/ILogoutApi'

// ADR 0024, point 7 : port ILogoutApi, fourni par la racine de composition ou par une doublure dans les tests.
const port = createPort<ILogoutApi>('LogoutApiProvider')

export const LogoutApiProvider = port.Provider
export const useLogoutApi = port.usePort
