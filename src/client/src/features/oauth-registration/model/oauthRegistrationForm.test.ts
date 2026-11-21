import { describe, expect, it } from 'vitest'
import { oauthRegistrationView } from './oauthRegistrationForm'

const BASE = {
  username: 'valentin.dupont',
  status: { kind: 'available' } as const,
  displayName: 'Valentin Dupont',
  displayNameTouched: false,
  refusedDisplayName: undefined,
}

describe('AUTH-32, SEC-01 : formulaire d’Identifiant-OAuth', () => {
  it('AUTH-32 : identifiant libre et nom affiché valide, envoi permis', () => {
    expect(oauthRegistrationView(BASE)).toEqual({ displayNameInvalid: false, valid: true })
  })

  it('AUTH-05 : disponibilité inconnue (réseau, 429), le serveur tranchera', () => {
    expect(oauthRegistrationView({ ...BASE, status: { kind: 'unknown' } }).valid).toBe(true)
  })

  it.each([{ kind: 'taken', suggestions: [] }, { kind: 'checking' }, { kind: 'invalid' }] as const)(
    'AUTH-05 : identifiant %o, envoi refusé',
    (status) => {
      expect(oauthRegistrationView({ ...BASE, status }).valid).toBe(false)
    },
  )

  it('ACC-03 : un nom proposé vide attend la saisie, sans erreur affichée avant la sortie du champ', () => {
    expect(oauthRegistrationView({ ...BASE, displayName: '' })).toEqual({
      displayNameInvalid: false,
      valid: false,
    })
    expect(
      oauthRegistrationView({ ...BASE, displayName: '\u200b', displayNameTouched: true }),
    ).toEqual({ displayNameInvalid: true, valid: false })
  })

  it('SEC-01 : un nom refusé par le serveur reste signalé tant qu’il n’a pas changé', () => {
    expect(oauthRegistrationView({ ...BASE, refusedDisplayName: 'Valentin Dupont' })).toEqual({
      displayNameInvalid: true,
      valid: false,
    })
    expect(
      oauthRegistrationView({
        ...BASE,
        displayName: 'Valentin',
        refusedDisplayName: 'Valentin Dupont',
      }),
    ).toEqual({ displayNameInvalid: false, valid: true })
  })
})
