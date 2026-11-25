import { createPort } from '@/shared/lib'
import type { IRenameGroupApi } from '../api/IRenameGroupApi'

// ADR 0024, point 7 : port IRenameGroupApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IRenameGroupApi>('RenameGroupApiProvider')

export const RenameGroupApiProvider = port.Provider
export const useRenameGroupApi = port.usePort
