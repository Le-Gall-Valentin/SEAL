// API publique de la tranche start-direct-conversation (ADR 0012) : ouvrir une directe avec un ami (CNV-01, ADR 0041).
export type { IStartDirectApi, OpenedDirect } from './api/IStartDirectApi'
export { startDirectApi } from './api/startDirectApi'
export { StartDirectApiProvider } from './model/startDirectPort'
export { useStartDirect } from './model/useStartDirect'
