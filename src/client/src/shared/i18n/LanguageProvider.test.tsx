import { useEffect } from 'react'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { defineMessages, LanguageProvider, useMessages } from './index'
import type { Language } from './index'

const messages = defineMessages({
  fr: { retry: 'Réessayer', greeting: 'Bonjour {name}' },
  en: { retry: 'Retry', greeting: 'Hello {name}' },
})

function Labels({ onT }: { readonly onT?: (t: unknown) => void }) {
  const t = useMessages(messages)
  useEffect(() => {
    onT?.(t)
  })
  return (
    <span>
      {t('retry')} · {t('greeting', { name: 'Ada' })}
    </span>
  )
}

// Contrôles de typage seulement, vérifiés par tsc : ce composant n'est jamais rendu.
export function TypeChecks() {
  const t = useMessages(messages)
  // @ts-expect-error ADR 0017 : une clé absente des textes de la tranche ne compile pas.
  t('inconnue')
  // @ts-expect-error ADR 0017 (Q6) : un paramètre oublié ne compile pas.
  t('greeting')
  // @ts-expect-error ADR 0017 (Q6) : un paramètre en trop ne compile pas.
  t('retry', { name: 'Ada' })
  return null
}

function inLanguage(language: Language, onT?: (t: unknown) => void) {
  return (
    <LanguageProvider language={language}>
      <Labels onT={onT} />
    </LanguageProvider>
  )
}

describe('ADR 0017 : langue courante et textes typés', () => {
  it('ACC-05 : le français est la langue par défaut, sans fournisseur', async () => {
    const screen = await render(<Labels />)

    await expect.element(screen.getByText('Réessayer · Bonjour Ada')).toBeVisible()
  })

  it('ACC-05 : la langue vient du fournisseur, et son changement met les textes à jour', async () => {
    const screen = await render(inLanguage('en'))
    await expect.element(screen.getByText('Retry · Hello Ada')).toBeVisible()

    await screen.rerender(inLanguage('fr'))

    await expect.element(screen.getByText('Réessayer · Bonjour Ada')).toBeVisible()
  })

  it('ACC-05 : t garde la même référence tant que la langue ne change pas', async () => {
    const seen: unknown[] = []
    const record = (t: unknown) => {
      seen.push(t)
    }
    const screen = await render(inLanguage('fr', record))
    await screen.rerender(inLanguage('fr', record))
    await screen.rerender(inLanguage('en', record))

    expect(seen).toHaveLength(3)
    expect(seen[1]).toBe(seen[0])
    expect(seen[2]).not.toBe(seen[1])
  })
})
