import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { Alert } from './Alert'
import { FailureAlert } from './FailureAlert'
import { PasswordField } from './PasswordField'
import { PrimaryButton } from './PrimaryButton'
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
  it('TextField : champ étiqueté, autocomplete et longueur maximale transmis (SEC-01)', async () => {
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

  it('Alert : annoncée dès son affichage, avec son titre', async () => {
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

  it('PrimaryButton : bouton d’envoi, désactivable', async () => {
    const screen = await render(<PrimaryButton disabled>Se connecter</PrimaryButton>)

    const button = screen.getByRole('button', { name: 'Se connecter' })
    await expect.element(button).toHaveAttribute('type', 'submit')
    await expect.element(button).toBeDisabled()
  })
})
