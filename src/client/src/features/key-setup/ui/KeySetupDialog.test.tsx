import { describe, expect, it, vi } from 'vitest'
import { userEvent } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import { type KeySetup, useKeySetup } from '../model/useKeySetup'
import { KeySetupDialog } from './KeySetupDialog'

vi.mock('../model/useKeySetup', () => ({ useKeySetup: vi.fn() }))

const OWNER = { userId: '0192f7a1-ffff-7000-8000-00000000a11c', username: 'valentin.dupont' }
const GROUPS = ['8WQK', '3MZD', 'T7PA', 'R2HX', '5NCF', 'Y9GB', 'K4VE', 'J6TS']

function state(overrides: Partial<KeySetup> = {}): KeySetup {
  return {
    ready: true,
    waiting: false,
    step: 1,
    groups: GROUPS,
    picked: [2, 6],
    typed: ['', ''],
    type: vi.fn(),
    mismatch: () => false,
    confirmed: false,
    showKey: vi.fn(),
    verify: vi.fn(),
    download: vi.fn(),
    finish: vi.fn(),
    saving: false,
    failure: null,
    ...overrides,
  }
}

async function renderWith(setup: KeySetup) {
  vi.mocked(useKeySetup).mockReturnValue(setup)
  return render(<KeySetupDialog owner={OWNER} onDone={() => undefined} />)
}

describe('KEY-30 : Dialogue-Cle-Recuperation, étape 1', () => {
  it('KEY-30 : textes de la maquette, les 8 groupes numérotés, le .txt et pas de « Copier » (ADR 0029, point 1)', async () => {
    const setup = state()
    const screen = await renderWith(setup)
    const dialog = screen.getByRole('dialog', { name: 'Votre clé de récupération' })

    await expect.element(dialog).toHaveAttribute('aria-modal', 'true')
    await expect.element(screen.getByText('Étape 1 sur 2')).toBeVisible()
    await expect
      .element(dialog)
      .toHaveAccessibleDescription(
        'Elle seule permet de restaurer vos clés sur un nouvel appareil. SEAL ne la connaît pas et ne pourra jamais la retrouver pour vous.',
      )
    const list = screen.getByRole('list', {
      name: 'Clé de récupération, 8 groupes de 4 caractères',
    })
    await expect.element(list).toHaveTextContent('18WQK23MZD3T7PA4R2HX55NCF6Y9GB7K4VE8J6TS')
    // KEY-31 : la traduction automatique du navigateur n'envoie pas la clé à un service tiers.
    await expect.element(list).toHaveAttribute('translate', 'no')
    await expect
      .element(screen.getByRole('note'))
      .toHaveTextContent(
        'Affichée une seule fois. Rangez-la hors de cet appareil : sur papier ou dans un gestionnaire de mots de passe.',
      )
    expect(screen.getByRole('button', { name: 'Copier' }).query()).toBeNull()

    await screen.getByRole('button', { name: 'Télécharger (.txt)' }).click()
    await screen.getByRole('button', { name: 'J’ai noté ma clé' }).click()
    expect(setup.download).toHaveBeenCalledOnce()
    expect(setup.verify).toHaveBeenCalledOnce()
  })

  it('ADR 0029, point 3 : rien ne s’affiche tant que le verrou des clés n’est pas tenu', async () => {
    const screen = await renderWith(state({ ready: false }))

    expect(screen.getByRole('dialog').query()).toBeNull()
  })
})

