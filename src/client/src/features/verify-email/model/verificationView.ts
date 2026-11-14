import { isEmailWellFormed } from '@/entities/account'
import { ApiError } from '@/shared/lib'
import { CODE_LENGTH, verifyFailureOf, type VerifyFailure } from './code'

/** Ce que l'écran sait au moment du rendu. */
export interface VerificationInput {
  readonly code: string
  /** Secondes avant l'expiration du code vivant ; null sans code vivant. */
  readonly secondsLeft: number | null
  /** /me annonce un code vivant (codeExpiresAt non nul). */
  readonly codeAlive: boolean
  readonly resendsRemaining: number
  readonly verifyError: unknown
  /** Erreur du renvoi ou du changement de courriel, qui partagent leur limite (AUTH-17). */
  readonly sendError: unknown
  readonly verifyLocked: boolean
  readonly sendLocked: boolean
  readonly pending: boolean
  readonly newEmail: string
  readonly newEmailTouched: boolean
  readonly refusedEmail: string | null
}

function isEmailRefusal(error: unknown): boolean {
  return error instanceof ApiError && error.fields.includes('email')
}

/**
 * AUTH-14, AUTH-17, ADR 0024 (points 9 et 10) : alerte et actions permises de Verification-Courriel.
 * - Le serveur tranche l'expiration : à zéro, le compte à rebours affiche le message, mais « Vérifier » reste permis ;
 *   seul /me, sans code vivant, le refuse.
 * - Le renvoi et le changement de courriel partagent leur limite et leur verrou ; la vérification garde les siens.
 */
export function verificationView(input: VerificationInput) {
  const error = input.verifyError ?? input.sendError
  const failure: VerifyFailure | null =
    error === null || error === undefined || isEmailRefusal(error) ? null : verifyFailureOf(error)
  const expired = input.secondsLeft === null || input.secondsLeft === 0
  const emailFormed = isEmailWellFormed(input.newEmail)
  const canSend = input.resendsRemaining > 0 && !input.sendLocked && !input.pending
  return {
    alert: failure ?? (expired ? ({ kind: 'expired' } as const) : null),
    canVerify:
      input.code.length === CODE_LENGTH && input.codeAlive && !input.verifyLocked && !input.pending,
    canResend: canSend,
    canChange: canSend && emailFormed && input.refusedEmail !== input.newEmail,
    newEmailInvalid:
      (input.newEmailTouched && input.newEmail !== '' && !emailFormed) ||
      input.refusedEmail === input.newEmail,
  }
}
