import { createPort } from '@/shared/lib'
import type { IAuthorityApi } from '../api/IAuthorityApi'

// ADR 0024, point 7 : port IAuthorityApi, fourni par la racine de composition ou par une doublure dans les tests.
const port = createPort<IAuthorityApi>('AuthorityApiProvider')

export const AuthorityApiProvider = port.Provider
export const useAuthorityApi = port.usePort
