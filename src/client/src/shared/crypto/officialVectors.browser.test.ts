import { commands } from 'vitest/browser'
import { describeOfficialVectors } from './testing/officialVectorSuite'
import { record } from './testing/vectorReader'

// QA-01 : les mêmes vecteurs dans Chromium, celui du client ; chemins relatifs à la racine du client (src/client).
async function read(name: string) {
  return record(JSON.parse(await commands.readFile(`../protocol/vectors/${name}`)))
}

describeOfficialVectors(
  await read('hkdf-rfc5869.json'),
  await read('x25519-rfc7748.json'),
  'Chromium',
)
