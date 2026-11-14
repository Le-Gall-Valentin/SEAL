import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { readResetToken } from './resetLink'

/** Le lien lu, et un numéro qui change à chaque nouveau lien (sans jamais exposer le jeton). */
export interface ResetLinkRef {
  readonly token: string | null
  readonly linkId: number
}

/**
 * ADR 0022, point 2 : jeton lu dans le fragment de l'adresse du routeur, à l'ouverture comme pour un second lien
 * ouvert dans le même onglet, puis retiré de l'adresse et de l'état du routeur (navigate, en remplacement). Il ne vit
 * qu'en mémoire, le temps de l'écran ; le navigateur garde pourtant le lien dans son historique global (risque
 * résiduel de l'ADR 0022).
 */
export function useResetLink(): ResetLinkRef {
  const location = useLocation()
  const navigate = useNavigate()
  const [link, setLink] = useState(() => ({
    token: readResetToken(location.hash),
    linkId: 0,
    hash: location.hash,
  }))
  // Un nouveau fragment (second lien) : son jeton remplace le précédent ; un fragment retiré garde le jeton lu.
  if (location.hash !== link.hash) {
    const next = readResetToken(location.hash)
    setLink(
      next === null
        ? { ...link, hash: location.hash }
        : { token: next, linkId: link.linkId + 1, hash: location.hash },
    )
  }
  useEffect(() => {
    if (location.hash !== '') {
      void navigate({ pathname: location.pathname, search: location.search }, { replace: true })
    }
  }, [location.hash, location.pathname, location.search, navigate])
  return { token: link.token, linkId: link.linkId }
}
