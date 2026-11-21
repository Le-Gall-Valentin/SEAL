import { createPort } from '@/shared/lib'
import type { IOAuthProvidersApi } from '../api/IOAuthProvidersApi'

// ADR 0024, point 7 : port IOAuthProvidersApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IOAuthProvidersApi>('OAuthProvidersApiProvider')

export const OAuthProvidersApiProvider = port.Provider
export const useOAuthProvidersApi = port.usePort
