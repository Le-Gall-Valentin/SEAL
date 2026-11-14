import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '@/shared/lib'
import { verificationView } from './verificationView'

const BASE = {
  code: '503271',
  secondsLeft: 90,
  codeAlive: true,
  resendsRemaining: 2,
  verifyError: null,
  sendError: null,
  verifyLocked: false,
  sendLocked: false,
  pending: false,
  newEmail: '',
  newEmailTouched: false,
  refusedEmail: null,
} as const

describe('AUTH-14, AUTH-17 : état de Verification-Courriel', () => {
  it('AUTH-14 : code complet, code vivant, tout est permis', () => {
    expect(verificationView(BASE)).toMatchObject({
      alert: null,
      canVerify: true,
      canResend: true,
      canChange: false,
    })
  })

  it('ADR 0024, point 10 : compte à rebours à zéro, message d’expiration, mais « Vérifier » permis', () => {
    expect(verificationView({ ...BASE, secondsLeft: 0 })).toMatchObject({
      alert: { kind: 'expired' },
      canVerify: true,
    })
  })

  it('ADR 0024 : aucun code vivant annoncé par /me, message d’expiration et « Vérifier » refusé', () => {
    expect(verificationView({ ...BASE, codeAlive: false, secondsLeft: null })).toMatchObject({
      alert: { kind: 'expired' },
      canVerify: false,
    })
  })

  it('AUTH-17 : le renvoi et le changement partagent leur limite, pas la vérification', () => {
    const view = verificationView({
      ...BASE,
      sendLocked: true,
      sendError: new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 60 }),
      newEmail: 'noah@uqac.ca',
    })
    expect(view).toMatchObject({
      alert: { kind: 'rate-limited', minutes: 1 },
      canVerify: true,
      canResend: false,
      canChange: false,
    })
  })

  it('AUTH-17 : sans renvoi restant, ni renvoi ni changement de courriel', () => {
    expect(
      verificationView({ ...BASE, resendsRemaining: 0, newEmail: 'noah@uqac.ca' }),
    ).toMatchObject({
      canResend: false,
      canChange: false,
    })
  })

  it('AUTH-17 : nouvelle adresse mal formée signalée une fois quittée, refusée par le serveur signalée', () => {
    expect(
      verificationView({ ...BASE, newEmail: 'noah@', newEmailTouched: true }).newEmailInvalid,
    ).toBe(true)
    expect(verificationView({ ...BASE, newEmail: 'noah@' }).newEmailInvalid).toBe(false)
    expect(
      verificationView({ ...BASE, newEmail: 'noah@uqac.ca', refusedEmail: 'noah@uqac.ca' }),
    ).toMatchObject({ newEmailInvalid: true, canChange: false })
  })

  it('ADR 0024 : l’erreur de l’action en cours prime ; un refus de l’adresse s’affiche sous son champ', () => {
    expect(verificationView({ ...BASE, verifyError: new NetworkError() }).alert).toEqual({
      kind: 'network',
    })
    expect(
      verificationView({
        ...BASE,
        sendError: new ApiError(400, 'VALIDATION_FAILED', { fields: ['email'] }),
      }).alert,
    ).toBeNull()
  })

  it('AUTH-14 : rien n’est permis pendant une requête, ni avec un code incomplet', () => {
    expect(verificationView({ ...BASE, pending: true })).toMatchObject({
      canVerify: false,
      canResend: false,
    })
    expect(verificationView({ ...BASE, code: '5032' }).canVerify).toBe(false)
  })
})
