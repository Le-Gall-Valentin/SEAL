-- ADR 0025, point 4 : au plus un certificat non révoqué par compte. Deux publications concurrentes se départagent
-- par INSERT ... ON CONFLICT (user_id) WHERE revoked_at IS NULL DO NOTHING ; un renouvellement révoque d'abord
-- l'ancien certificat, motif « remplacé » (code 4 de X.509).
CREATE UNIQUE INDEX identity_certificates_one_unrevoked ON identity_certificates (user_id) WHERE revoked_at IS NULL;
