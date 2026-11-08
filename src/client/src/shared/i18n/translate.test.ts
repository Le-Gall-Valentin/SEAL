import { describe, expect, it } from 'vitest'
import { defineMessages, translate } from './index'

const messages = defineMessages({
  fr: { title: 'Impossible de charger', greeting: 'Bonjour {name}, {count} messages' },
  en: { title: 'Unable to load' },
})

describe('ADR 0017 : traduction d’un texte de tranche', () => {
  it('ACC-05 : renvoie le texte de la langue demandée', () => {
    expect(translate(messages, 'fr', 'title')).toBe('Impossible de charger')
    expect(translate(messages, 'en', 'title')).toBe('Unable to load')
  })

  it('ACC-05 : un texte anglais absent retombe sur le français', () => {
    expect(translate(messages, 'en', 'greeting', { name: 'Ada', count: 3 })).toBe(
      'Bonjour Ada, 3 messages',
    )
  })

  it('ADR 0017 : interpole les paramètres nommés et laisse visible un paramètre manquant', () => {
    expect(translate(messages, 'fr', 'greeting', { name: 'Ada', count: 0 })).toBe(
      'Bonjour Ada, 0 messages',
    )
    expect(translate(messages, 'fr', 'greeting', { name: 'Ada' })).toBe(
      'Bonjour Ada, {count} messages',
    )
  })
})
