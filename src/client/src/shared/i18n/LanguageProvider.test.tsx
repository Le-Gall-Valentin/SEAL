import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { defineMessages, LanguageProvider, useMessages } from './index'

const messages = defineMessages({
  fr: { retry: 'Réessayer' },
  en: { retry: 'Retry' },
})

function RetryLabel() {
  const t = useMessages(messages)
  // @ts-expect-error ADR 0017 : une clé absente des textes de la tranche ne compile pas.
  t('inconnue')
  return <span>{t('retry')}</span>
}

describe('ADR 0017 : langue courante et textes typés', () => {
  it('ACC-05 : le français est la langue par défaut, sans fournisseur', async () => {
    const screen = await render(<RetryLabel />)

    await expect.element(screen.getByText('Réessayer')).toBeVisible()
  })

  it('ACC-05 : la langue vient du fournisseur monté au-dessus', async () => {
    const screen = await render(
      <LanguageProvider language="en">
        <RetryLabel />
      </LanguageProvider>,
    )

    await expect.element(screen.getByText('Retry')).toBeVisible()
  })
})
