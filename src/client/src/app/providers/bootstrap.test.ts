import { describe, expect, it, vi } from 'vitest'
import type { Apis } from './ApiProviders'
import { bootstrap } from './bootstrap'

function apisWith(reloadCsrf: () => Promise<void>): Apis {
  const never = () => Promise.reject(new Error('non attendu'))
  return {
    session: { me: never, reloadCsrf },
    passwordBlocklist: { load: never },
    login: { login: never },
    register: { register: never, usernameAvailability: never },
    verifyEmail: { verify: never, resend: never, changeEmail: never },
    passwordReset: { request: never, inspect: never, confirm: never },
    logout: { logout: never },
  }
}

describe('Racine de composition : démarrage', () => {
  it('AUTH-60 : le jeton CSRF est rechargé au démarrage, par le port de la session', () => {
    const reloadCsrf = vi.fn(() => Promise.resolve())

    bootstrap(apisWith(reloadCsrf))

    expect(reloadCsrf).toHaveBeenCalledTimes(1)
  })

  it('AUTH-60 : un échec est sans suite, la première requête non sûre rechargera le jeton', async () => {
    const failure = Promise.reject(new Error('réseau'))
    const reloadCsrf = vi.fn(() => failure)

    expect(() => {
      bootstrap(apisWith(reloadCsrf))
    }).not.toThrow()
    await expect(failure).rejects.toThrow('réseau')
  })
})
