import { useState } from 'react'
import type { KeyOwner } from '@/entities/keyring'
import { KeySetupDialog } from '@/features/key-setup'
import { ReauthDialog } from '@/features/reauthenticate'

interface IdentityResetDialogProps {
  readonly owner: KeyOwner
  /** La nouvelle identité est en place, ou un autre onglet a repris la main : la zone du compte relit les clés. */
  readonly onDone: () => void
}

/**
 * KEY-34, ADR 0036 (points 3 et 4) : Dialogue-Cle-Recuperation en variante reset, seul sur le fond comme à la première
 * connexion ; sur 403 REAUTH_REQUIRED, « Confirmez votre identité » par-dessus, puis la relance avec la même nouvelle
 * clé (AUTH-55, ADR 0035).
 */
export function IdentityResetDialog({ owner, onDone }: IdentityResetDialogProps) {
  const [pendingRetry, setPendingRetry] = useState<{ readonly retry: () => void } | null>(null)
  return (
    <>
      <KeySetupDialog
        owner={owner}
        variant="reset"
        onDone={onDone}
        onReauthRequired={(retry) => {
          setPendingRetry({ retry })
        }}
      />
      <ReauthDialog
        open={pendingRetry !== null}
        onReauthenticated={() => {
          pendingRetry?.retry()
          setPendingRetry(null)
        }}
        onCancel={() => {
          setPendingRetry(null)
        }}
      />
    </>
  )
}
