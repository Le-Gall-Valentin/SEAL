import { describe, expect, it } from 'vitest'
import { recoveryKeyFile } from './recoveryKeyFile'

const TEXTS = {
  title: 'SEAL — clé de récupération',
  account: 'Compte : valentin.dupont',
  warning: 'Elle seule permet de restaurer vos clés sur un nouvel appareil.',
}
const GROUPS = ['8WQK', '3MZD', 'T7PA', 'R2HX', '5NCF', 'Y9GB', 'K4VE', 'J6TS']

describe('KEY-30 : fichier .txt de la clé de récupération (ADR 0029, point 7)', () => {
  it('KEY-30 : titre, compte, les 8 groupes sur deux lignes de 4, puis l’avertissement', () => {
    expect(recoveryKeyFile('valentin.dupont', GROUPS, TEXTS)).toEqual({
      name: 'seal-cle-de-recuperation-valentin.dupont.txt',
      text: [
        'SEAL — clé de récupération',
        'Compte : valentin.dupont',
        '',
        '8WQK 3MZD T7PA R2HX',
        '5NCF Y9GB K4VE J6TS',
        '',
        'Elle seule permet de restaurer vos clés sur un nouvel appareil.',
        '',
      ].join('\n'),
    })
  })
})
