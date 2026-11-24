import { useState } from 'react'
import type { AcceptResult } from '@/entities/friend'

type AcceptIssue = Exclude<AcceptResult, { readonly kind: 'accepted' }>

interface IncomingAcceptanceOptions {
  /** La page fournit l'acceptation et en rend l'issue. */
  readonly accept: (requestId: string) => Promise<AcceptResult>
  /** Acceptée : le formulaire efface le bloc 409 et vide le champ ; la feuille se ferme. */
  readonly onAccepted: () => void
  /** Close ailleurs : le bloc 409 part ; il n'y a plus rien à accepter. */
  readonly onGone: () => void
}

/**
 * FRD-01, ADR 0038 (points 8 et 9) : « Accepter l’invitation » après un 409, une acceptation à la fois. Un échec réseau
 * ou 429 garde le bloc 409, pour réessayer.
 */
export function useIncomingAcceptance({ accept, onAccepted, onGone }: IncomingAcceptanceOptions) {
  const [accepting, setAccepting] = useState(false)
  const [issue, setIssue] = useState<AcceptIssue | null>(null)
  const run = async (requestId: string) => {
    if (accepting) {
      return
    }
    setAccepting(true)
    setIssue(null)
    const result = await accept(requestId)
    setAccepting(false)
    if (result.kind === 'accepted') {
      onAccepted()
      return
    }
    if (result.kind === 'gone') {
      onGone()
    }
    setIssue(result)
  }
  return {
    accepting,
    issue,
    accept: (requestId: string) => {
      void run(requestId)
    },
    clearIssue: () => {
      setIssue(null)
    },
  }
}
