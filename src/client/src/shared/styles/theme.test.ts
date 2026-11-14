import { readFileSync } from 'node:fs'
import { readFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { compile } from 'tailwindcss'
import { describe, expect, it } from 'vitest'

// Le thème compilé par Tailwind lui-même, comme au build : les jetons et le thème de shared/styles.
const STYLES = fileURLToPath(new URL('.', import.meta.url))
const TAILWIND = fileURLToPath(
  new URL('../../../node_modules/tailwindcss/index.css', import.meta.url),
)
const ENTRY = "@import 'tailwindcss';\n@import './tokens.css';\n@import './theme.css';\n"

async function loadStylesheet(id: string, base: string) {
  const path = id === 'tailwindcss' ? TAILWIND : resolve(base, id)
  return { path, base: dirname(path), content: await readFile(path, 'utf-8') }
}

async function rule(candidate: string): Promise<string | undefined> {
  const compiler = await compile(ENTRY, { base: STYLES, loadStylesheet })
  const css = compiler.build([candidate])
  const selector = `.${candidate.replaceAll('.', '\\.')} {`
  const start = css.indexOf(selector)
  return start === -1 ? undefined : css.slice(start, css.indexOf('}', start) + 1)
}

const TOKENS_CSS = readFileSync(resolve(STYLES, 'tokens.css'), 'utf-8')

function names(css: string): string[] {
  return [...css.matchAll(/--([a-z0-9-]+):/g)].map(([, name]) => name ?? '')
}

const TOKENS = [...new Set(names(TOKENS_CSS))]

describe('ADR 0016 : thème Tailwind sur les jetons de la maquette', () => {
  it('ADR 0016 : chaque couleur de tokens.css a ses classes, et chaque police la sienne', async () => {
    const colors = TOKENS.filter((name) => name !== 'sans' && name !== 'mono')
    expect(colors.length).toBeGreaterThan(0)
    for (const name of colors) {
      expect(await rule(`bg-${name}`), name).toContain(`var(--${name})`)
    }
    expect(await rule('font-sans')).toContain('var(--sans)')
    expect(await rule('font-mono')).toContain('var(--mono)')
  })

  it("ADR 0016 : aucune couleur, ombre ni police par défaut de Tailwind n'a de classe", async () => {
    for (const candidate of [
      'bg-red-500',
      'text-white',
      'shadow-lg',
      'inset-shadow-sm',
      'drop-shadow-xl',
      'text-shadow-lg',
      'font-serif',
    ]) {
      expect(await rule(candidate), candidate).toBeUndefined()
    }
  })

  it('ADR 0016 : espacements et rayons en px, sans échelle de texte ni de conteneur en rem', async () => {
    expect(await rule('p-4')).toContain('4px')
    expect(await rule('rounded-xl')).toContain('12px')
    expect(await rule('rounded-lg')).toContain('8px')
    expect(await rule('text-lg')).toBeUndefined()
    expect(await rule('max-w-md')).toBeUndefined()
  })
})

describe('ADR 0024 : thème sombre de la maquette', () => {
  it('ADR 0024 : chaque couleur du thème clair a sa valeur sombre, sous prefers-color-scheme', () => {
    const [light = '', dark = ''] = TOKENS_CSS.split('@media (prefers-color-scheme: dark)')
    const colors = names(light).filter((name) => name !== 'sans' && name !== 'mono')

    expect(colors.length).toBeGreaterThan(0)
    expect(names(dark).toSorted()).toEqual(colors.toSorted())
  })
})
