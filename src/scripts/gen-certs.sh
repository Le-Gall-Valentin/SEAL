#!/usr/bin/env bash
# Prépare SEAL pour un lancement local (section 17 du CDC, ADR 0004) :
#   1. installe l'autorité locale de mkcert et crée un certificat pour localhost, 127.0.0.1 et ::1 ;
#   2. crée .env à partir de .env.example s'il n'existe pas, puis remplit les secrets vides et ajoute ceux qui
#      manquent à un .env plus ancien, sans jamais toucher une valeur présente (ADR 0026, point 9).
# Testé sous Linux ; non testé sous macOS. Sous Windows : scripts\gen-certs.ps1 (non testé).
# Usage, depuis src/ : ./scripts/gen-certs.sh
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v mkcert >/dev/null 2>&1; then
  echo "mkcert est introuvable : https://github.com/FiloSottile/mkcert#installation" >&2
  exit 1
fi

# Sous Linux, Chrome et Chromium ne lisent pas le magasin du système mais leur base NSS
# (~/.pki/nssdb), créée à leur premier lancement : sans elle, mkcert ne peut pas y installer
# l'autorité et le navigateur refuse le certificat. On la crée si elle manque (ADR 0004).
if [ "$(uname -s)" = Linux ] && [ ! -d "$HOME/.pki/nssdb" ]; then
  if command -v certutil >/dev/null 2>&1; then
    mkdir -p "$HOME/.pki/nssdb"
    certutil -d "sql:$HOME/.pki/nssdb" -N --empty-password
    echo "Base de certificats de Chrome créée : ~/.pki/nssdb"
  else
    echo "Attention : certutil est introuvable (paquet libnss3-tools) ; Chrome et Chromium ne reconnaîtront pas" >&2
    echo "le certificat. Installez-le, puis relancez ce script." >&2
  fi
fi

mkdir -p certs
mkcert -install
mkcert -cert-file certs/localhost.pem -key-file certs/localhost-key.pem localhost 127.0.0.1 ::1
# Le conteneur lit la clé sous un autre utilisateur (UID 10001) : elle doit être lisible par tous.
# Elle ne protège que ce certificat localhost ; la clé de l'autorité mkcert reste dans son dossier privé.
chmod 644 certs/localhost-key.pem

# SEC-22 (ADR 0014) : .env n'est jamais lisible par un autre compte, pas même un instant.
if [ ! -f .env ]; then
  (umask 077 && cp .env.example .env)
  echo ".env créé à partir de .env.example"
fi
chmod 600 .env

# Remplit NOM= s'il est vide, ou l'ajoute à un .env plus ancien qui ne l'a pas encore ; ne touche jamais une
# valeur déjà présente. La valeur ne passe en argument d'aucun autre processus, que tout compte local pourrait lire
# dans /proc/<pid>/cmdline : seuls des commandes internes du shell la manipulent (ADR 0026, point 9).
fill_secret() {
  local name=$1 value=$2 line content=""
  if ! grep -q "^${name}=" .env; then
    # Une dernière ligne sans saut de ligne recevrait la variable collée à elle.
    if [ -s .env ] && [ -n "$(tail -c 1 .env)" ]; then
      echo >>.env
    fi
    printf '%s=%s\n' "$name" "$value" >>.env
    echo "${name} ajouté"
  elif grep -q "^${name}=\$" .env; then
    while IFS= read -r line || [ -n "$line" ]; do
      if [ "$line" = "${name}=" ]; then
        line="${name}=${value}"
      fi
      content+="${line}"$'\n'
    done <.env
    # Réécrit le fichier en place : il garde ses droits (600).
    printf '%s' "$content" >.env
    echo "${name} généré"
  fi
}

random_base64() {
  head -c "$1" /dev/urandom | base64 | tr -d '\n'
}

# SEC-22 : 32 octets aléatoires en base64.
fill_secret SEAL_DATA_KEY "$(random_base64 32)"
# AUTH-13, ADR 0026 : poivre des mots de passe, 32 octets aléatoires en base64.
fill_secret SEAL_PASSWORD_PEPPER "$(random_base64 32)"
# Sans + ni / ni =, pour rester sûr dans une URL JDBC ou une ligne de commande.
fill_secret SEAL_DB_PASSWORD "$(random_base64 32 | tr -d '+/=')"

echo "Prêt : lancez docker compose up --build, puis ouvrez https://localhost:8443"
