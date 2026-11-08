-- Tables par contexte (ADR 0009) : chaque contexte du serveur possède ses tables, comme le décrit la section 13 du
-- cahier des charges. Les secrets quittent users, one_time_codes est scindée selon ses usages, et chaque clé
-- étrangère reçoit l'index qui lui manquait. Les données éventuelles sont recopiées avant toute suppression.

-- identity
CREATE TABLE email_codes (
  id          uuid PRIMARY KEY DEFAULT uuidv7(),
  user_id     uuid NOT NULL REFERENCES users ON DELETE CASCADE,
  purpose     text NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'CHANGE_EMAIL')),
  code_hash   bytea NOT NULL,                   -- SHA-256, jamais le code
  target      citext,                           -- nouveau courriel pour CHANGE_EMAIL
  attempts    int NOT NULL DEFAULT 0,
  expires_at  timestamptz NOT NULL,
  consumed_at timestamptz
);

-- authentication
CREATE TABLE user_credentials (                 -- aucune ligne pour un compte uniquement OAuth
  user_id       uuid PRIMARY KEY REFERENCES users ON DELETE CASCADE,
  password_hash text NOT NULL
);

CREATE TABLE password_reset_tokens (
  id          uuid PRIMARY KEY DEFAULT uuidv7(),
  user_id     uuid NOT NULL REFERENCES users ON DELETE CASCADE,
  token_hash  bytea NOT NULL UNIQUE,            -- SHA-256 du jeton de 256 bits
  expires_at  timestamptz NOT NULL,
  consumed_at timestamptz
);

-- mfa
CREATE TABLE totp_factors (
  user_id     uuid PRIMARY KEY REFERENCES users ON DELETE CASCADE,
  secret_enc  bytea NOT NULL,                   -- AES-256-GCM avec SEAL_DATA_KEY
  enabled_at  timestamptz,                      -- NULL tant que l'activation n'est pas confirmée
  last_step   bigint                            -- anti-rejeu (AUTH-42)
);

-- keys
CREATE TABLE key_challenges (
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  user_id        uuid NOT NULL REFERENCES users ON DELETE CASCADE,
  challenge_hash bytea NOT NULL,                -- SHA-256 du défi de 32 octets (KEY-03)
  expires_at     timestamptz NOT NULL,
  consumed_at    timestamptz
);

-- Recopie des données de la V1.
INSERT INTO user_credentials (user_id, password_hash)
  SELECT id, password_hash FROM users WHERE password_hash IS NOT NULL;

INSERT INTO totp_factors (user_id, secret_enc, enabled_at, last_step)
  SELECT id, totp_secret_enc, totp_enabled_at, totp_last_step FROM users WHERE totp_secret_enc IS NOT NULL;

INSERT INTO email_codes (id, user_id, purpose, code_hash, target, attempts, expires_at, consumed_at)
  SELECT id, user_id, purpose, code_hash, target, attempts, expires_at, consumed_at
  FROM one_time_codes WHERE purpose IN ('VERIFY_EMAIL', 'CHANGE_EMAIL');

INSERT INTO password_reset_tokens (id, user_id, token_hash, expires_at, consumed_at)
  SELECT id, user_id, code_hash, expires_at, consumed_at FROM one_time_codes WHERE purpose = 'RESET_PASSWORD';

INSERT INTO key_challenges (id, user_id, challenge_hash, expires_at, consumed_at)
  SELECT id, user_id, code_hash, expires_at, consumed_at FROM one_time_codes WHERE purpose = 'KEY_CHALLENGE';

DROP TABLE one_time_codes;
ALTER TABLE users
  DROP COLUMN password_hash,
  DROP COLUMN totp_secret_enc,
  DROP COLUMN totp_enabled_at,
  DROP COLUMN totp_last_step;

-- AUTH-15 : seul un compte actif se réserve une adresse ; plusieurs comptes en attente peuvent la partager
-- (ADR 0019). citext rend la comparaison insensible à la casse.
ALTER TABLE users DROP CONSTRAINT users_email_key;
CREATE UNIQUE INDEX users_verified_email ON users (email) WHERE status = 'ACTIVE';

-- Index des clés étrangères (ADR 0009) : sans eux, supprimer un parent parcourt toute la table enfant.
CREATE INDEX email_codes_user_id ON email_codes (user_id);
CREATE INDEX password_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX key_challenges_user_id ON key_challenges (user_id);
CREATE INDEX identity_certificates_user_id ON identity_certificates (user_id);
CREATE INDEX subkeys_user_id ON subkeys (user_id);
CREATE INDEX subkeys_cert_serial ON subkeys (cert_serial);
CREATE INDEX friend_requests_sender_id ON friend_requests (sender_id);
CREATE INDEX friend_requests_receiver_id ON friend_requests (receiver_id);
CREATE INDEX friendships_user_high ON friendships (user_high);
CREATE INDEX blocks_blocked_id ON blocks (blocked_id);
CREATE INDEX conversations_created_by ON conversations (created_by);
CREATE INDEX conversation_members_user_id ON conversation_members (user_id);
CREATE INDEX group_ops_actor_id ON group_ops (actor_id);
CREATE INDEX messages_sender_id ON messages (sender_id);
CREATE INDEX messages_sender_cert_serial ON messages (sender_cert_serial);
CREATE INDEX receipts_recipient_id ON receipts (recipient_id);
CREATE INDEX security_events_user_id ON security_events (user_id);
