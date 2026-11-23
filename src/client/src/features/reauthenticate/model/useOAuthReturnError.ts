import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { REAUTH_ERROR_PARAM, type ReauthReturnError, reauthReturnErrorOf } from './oauthReturn'

/**
 * AUTH-55, ADR 0034 : refus d'une ré-authentification OAuth, lu une fois dans `?erreur=`, puis retiré de l'adresse pour
 * qu'un rechargement ne le montre pas de nouveau ; l'alerte reste tant que la page est ouverte. Lu par Paramètres et,
 * tant que l'appareil n'a pas de clés, par la restauration, que `/parametres` affiche alors (ADR 0036, point 4).
 */
export function useOAuthReturnError(): ReauthReturnError | null {
  const [params, setParams] = useSearchParams()
  const [error] = useState(() => reauthReturnErrorOf(`?${params.toString()}`))
  const present = params.has(REAUTH_ERROR_PARAM)
  useEffect(() => {
    if (present) {
      setParams(
        (current) => {
          const next = new URLSearchParams(current)
          next.delete(REAUTH_ERROR_PARAM)
          return next
        },
        { replace: true },
      )
    }
  }, [present, setParams])
  return error
}
