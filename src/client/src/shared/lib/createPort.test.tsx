import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { createPort } from './createPort'

interface Greeter {
  greet(): string
}

const port = createPort<Greeter>('GreeterProvider')

function Greeting() {
  return <p>{port.usePort().greet()}</p>
}

describe('ADR 0024, point 7 : port injecté par un fournisseur', () => {
  it('ADR 0024 : le crochet rend l’implémentation fournie', async () => {
    const screen = await render(
      <port.Provider api={{ greet: () => 'bonjour' }}>
        <Greeting />
      </port.Provider>,
    )

    await expect.element(screen.getByText('bonjour')).toBeVisible()
  })

  it('ADR 0024 : sans fournisseur, le port manquant est nommé', async () => {
    await expect(render(<Greeting />)).rejects.toThrow('GreeterProvider')
  })
})
