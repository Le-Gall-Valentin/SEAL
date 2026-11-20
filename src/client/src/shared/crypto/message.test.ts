import { readFileSync } from 'node:fs'
import { describeEnvelopeVectors } from './testing/envelopeVectorSuite'
import { messageParties } from './testing/messageParties'
import { describeMessageSeal } from './testing/messageSealSuite'
import { record } from './testing/vectorReader'

// QA-02 : envelope.json rejoué sous Node (OpenSSL). L'émetteur ouvre son trousseau dans le coffre de vault.json ; les
// certificats sont signés par l'Autorité de identity.json.
const VECTORS = new URL('../../../../protocol/vectors/', import.meta.url)
const read = (file: string) => record(JSON.parse(readFileSync(new URL(file, VECTORS), 'utf8')))
const envelope = read('envelope.json')
const parties = await messageParties(envelope, read('vault.json'), read('identity.json'))

describeEnvelopeVectors(envelope, 'Node')
describeMessageSeal(envelope, parties, 'Node')
