import { createPort } from '@/shared/lib'
import type { ICreateGroupApi } from '../api/ICreateGroupApi'

// ADR 0024, point 7 : port ICreateGroupApi, fourni par la racine de composition ou par une doublure.
const port = createPort<ICreateGroupApi>('CreateGroupApiProvider')

export const CreateGroupApiProvider = port.Provider
export const useCreateGroupApi = port.usePort
