import { afterEach, describe, expect, it, vi } from 'vitest'
import { downloadText } from './downloadText'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('KEY-30 : téléchargement d’un texte (.txt)', () => {
  it('KEY-30 : le texte part dans un fichier du nom donné, et son lien blob est révoqué juste après', async () => {
    const created: Blob[] = []
    const revoked: string[] = []
    vi.spyOn(URL, 'createObjectURL').mockImplementation((blob) => {
      if (blob instanceof Blob) {
        created.push(blob)
      }
      return 'blob:seal-test'
    })
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation((url) => {
      revoked.push(url)
    })
    const clicked: { download: string; href: string }[] = []
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      clicked.push({ download: this.download, href: this.href })
    })

    downloadText('seal-cle.txt', 'SEAL\nligne')

    expect(clicked).toEqual([{ download: 'seal-cle.txt', href: 'blob:seal-test' }])
    await expect.poll(() => revoked).toEqual(['blob:seal-test'])
    await expect(created[0]?.text()).resolves.toBe('SEAL\nligne')
    expect(created[0]?.type).toBe('text/plain;charset=utf-8')
    expect(document.querySelector('a[download]')).toBeNull()
  })
})
