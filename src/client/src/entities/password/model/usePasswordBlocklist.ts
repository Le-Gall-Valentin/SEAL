import { useQuery } from '@tanstack/react-query'
import { usePasswordBlocklistApi } from './passwordBlocklistContext'
import { foldPassword } from './passwordPolicy'

/** Liste de blocage repliée, ou null tant qu'elle n'est pas chargée, ou si son chargement a échoué (ADR 0024). */
export function usePasswordBlocklist(): ReadonlySet<string> | null {
  const api = usePasswordBlocklistApi()
  const query = useQuery({
    queryKey: ['password-blocklist'],
    queryFn: async (): Promise<ReadonlySet<string>> =>
      new Set((await api.load()).map(foldPassword)),
    staleTime: Infinity,
  })
  return query.data ?? null
}
