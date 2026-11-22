import { useEffect } from 'react'
import { useLoginMethods } from '@/entities/account'
import {
  type OAuthProviderId,
  useOAuthProviders,
  useRetryFailedOAuthProviders,
} from '@/entities/oauth-provider'

/** Ce que le dialogue peut proposer : lu (ou non), mot de passe, fournisseurs liés et configurés. */
export interface ReauthMethods {
  /** Faux tant que les méthodes du compte ne sont pas lues : rien n'est proposé. */
  readonly loaded: boolean
  /** La lecture a échoué et rien n'est encore lu. */
  readonly failed: boolean
  readonly passwordSet: boolean
  readonly providers: readonly OAuthProviderId[]
}

/**
 * AUTH-55, ADR 0035 : méthodes de ré-authentification du compte, liées et configurées. Une lecture ratée (sans nouvel
 * essai automatique) est refaite à chaque ouverture du dialogue : il a toujours une issue.
 */
export function useReauthMethods(open: boolean): ReauthMethods {
  const methods = useLoginMethods()
  const configured = useOAuthProviders()
  const retryProviders = useRetryFailedOAuthProviders()
  const { isError: methodsFailed, refetch: refetchMethods } = methods
  useEffect(() => {
    if (open) {
      if (methodsFailed) {
        void refetchMethods()
      }
      void retryProviders()
    }
  }, [open, methodsFailed, refetchMethods, retryProviders])
  return {
    loaded: methods.data !== undefined,
    failed: methods.isError && methods.data === undefined,
    passwordSet: methods.data?.passwordSet === true,
    providers: (methods.data?.providers ?? []).filter((provider) => configured.includes(provider)),
  }
}
