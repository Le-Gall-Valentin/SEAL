import { readFileSync } from 'node:fs'
import { describeOfficialVectors } from './testing/officialVectorSuite'
import { record } from './testing/vectorReader'

// QA-01 : vecteurs produits par src/protocol/tools/gen_vectors.py, relus aussi par les tests Java.
const VECTORS = new URL('../../../../protocol/vectors/', import.meta.url)

function read(name: string) {
  return record(JSON.parse(readFileSync(new URL(name, VECTORS), 'utf8')))
}

describeOfficialVectors(read('hkdf-rfc5869.json'), read('x25519-rfc7748.json'), 'Node')
