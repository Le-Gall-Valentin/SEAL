import { createPort } from '@/shared/lib'
import type { IProfileApi } from '../api/IProfileApi'

// ADR 0024, point 7 : port IProfileApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IProfileApi>('ProfileApiProvider')

export const ProfileApiProvider = port.Provider
export const useProfileApi = port.usePort
