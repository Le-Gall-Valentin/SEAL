import { createPort } from '@/shared/lib'
import type { IUsernameAvailabilityApi } from '../api/IUsernameAvailabilityApi'

// ADR 0024, point 7 : port IUsernameAvailabilityApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IUsernameAvailabilityApi>('UsernameAvailabilityApiProvider')

export const UsernameAvailabilityApiProvider = port.Provider
export const useUsernameAvailabilityApi = port.usePort
