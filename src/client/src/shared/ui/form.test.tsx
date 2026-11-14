import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { Alert } from './Alert'
import { FailureAlert } from './FailureAlert'
import { FormAlert } from './FormAlert'
import { PasswordField } from './PasswordField'
import { PrimaryButton } from './PrimaryButton'
import { RateLimitedAlert } from './RateLimitedAlert'
import { TextField } from './TextField'

function ControlledPassword({ disabled = false }: { readonly disabled?: boolean }) {
  const [value, setValue] = useState('fjord-tuque-raquette')
  return (
    <PasswordField
      label="Mot de passe"
      autoComplete="current-password"
      maxLength={512}
      value={value}
      onChange={setValue}
      disabled={disabled}
    />
  )
}

describe('Champs, alertes et bouton des formulaires (maquette des écrans d’authentification)', () => {
  it('SEC-01 : TextField, champ étiqueté, autocomplete et longueur maximale transmis', async () => {
    let typed = ''
    const screen = await render(
      <TextField
        label="Identifiant ou courriel"
        autoComplete="username"
        maxLength={254}
        value=""
        onChange={(value) => {
          typed = value
        }}
      />,
    )

    const input = screen.getByLabelText('Identifiant ou courriel')
    await expect.element(input).toHaveAttribute('autocomplete', 'username')
    await expect.element(input).toHaveAttribute('maxlength', '254')
    await input.fill('valentin')
    expect(typed).toBe('valentin')
  })

  it('AUTH-20 : le mot de passe est masqué, et « Afficher le mot de passe » le montre puis le cache', async () => {
    const screen = await render(<ControlledPassword />)

    const input = screen.getByLabelText('Mot de passe', { exact: true })
    const toggle = screen.getByRole('button', { name: 'Afficher le mot de passe' })
    await expect.element(input).toHaveAttribute('type', 'password')
    await expect.element(toggle).toHaveAttribute('aria-pressed', 'false')

    await toggle.click()
    await expect.element(input).toHaveAttribute('type', 'text')
    await expect.element(toggle).toHaveAttribute('aria-pressed', 'true')

    await toggle.click()
    await expect.element(input).toHaveAttribute('type', 'password')
  })

  it('AUTH-22 : un champ désactivé désactive aussi son bouton « Afficher »', async () => {
    const screen = await render(<ControlledPassword disabled />)

    await expect.element(screen.getByLabelText('Mot de passe', { exact: true })).toBeDisabled()
    await expect
      .element(screen.getByRole('button', { name: 'Afficher le mot de passe' }))
      .toBeDisabled()
  })

  it('ADR 0024, point 8 : Alert, annoncée dès son affichage, avec son titre', async () => {
    const screen = await render(
      <Alert variant="rate-limited" title="Trop de tentatives">
        Réessayez plus tard.
      </Alert>,
    )

    const alert = screen.getByRole('alert')
    await expect.element(alert.getByText('Trop de tentatives', { exact: true })).toBeVisible()
    await expect.element(alert.getByText('Réessayez plus tard.', { exact: true })).toBeVisible()
  })

  it.each([
    ['network', 'Connexion impossible. Vérifiez votre réseau puis réessayez.'],
    ['unexpected', 'Une erreur inattendue est survenue. Réessayez.'],
  ] as const)('ADR 0024, point 8 : échec %s', async (kind, text) => {
    const screen = await render(<FailureAlert failure={{ kind }} />)

    await expect.element(screen.getByRole('alert')).toHaveTextContent(text)
  })

  it('AUTH-20 : PrimaryButton, bouton d’envoi, désactivable', async () => {
    const screen = await render(<PrimaryButton disabled>Se connecter</PrimaryButton>)

    const button = screen.getByRole('button', { name: 'Se connecter' })
    await expect.element(button).toHaveAttribute('type', 'submit')
    await expect.element(button).toBeDisabled()
  })

  it('ADR 0024, point 9 : TextField, aide et erreur sous le champ, reliées au champ ; l’erreur est annoncée', async () => {
    const screen = await render(
      <TextField
        label="Nom affiché"
        autoComplete="name"
        maxLength={200}
        value=""
        onChange={() => undefined}
        hint="Visible par vos amis. Modifiable à tout moment."
        error="1 à 50 caractères, dont un visible au moins."
      />,
    )

    const input = screen.getByLabelText('Nom affiché')
    await expect.element(input).toHaveAttribute('aria-invalid', 'true')
    await expect
      .element(input)
      .toHaveAccessibleDescription(
        'Visible par vos amis. Modifiable à tout moment. 1 à 50 caractères, dont un visible au moins.',
      )
    const error = screen.getByText('1 à 50 caractères, dont un visible au moins.')
    await expect.element(error).toBeVisible()
    expect(error.element().closest('[aria-live="polite"]')).not.toBeNull()
  })

  it('ADR 0024, point 9 : TextField sans erreur, le champ n’est pas marqué invalide', async () => {
    const screen = await render(
      <TextField
        label="Courriel"
        autoComplete="email"
        maxLength={254}
        value=""
        onChange={() => undefined}
      />,
    )

    await expect.element(screen.getByLabelText('Courriel')).not.toHaveAttribute('aria-invalid')
  })

  it.each([
    [1, undefined, 'Réessayez dans 1 minute.'],
    [5, undefined, 'Réessayez dans 5 minutes.'],
    [
      15,
      'Vous pouvez aussi réinitialiser votre mot de passe.',
      'Réessayez dans 15 minutes. Vous pouvez aussi réinitialiser votre mot de passe.',
    ],
  ] as const)(
    'SEC-10, ADR 0015 : « Trop de tentatives », %i minute(s)',
    async (minutes, hint, text) => {
      const screen = await render(<RateLimitedAlert minutes={minutes} hint={hint} />)

      const alert = screen.getByRole('alert')
      await expect.element(alert.getByText('Trop de tentatives', { exact: true })).toBeVisible()
      await expect.element(alert).toMatchTextContent(text)
    },
  )

  it.each([
    [{ kind: 'network' }, 'Connexion impossible. Vérifiez votre réseau puis réessayez.'],
    [{ kind: 'unexpected' }, 'Une erreur inattendue est survenue. Réessayez.'],
    [
      { kind: 'rate-limited', minutes: 3 },
      'Trop de tentativesRéessayez dans 3 minutes. Plus tard.',
    ],
  ] as const)(
    'ADR 0024, point 8 : FormAlert affiche l’alerte de l’échec %o',
    async (failure, text) => {
      const screen = await render(<FormAlert failure={failure} rateLimitedHint="Plus tard." />)

      await expect.element(screen.getByRole('alert')).toHaveTextContent(text)
    },
  )

  it('ADR 0024 : FormAlert sans échec n’affiche rien', async () => {
    const screen = await render(<FormAlert failure={null} />)

    expect(screen.container.textContent).toBe('')
  })
})
