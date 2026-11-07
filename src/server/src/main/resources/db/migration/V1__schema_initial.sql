-- Schéma initial de SEAL : section 13 du cahier des charges, reprise telle quelle (DATA-01).
-- La base ne contient aucun texte de message : enveloppes chiffrées, clés publiques, secrets hachés
-- ou chiffrés et métadonnées seulement (DATA-04).

CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE users (
  id                uuid PRIMARY KEY DEFAULT uuidv7(),
  username          text NOT NULL UNIQUE CHECK (char_length(username) BETWEEN 3 AND 20
                    AND username ~ '^[a-z0-9]+([._][a-z0-9]+)*$'),
  display_name      text NOT NULL CHECK (char_length(display_name) BETWEEN 1 AND 50),
  email             citext UNIQUE,              -- NULL après suppression du compte
  email_verified_at timestamptz,
  password_hash     text,                       -- NULL pour un compte uniquement OAuth
  totp_secret_enc   bytea,                      -- AES-256-GCM avec SEAL_DATA_KEY
  totp_enabled_at   timestamptz,
  totp_last_step    bigint,                     -- anti-rejeu (AUTH-42)
  status            text NOT NULL DEFAULT 'PENDING_EMAIL'
                    CHECK (status IN ('PENDING_EMAIL', 'ACTIVE', 'DELETED')),
  created_at        timestamptz NOT NULL DEFAULT now(),
  deleted_at        timestamptz
);

-- AUTH-03 : identifiant définitif, même si le code applicatif se trompe
CREATE FUNCTION forbid_username_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.username IS DISTINCT FROM OLD.username THEN
    RAISE EXCEPTION 'username is immutable';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER users_username_immutable BEFORE UPDATE OF username ON users
  FOR EACH ROW EXECUTE FUNCTION forbid_username_update();

CREATE TABLE oauth_identities (
  id         uuid PRIMARY KEY DEFAULT uuidv7(),
  user_id    uuid NOT NULL REFERENCES users ON DELETE CASCADE,
  provider   text NOT NULL CHECK (provider IN ('GOOGLE', 'MICROSOFT', 'GITHUB')),
  subject    text NOT NULL,                     -- sub, tid:oid ou id GitHub
  email_seen citext,
  linked_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (provider, subject),
  UNIQUE (user_id, provider)
);

CREATE TABLE one_time_codes (
  id          uuid PRIMARY KEY DEFAULT uuidv7(),
  user_id     uuid NOT NULL REFERENCES users ON DELETE CASCADE,
  purpose     text NOT NULL CHECK (purpose IN
              ('VERIFY_EMAIL', 'CHANGE_EMAIL', 'RESET_PASSWORD', 'KEY_CHALLENGE')),
  code_hash   bytea NOT NULL,                   -- SHA-256, jamais le code
  target      citext,                           -- nouveau courriel pour CHANGE_EMAIL
  attempts    int NOT NULL DEFAULT 0,
  expires_at  timestamptz NOT NULL,
  consumed_at timestamptz
);

CREATE TABLE recovery_codes (
  user_id   uuid REFERENCES users ON DELETE CASCADE,
  code_hash bytea,
  used_at   timestamptz,
  PRIMARY KEY (user_id, code_hash)
);

CREATE TABLE identity_certificates (
  serial            bytea PRIMARY KEY,          -- 16 octets
  user_id           uuid NOT NULL REFERENCES users,
  ik_public         bytea NOT NULL,             -- 32 octets
  not_before        timestamptz NOT NULL,
  not_after         timestamptz NOT NULL,
  cert_bytes        bytea NOT NULL,             -- SealCertificate encodé
  signature         bytea NOT NULL,
  revoked_at        timestamptz,
  revocation_reason smallint
);
CREATE UNIQUE INDEX one_active_certificate ON identity_certificates (user_id)
  WHERE revoked_at IS NULL;

CREATE TABLE subkeys (
  ek_id             bytea PRIMARY KEY,          -- 16 octets
  user_id           uuid NOT NULL REFERENCES users,
  cert_serial       bytea NOT NULL REFERENCES identity_certificates,
  kem               text NOT NULL,
  ek_public         bytea NOT NULL,
  created_at        timestamptz NOT NULL,
  expires_at        timestamptz NOT NULL,
  binding_signature bytea NOT NULL
);

CREATE TABLE key_vaults (
  user_id     uuid PRIMARY KEY REFERENCES users ON DELETE CASCADE,
  version     bigint NOT NULL,
  kek_salt    bytea NOT NULL,
  vdk_nonce   bytea NOT NULL,
  wrapped_vdk bytea NOT NULL,
  blob_nonce  bytea NOT NULL,
  blob        bytea NOT NULL,                   -- opaque pour le serveur
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE friend_requests (
  id           uuid PRIMARY KEY DEFAULT uuidv7(),
  sender_id    uuid NOT NULL REFERENCES users ON DELETE CASCADE,
  receiver_id  uuid NOT NULL REFERENCES users ON DELETE CASCADE,
  status       text NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED')),
  created_at   timestamptz NOT NULL DEFAULT now(),
  responded_at timestamptz,
  CHECK (sender_id <> receiver_id)
);
-- FRD-01 : une seule invitation en attente par paire, dans les deux sens
CREATE UNIQUE INDEX one_pending_request_per_pair ON friend_requests
  (LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id))
  WHERE status = 'PENDING';

