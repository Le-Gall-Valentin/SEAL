import { readFileSync } from 'node:fs'
import { describeEnvelopeVectors } from './testing/envelopeVectorSuite'
import { describeMessageSeal } from './testing/messageSealSuite'
import { record } from './testing/vectorReader'

// QA-02 : envelope.json rejoué sous Node (OpenSSL) ; l'émetteur ouvre son trousseau dans le coffre de vault.json.
const VECTORS = new URL('../../../../protocol/vectors/', import.meta.url)
const envelope = record(JSON.parse(readFileSync(new URL('envelope.json', VECTORS), 'utf8')))
const vault = record(JSON.parse(readFileSync(new URL('vault.json', VECTORS), 'utf8')))

describeEnvelopeVectors(envelope, 'Node')
describeMessageSeal(envelope, vault, 'Node')
