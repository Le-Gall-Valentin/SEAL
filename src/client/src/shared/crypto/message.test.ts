import { readFileSync } from 'node:fs'
import { describeEnvelopeVectors } from './testing/envelopeVectorSuite'
import { record } from './testing/vectorReader'

// QA-02 : envelope.json rejoué sous Node (OpenSSL).
const ENVELOPE = new URL('../../../../protocol/vectors/envelope.json', import.meta.url)

describeEnvelopeVectors(record(JSON.parse(readFileSync(ENVELOPE, 'utf8'))), 'Node')
