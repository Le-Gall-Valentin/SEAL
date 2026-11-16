-- ADR 0025, point 8 : un seul défi vivant par compte. Un nouveau défi remplace le précédent par
-- INSERT ... ON CONFLICT (user_id) DO UPDATE, ce qui borne la table sans limite de débit. Avant l'index unique,
-- seul le défi le plus récent de chaque compte est gardé (les identifiants UUIDv7 suivent l'ordre de création).
DELETE FROM key_challenges older USING key_challenges newer
  WHERE older.user_id = newer.user_id AND older.id < newer.id;
DROP INDEX key_challenges_user_id;
CREATE UNIQUE INDEX key_challenges_user_id ON key_challenges (user_id);
