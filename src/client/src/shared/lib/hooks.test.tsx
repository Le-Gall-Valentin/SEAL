import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { ApiError } from './apiErrors'
import { useDebouncedValue } from './useDebouncedValue'
import { useRetryAfterLock } from './useRetryAfterLock'

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
})
