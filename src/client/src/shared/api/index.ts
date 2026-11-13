// API publique du segment api de shared (ADR 0012) : seul un segment api/ de tranche l'atteint.
export { reloadCsrf, request, requestNoContent } from './http'
export type { RequestOptions } from './http'
