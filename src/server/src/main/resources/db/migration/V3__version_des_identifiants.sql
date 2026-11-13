-- Version du mot de passe (ADR 0023) : incrémentée à chaque remplacement du haché, et gravée dans la session à la
-- connexion. Une session ouverte avec une version périmée tombe, même si elle a échappé à la révocation (AUTH-70).
ALTER TABLE user_credentials
  ADD COLUMN version bigint NOT NULL DEFAULT 1;
