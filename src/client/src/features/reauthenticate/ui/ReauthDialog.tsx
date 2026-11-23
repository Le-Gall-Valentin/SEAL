import { useId } from 'react'
import { OrSeparator, ProviderLinks } from '@/entities/oauth-provider'
import { PASSWORD_INPUT_MAX_LENGTH } from '@/entities/password'
import { useMessages } from '@/shared/i18n'
import {
  Alert,
  DialogBody,
  DialogFooter,
  FailureAlert,
  FormAlert,
  ModalDialog,
  PasswordField,
} from '@/shared/ui'
import { reauthMessages } from '../i18n'
import type { ReauthReturnError } from '../model/oauthReturn'
import { useReauthentication } from '../model/useReauthentication'
import { useReauthMethods } from '../model/useReauthMethods'

interface ReauthDialogProps {
  readonly open: boolean
  /** Mot de passe accepté : l'écran ferme le dialogue et relance l'action. */
  readonly onReauthenticated: () => void
  readonly onCancel: () => void
}

/**
 * AUTH-55, ADR 0034 et 0035 : « Confirmez votre identité ». Le mot de passe si le compte en a un ; un bouton par
 * fournisseur lié et configuré, qui part chez lui (navigation de premier niveau, ADR 0032, point 3) et revient sur
 * /parametres, le mode sudo ouvert : rien n'est gardé dans le navigateur pour rouvrir l'action (ADR 0012, point 2).
 * Tant que les méthodes du compte ne sont pas lues, rien n'est proposé.
 */
export function ReauthDialog({ open, onReauthenticated, onCancel }: ReauthDialogProps) {
  const t = useMessages(reauthMessages)
  const descriptionId = useId()
  const methods = useReauthMethods(open)
  const reauth = useReauthentication(onReauthenticated)
  const { passwordSet, providers } = methods
  const cancel = () => {
    reauth.clear()
    onCancel()
  }
  return (
    <ModalDialog
      open={open}
      onClose={cancel}
      closable={!reauth.pending}
      title={t('title')}
      subtitle={t('subtitle')}
      describedBy={methods.loaded ? descriptionId : undefined}
    >
      <form
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          reauth.submit()
        }}
      >
        <DialogBody>
          {methods.failed && <FailureAlert failure={{ kind: 'unexpected' }} />}
          {methods.loaded && (
            <p id={descriptionId} className="m-0 text-[14.5px] leading-[1.45] text-ink-2">
              {passwordSet ? t('passwordIntro') : t('providerIntro')}
            </p>
          )}
          {reauth.failure !== null && reauth.failure.kind !== 'invalid-password' && (
            <FormAlert failure={reauth.failure} />
          )}
          {passwordSet && (
            <PasswordField
              label={t('password')}
              autoComplete="current-password"
              maxLength={PASSWORD_INPUT_MAX_LENGTH}
              value={reauth.password}
              onChange={reauth.setPassword}
              disabled={reauth.locked}
              error={reauth.failure?.kind === 'invalid-password' ? t('invalidPassword') : null}
            />
          )}
          {passwordSet && providers.length > 0 && <OrSeparator />}
          {providers.length > 0 && <ProviderLinks providers={providers} />}
        </DialogBody>
        <DialogFooter
          onCancel={cancel}
          cancelable={!reauth.pending}
          submitLabel={passwordSet ? t('submit') : undefined}
          canSubmit={reauth.canSubmit}
        />
      </form>
    </ModalDialog>
  )
}

/** Refus d'un retour OAuth vers les paramètres (ADR 0034, point 2), comme les alertes de Connexion (ADR 0033). */
export function ReauthReturnAlert({ error }: { readonly error: ReauthReturnError }) {
  const t = useMessages(reauthMessages)
  return error === 'RATE_LIMITED' ? (
    <Alert variant="rate-limited" title={t('rateLimitedTitle')}>
      {t('rateLimitedBody')}
    </Alert>
  ) : (
    <Alert variant="error">{t('rejected')}</Alert>
  )
}
