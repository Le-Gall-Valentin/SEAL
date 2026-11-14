import { createContext, type ReactNode, useContext } from 'react'

interface PortProviderProps<Port> {
  readonly api: Port
  readonly children: ReactNode
}

/**
 * ADR 0024, point 7 : le port d'une tranche, injecté par un fournisseur React. La racine de composition fournit
 * l'adaptateur, les tests une doublure ; un crochet appelé sans fournisseur échoue en nommant le fournisseur manquant.
 */
export function createPort<Port>(providerName: string) {
  const PortContext = createContext<Port | null>(null)

  function Provider({ api, children }: PortProviderProps<Port>) {
    return <PortContext value={api}>{children}</PortContext>
  }

  function usePort(): Port {
    const api = useContext(PortContext)
    if (api === null) {
      throw new Error(`Port absent : appelez ce crochet sous un ${providerName}`)
    }
    return api
  }

  return { Provider, usePort }
}
