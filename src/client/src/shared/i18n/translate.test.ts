import { describe, expect, it } from 'vitest'
import { defineMessages, hasMessage } from './messages'
import { translate } from './translate'

const messages = defineMessages({
  fr: {
    title: 'Impossible de charger',
    greeting: 'Bonjour {name}, {count} messages',
    probe: 'A {constructor} B',
  },
  en: { title: 'Unable to load', probe: '' },
})

describe('ADR 0017 : traduction d’un texte de tranche', () => {
  it('ACC-05 : renvoie le texte de la langue demandée', () => {
    expect(translate(messages, 'fr', 'title')).toBe('Impossible de charger')
    expect(translate(messages, 'en', 'title')).toBe('Unable to load')
  })

  it('ACC-05 : un texte anglais absent ou vide, ou des textes sans anglais, retombent sur le français', () => {
    expect(translate(messages, 'en', 'greeting', { name: 'Ada', count: 3 })).toBe(
      'Bonjour Ada, 3 messages',
    )
    expect(translate(messages, 'en', 'probe', { constructor: 'x' })).toBe('A x B')
    expect(translate(defineMessages({ fr: { solo: 'Seul' } }), 'en', 'solo')).toBe('Seul')
  })

  it('ACC-05 : interpole les paramètres nommés et laisse visible un paramètre manquant', () => {
    expect(translate(messages, 'fr', 'greeting', { name: 'Ada', count: 0 })).toBe(
      'Bonjour Ada, 0 messages',
    )
    expect(translate(messages, 'fr', 'greeting', { name: 'Ada' })).toBe(
      'Bonjour Ada, {count} messages',
    )
  })

  it("ACC-05 : ne lit jamais une propriété héritée d'Object.prototype, ni comme paramètre ni comme clé", () => {
    expect(translate(messages, 'fr', 'probe', {})).toBe('A {constructor} B')
    for (const key of ['constructor', 'toString', '__proto__', 'inconnue']) {
      expect(() => translate(messages, 'fr', key as never), key).toThrow(/Texte inconnu/)
    }
  })

  it('ACC-05 : hasMessage reconnaît une clé venue de l’extérieur, sans les propriétés héritées', () => {
    expect(hasMessage(messages, 'title')).toBe(true)
    for (const key of ['constructor', 'toString', '__proto__', 'inconnue']) {
      expect(hasMessage(messages, key), key).toBe(false)
    }
  })
})