CREATE TABLE friendships (
  user_low   uuid REFERENCES users ON DELETE CASCADE,
  user_high  uuid REFERENCES users ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_low, user_high),
  CHECK (user_low < user_high)
);

CREATE TABLE blocks (
  blocker_id uuid REFERENCES users ON DELETE CASCADE,
  blocked_id uuid REFERENCES users ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (blocker_id, blocked_id)
);

CREATE TABLE conversations (
  id              uuid PRIMARY KEY DEFAULT uuidv7(),
  type            text NOT NULL CHECK (type IN ('DIRECT', 'GROUP')),
  name            text CHECK (char_length(name) BETWEEN 1 AND 64),
  direct_key      text UNIQUE,                  -- "uuidMin:uuidMax" pour DIRECT (CNV-01)
  created_by      uuid NOT NULL REFERENCES users,
  created_at      timestamptz NOT NULL DEFAULT now(),
  last_server_seq bigint NOT NULL DEFAULT 0,
  CHECK ((type = 'DIRECT') = (direct_key IS NOT NULL))
);

CREATE TABLE conversation_members (
  conversation_id uuid REFERENCES conversations ON DELETE CASCADE,
  user_id         uuid REFERENCES users,
  role            text NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('ADMIN', 'MEMBER')),
  joined_at       timestamptz NOT NULL DEFAULT now(),
  joined_seq      bigint NOT NULL,              -- serverSeq à l'arrivée
  left_at         timestamptz,
  left_seq        bigint,                       -- CNV-07
  last_read_seq   bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE group_ops (
  conversation_id uuid REFERENCES conversations ON DELETE CASCADE,
  seq             bigint,
  op_bytes        bytea NOT NULL,               -- GroupOp encodé
  op_hash         bytea NOT NULL,
  signature       bytea NOT NULL,
  actor_id        uuid NOT NULL REFERENCES users,
  created_at      timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (conversation_id, seq)
);

CREATE TABLE messages (
  id                 uuid PRIMARY KEY,          -- messageId du client, UUIDv7
  conversation_id    uuid NOT NULL REFERENCES conversations ON DELETE CASCADE,
  server_seq         bigint NOT NULL,
  sender_id          uuid NOT NULL REFERENCES users,
  sender_cert_serial bytea NOT NULL REFERENCES identity_certificates,
  envelope           jsonb NOT NULL,            -- enveloppe complète, opaque
  envelope_hash      bytea NOT NULL,
  server_received_at timestamptz NOT NULL DEFAULT now(),
  timestamp_token    bytea,                     -- NR-03
  UNIQUE (conversation_id, server_seq)
);

CREATE TABLE receipts (
  message_id    uuid REFERENCES messages ON DELETE CASCADE,
  recipient_id  uuid REFERENCES users,
  kind          smallint CHECK (kind IN (1, 2)),
  receipt_bytes bytea NOT NULL,
  signature     bytea NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (message_id, recipient_id, kind)
);

CREATE TABLE user_settings (
  user_id           uuid PRIMARY KEY REFERENCES users ON DELETE CASCADE,
  preferences       jsonb NOT NULL DEFAULT '{}',
  invitation_policy text NOT NULL DEFAULT 'EVERYONE'
                    CHECK (invitation_policy IN ('EVERYONE', 'NOBODY')),
  read_receipts     boolean NOT NULL DEFAULT true,
  typing_indicators boolean NOT NULL DEFAULT true
);

CREATE TABLE security_events (
  id         uuid PRIMARY KEY DEFAULT uuidv7(),
  user_id    uuid REFERENCES users ON DELETE SET NULL,
  type       text NOT NULL,
  ip         inet,
  user_agent text,
  details    jsonb NOT NULL DEFAULT '{}',
  created_at timestamptz NOT NULL DEFAULT now()
);

-- Tables de Spring Session JDBC (AUTH-50), copiées de schema-postgresql.sql de spring-session-jdbc 4.1.1.
CREATE TABLE SPRING_SESSION (
  PRIMARY_ID CHAR(36) NOT NULL,
  SESSION_ID CHAR(36) NOT NULL,
  CREATION_TIME BIGINT NOT NULL,
  LAST_ACCESS_TIME BIGINT NOT NULL,
  MAX_INACTIVE_INTERVAL INT NOT NULL,
  EXPIRY_TIME BIGINT NOT NULL,
  PRINCIPAL_NAME VARCHAR(100),
  CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
  SESSION_PRIMARY_ID CHAR(36) NOT NULL,
  ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
  ATTRIBUTE_BYTES BYTEA NOT NULL,
  CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
  CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
