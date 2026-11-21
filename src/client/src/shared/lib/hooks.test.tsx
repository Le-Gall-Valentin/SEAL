import { useEffect, useState } from 'react'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { ApiError } from './apiErrors'
import { useDebouncedValue } from './useDebouncedValue'
import { useRetryAfterLock } from './useRetryAfterLock'
import { type SubmitGuard, useSubmitGuard } from './useSubmitGuard'

function Debounced() {
  const [value, setValue] = useState('')
  const debounced = useDebouncedValue(value, 200)
  return (
    <>
      <input
        aria-label="saisie"
        value={value}
        onChange={(event) => {
          setValue(event.target.value)
        }}
      />
      <output>{debounced}</output>
    </>
  )
}

function Locked({ error }: { readonly error: unknown }) {
  const [released, setReleased] = useState(false)
  const locked = useRetryAfterLock(released ? null : error, () => {
    setReleased(true)
  })
  return <p>{locked ? 'verrouillé' : 'libre'}</p>
}

// Un écran qui refait son rendu chaque 100 ms pendant le verrou, comme le compte à rebours de Verification-Courriel,
// et qui passe une nouvelle fonction de libération à chaque rendu.
function TickingLocked({ error }: { readonly error: unknown }) {
  const [released, setReleased] = useState(false)
  const [, setTick] = useState(0)
  useEffect(() => {
    const timer = setInterval(() => {
      setTick((tick) => tick + 1)
    }, 100)
    return () => {
      clearInterval(timer)
    }
  }, [])
  const locked = useRetryAfterLock(released ? null : error, () => {
    setReleased(true)
  })
  return <p>{locked ? 'verrouillé' : 'libre'}</p>
}

describe('shared/lib : crochets sans métier', () => {
  it('AUTH-05 : la valeur suit la saisie après un délai, une fois la frappe arrêtée', async () => {
    const screen = await render(<Debounced />)

    await screen.getByLabelText('saisie').fill('valentin')
    expect(screen.container.querySelector('output')?.textContent).toBe('')
    await expect.poll(() => screen.container.querySelector('output')?.textContent).toBe('valentin')
  })

  it('SEC-10 : un 429 verrouille jusqu’à la fin de Retry-After, puis libère', async () => {
    const screen = await render(
      <Locked error={new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 1 })} />,
    )

    await expect.element(screen.getByText('verrouillé')).toBeVisible()
    await expect.element(screen.getByText('libre'), { timeout: 3000 }).toBeVisible()
  })

  it('SEC-10 : toute autre erreur ne verrouille rien', async () => {
    const screen = await render(<Locked error={new ApiError(401, 'INVALID_CREDENTIALS')} />)

    await expect.element(screen.getByText('libre')).toBeVisible()
  })

  it('SEC-10 : le verrou se lève à la fin de Retry-After, même si l’écran refait son rendu entre-temps', async () => {
    const screen = await render(
      <TickingLocked error={new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 1 })} />,
    )

    await expect.element(screen.getByText('verrouillé')).toBeVisible()
    await expect.element(screen.getByText('libre'), { timeout: 3000 }).toBeVisible()
  })

  it('SEC-10 : un Retry-After au-delà de la limite de setTimeout garde le verrou', async () => {
    const screen = await render(
      <Locked error={new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 3_000_000 })} />,
    )

    await new Promise((resolve) => setTimeout(resolve, 300))
    await expect.element(screen.getByText('verrouillé')).toBeVisible()
  })

  it('ADR 0033 (point 6) : useSubmitGuard ne laisse partir qu’un envoi, et rend le verrou quand il se règle', async () => {
    let guard: SubmitGuard | undefined
    function Probe({ onReady }: { readonly onReady: (ready: SubmitGuard) => void }) {
      const submitGuard = useSubmitGuard()
      useEffect(() => {
        onReady(submitGuard)
      }, [onReady, submitGuard])
      return null
    }
    await render(
      <Probe
        onReady={(ready) => {
          guard = ready
        }}
      />,
    )

    let finish: (ok: boolean) => void = () => undefined
    const pending = () =>
      new Promise<void>((resolve, reject) => {
        finish = (ok) => {
          if (ok) {
            resolve()
          } else {
            reject(new Error('refus'))
          }
        }
      })
    const sent: string[] = []
    const send = (label: string) => () => {
      sent.push(label)
      return pending()
    }

    // Un envoi à la fois : le second est ignoré tant que le premier n'est pas réglé.
    expect(guard?.run(send('premier'))).toBe(true)
    expect(guard?.run(send('second'))).toBe(false)
    expect(sent).toEqual(['premier'])

    // Rendu après une réussite, puis après un échec, sans rejet laissé sans traitement.
    finish(true)
    await expect.poll(() => guard?.run(send('troisième'))).toBe(true)
    finish(false)
    await expect.poll(() => guard?.run(send('quatrième'))).toBe(true)
    expect(sent).toEqual(['premier', 'troisième', 'quatrième'])
  })

  it('ADR 0033 (point 6) : un envoi qui lève avant de partir rend le verrou et propage l’erreur', async () => {
    let guard: SubmitGuard | undefined
    function Probe({ onReady }: { readonly onReady: (ready: SubmitGuard) => void }) {
      const submitGuard = useSubmitGuard()
      useEffect(() => {
        onReady(submitGuard)
      }, [onReady, submitGuard])
      return null
    }
    await render(
      <Probe
        onReady={(ready) => {
          guard = ready
        }}
      />,
    )

    expect(() =>
      guard?.run(() => {
        throw new TypeError('avant l’envoi')
      }),
    ).toThrow('avant l’envoi')
    expect(guard?.run(() => Promise.resolve())).toBe(true)
  })
})
