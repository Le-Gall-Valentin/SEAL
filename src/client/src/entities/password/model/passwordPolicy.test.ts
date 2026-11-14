import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { z } from 'zod'
import { localPartOf } from '@/entities/account/@x/password'
import {
  canSubmitPassword,
  evaluatePassword,
  foldPassword,
  isPasswordAccepted,
} from './passwordPolicy'

// ADR 0024, point 6 : vecteurs produits par src/protocol/tools/gen_vectors.py, relus aussi par le serveur.
const PROTOCOL = new URL('../../../../../protocol/', import.meta.url)

const VECTORS = z.object({
  format: z.literal('seal-password-policy-v1'),
  cases: z.array(
    z.object({
      name: z.string(),
      password: z.string(),
      username: z.string(),
      email: z.string(),
      localPart: z.string(),
      rules: z.object({ length: z.boolean(), context: z.boolean(), blocklist: z.boolean() }),
      accepted: z.boolean(),
    }),
  ),
})

const { cases } = VECTORS.parse(
  JSON.parse(readFileSync(new URL('vectors/password-policy.json', PROTOCOL), 'utf-8')),
)
const BLOCKLIST: ReadonlySet<string> = new Set(
  readFileSync(new URL('password-blocklist.txt', PROTOCOL), 'utf-8')
    .split('\n')
    .filter((line) => line !== '')
    .map(foldPassword),
)

const state = (met: boolean) => (met ? 'met' : 'unmet')

describe('AUTH-11 : politique des mots de passe, d’après les vecteurs du serveur', () => {
  it('AUTH-11 : les vecteurs couvrent des mots de passe acceptés et refusés', () => {
    expect(cases.map((vector) => vector.accepted)).toEqual(expect.arrayContaining([true, false]))
  })

  it.each(cases.map((vector) => [vector.name, vector] as const))(
    'AUTH-11 : %s',
    (_name, vector) => {
      const rules = evaluatePassword(
        vector.password,
        { username: vector.username, email: vector.email },
        BLOCKLIST,
      )

      expect(localPartOf(vector.email)).toBe(vector.localPart)
      expect(rules).toEqual({
        length: state(vector.rules.length),
        context: state(vector.rules.context),
        blocklist: state(vector.rules.blocklist),
      })
      expect(isPasswordAccepted(rules)).toBe(vector.accepted)
    },
  )

  it('ADR 0024 : liste de blocage absente, la règle reste « non vérifiée », jamais respectée', () => {
    const context = { username: 'valentin', email: 'valentin@exemple.ca' }

    expect(evaluatePassword('le phoque nage dans le port', context, null)).toEqual({
      length: 'met',
      context: 'met',
      blocklist: 'unknown',
    })
    expect(evaluatePassword('court', context, null).blocklist).toBe('unmet')
  })

  it('ADR 0024 : l’envoi reste possible sans liste de blocage ; le serveur tranche', () => {
    expect(canSubmitPassword({ length: 'met', context: 'met', blocklist: 'unknown' })).toBe(true)
    expect(isPasswordAccepted({ length: 'met', context: 'met', blocklist: 'unknown' })).toBe(false)
    expect(canSubmitPassword({ length: 'met', context: 'met', blocklist: 'unmet' })).toBe(false)
    expect(canSubmitPassword({ length: 'met', context: 'unmet', blocklist: 'met' })).toBe(false)
  })
})