describe('KEY-30 : Dialogue-Cle-Recuperation, étape 2', () => {
  it('KEY-30 : les deux groupes tirés, numérotés de 1 à 8, et « Terminer » inactif sans confirmation', async () => {
    const setup = state({ step: 2 })
    const screen = await renderWith(setup)

    await expect.element(screen.getByRole('dialog', { name: 'Vérifiez votre clé' })).toBeVisible()
    await expect.element(screen.getByText('Étape 2 sur 2')).toBeVisible()
    await expect
      .element(
        screen.getByText(
          'Saisissez deux groupes tirés au hasard pour confirmer que votre clé est bien notée.',
        ),
      )
      .toBeVisible()
    await expect.element(screen.getByLabelText('Groupe 3')).toHaveFocus()
    await expect.element(screen.getByLabelText('Groupe 7')).toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Terminer' })).toBeDisabled()

    await screen.getByLabelText('Groupe 3').fill('t7pa')
    expect(setup.type).toHaveBeenLastCalledWith(0, 't7pa')
    // Comme « T7PA » dans la maquette : majuscules d'affichage, la saisie reste telle quelle.
    expect(getComputedStyle(screen.getByLabelText('Groupe 3').element()).textTransform).toBe(
      'uppercase',
    )
    await expect.element(screen.getByLabelText('Groupe 3')).toHaveAttribute('translate', 'no')
    await screen.getByRole('button', { name: 'Revoir la clé' }).click()
    expect(setup.showKey).toHaveBeenCalledOnce()
  })

  it('ADR 0029, point 5 : un groupe faux est signalé sous sa case', async () => {
    const screen = await renderWith(
      state({ step: 2, typed: ['ZZZZ', ''], mismatch: (slot) => slot === 0 }),
    )

    await expect.element(screen.getByLabelText('Groupe 3')).toHaveAttribute('aria-invalid', 'true')
    await expect
      .element(screen.getByLabelText('Groupe 3'))
      .toHaveAccessibleDescription('Ce groupe ne correspond pas à votre clé.')
    await expect
      .element(screen.getByLabelText('Groupe 7'))
      .not.toHaveAttribute('aria-invalid', 'true')
  })

  it('KEY-30, ADR 0029 (point 9) : clé confirmée : le statut, le texte au futur, et « Terminer » lance la sauvegarde', async () => {
    const setup = state({ step: 2, typed: ['T7PA', 'K4VE'], confirmed: true })
    const screen = await renderWith(setup)

    await expect.element(screen.getByRole('status')).toHaveTextContent('Clé confirmée.')
    await expect
      .element(
        screen.getByText(
          'Votre trousseau sera sauvegardé chiffré sur le serveur, qui ne pourra pas le lire.',
          { exact: true },
        ),
      )
      .toBeVisible()
    expect(screen.getByText(/Paramètres/).query()).toBeNull()
    await screen.getByRole('button', { name: 'Terminer' }).click()
    expect(setup.finish).toHaveBeenCalledOnce()
  })

  it('ADR 0029, points 5 et 9 : un échec réseau s’annonce, sans le texte de sauvegarde, et « Terminer » reste disponible ; en cours, il est inactif', async () => {
    const failed = await renderWith(
      state({ step: 2, confirmed: true, failure: { kind: 'network' } }),
    )

    await expect
      .element(failed.getByRole('alert'))
      .toHaveTextContent(
        'Vos clés n’ont pas pu être sauvegardées. Vérifiez votre réseau puis réessayez.',
      )
    expect(failed.getByText(/sera sauvegardé/).query()).toBeNull()
    await expect.element(failed.getByRole('button', { name: 'Terminer' })).toBeEnabled()
    await failed.unmount()

    const unexpected = await renderWith(
      state({ step: 2, confirmed: true, failure: { kind: 'unexpected' } }),
    )
    await expect
      .element(unexpected.getByRole('alert'))
      .toHaveTextContent('Une erreur inattendue est survenue. Réessayez.')
    await unexpected.unmount()

    const saving = await renderWith(state({ step: 2, confirmed: true, saving: true }))
    await expect.element(saving.getByRole('button', { name: 'Terminer' })).toBeDisabled()
  })

  it('Accessibilité : « Revoir la clé » rend le focus à la fenêtre, qui annonce l’étape 1', async () => {
    const setup = state({ step: 2 })
    const screen = await renderWith(setup)
    await expect.element(screen.getByLabelText('Groupe 3')).toHaveFocus()

    await screen.getByRole('button', { name: 'Revoir la clé' }).click()
    vi.mocked(useKeySetup).mockReturnValue({ ...setup, step: 1 })
    await screen.rerender(<KeySetupDialog owner={OWNER} onDone={() => undefined} />)

    await expect
      .element(screen.getByRole('dialog', { name: 'Votre clé de récupération' }))
      .toHaveFocus()
  })

  it('Dialogue-Cle-Recuperation : au clavier, « Terminer » porte l’anneau de focus de la maquette', async () => {
    const screen = await renderWith(state({ step: 2, typed: ['T7PA', 'K4VE'], confirmed: true }))
    await expect.element(screen.getByLabelText('Groupe 3')).toHaveFocus()

    await userEvent.tab()
    await userEvent.tab()
    await userEvent.tab()

    const finish = screen.getByRole('button', { name: 'Terminer' })
    await expect.element(finish).toHaveFocus()
    expect(getComputedStyle(finish.element())).toMatchObject({
      outlineStyle: 'solid',
      outlineWidth: '2px',
      outlineOffset: '2px',
      outlineColor: 'rgb(102, 116, 28)',
    })
  })
})

describe('KEY-34 : Dialogue-Cle-Recuperation à la réinitialisation (ADR 0036)', () => {
  it('KEY-34 : en variante « reset », le modèle reçoit le relais de la ré-authentification ; sinon, rien', async () => {
    const onDone = vi.fn()
    const onReauthRequired = vi.fn()
    vi.mocked(useKeySetup).mockReturnValue(state())

    await render(
      <KeySetupDialog
        owner={OWNER}
        onDone={onDone}
        variant="reset"
        onReauthRequired={onReauthRequired}
      />,
    )
    await render(<KeySetupDialog owner={OWNER} onDone={onDone} />)

    expect(vi.mocked(useKeySetup).mock.calls.at(0)).toEqual([OWNER, onDone, { onReauthRequired }])
    expect(vi.mocked(useKeySetup).mock.calls.at(-1)).toEqual([OWNER, onDone, undefined])
  })

  it('ADR 0029 (point 13), ADR 0036 : tant qu’un autre onglet tient le verrou, l’écran d’attente fourni ; sinon rien', async () => {
    vi.mocked(useKeySetup).mockReturnValue(state({ ready: false, waiting: true }))
    const screen = await render(
      <KeySetupDialog
        owner={OWNER}
        onDone={() => undefined}
        waiting={<p>attente de l’autre onglet</p>}
      />,
    )
    await expect.element(screen.getByText('attente de l’autre onglet')).toBeVisible()
    expect(screen.getByRole('dialog').query()).toBeNull()

    vi.mocked(useKeySetup).mockReturnValue(state({ ready: false, waiting: false }))
    await screen.rerender(
      <KeySetupDialog
        owner={OWNER}
        onDone={() => undefined}
        waiting={<p>attente de l’autre onglet</p>}
      />,
    )
    expect(screen.getByText('attente de l’autre onglet').query()).toBeNull()
  })

  it('SEC-10, ADR 0036 (point 7) : une limite atteinte s’annonce « Trop de tentatives », avec son délai', async () => {
    const screen = await renderWith(
      state({ step: 2, confirmed: true, failure: { kind: 'rate-limited', minutes: 120 } }),
    )

    await expect.element(screen.getByText('Trop de tentatives')).toBeVisible()
    await expect.element(screen.getByText('120 minutes')).toBeVisible()
    expect(
      screen.getByText('Vos clés n’ont pas pu être sauvegardées.', { exact: false }).query(),
    ).toBeNull()
  })
})
