import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { AVATAR_TINTS } from '@/shared/config'
import { Avatar } from './Avatar'

describe('ADR 0038 (point 7) : avatar à initiales, teintes de la maquette', () => {
  it('FRD-10, ADR 0038 : les initiales sont décoratives, à la taille demandée', async () => {
    const screen = await render(<Avatar initials="SB" tint="heather" size={44} />)
    const avatar = screen.getByText('SB').element()

    expect(avatar.getAttribute('aria-hidden')).toBe('true')
    expect(getComputedStyle(avatar)).toMatchObject({
      width: '44px',
      height: '44px',
      fontSize: '16px',
    })
  })

  it.each([
    [24, '10px'],
    [36, '13px'],
    [40, '13px'],
    [56, '20px'],
    [64, '23px'],
  ] as const)('ADR 0038 : taille %i px, texte de %s', async (size, fontSize) => {
    const screen = await render(<Avatar initials="VD" tint="moss" size={size} />)

    expect(getComputedStyle(screen.getByText('VD').element())).toMatchObject({
      width: `${String(size)}px`,
      fontSize,
    })
  })

  it('FRD-10, ADR 0038 : chacune des six teintes a sa couleur de fond', async () => {
    const colors = new Set<string>()
    for (const tint of AVATAR_TINTS) {
      const screen = await render(<Avatar initials={tint} tint={tint} size={44} />)
      colors.add(getComputedStyle(screen.getByText(tint).element()).backgroundColor)
    }

    expect(colors.size).toBe(6)
  })
})
