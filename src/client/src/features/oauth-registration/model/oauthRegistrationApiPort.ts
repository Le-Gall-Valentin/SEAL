import { createPort } from '@/shared/lib'
import type { IOAuthRegistrationApi } from '../api/IOAuthRegistrationApi'

// ADR 0024, point 7 : port IOAuthRegistrationApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IOAuthRegistrationApi>('OAuthRegistrationApiProvider')

export const OAuthRegistrationApiProvider = port.Provider
export const useOAuthRegistrationApi = port.usePort
