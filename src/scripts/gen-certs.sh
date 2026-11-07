#!/usr/bin/env bash
# Prépare SEAL pour un lancement local (section 17 du CDC, ADR 0004) :
#   1. installe l'autorité locale de mkcert et crée un certificat pour localhost, 127.0.0.1 et ::1 ;
#   2. crée .env à partir de .env.example s'il n'existe pas, puis remplit les secrets vides.
# Testé sous Linux ; non testé sous macOS. Sous Windows : scripts\gen-certs.ps1 (non testé).
# Usage, depuis src/ : ./scripts/gen-certs.sh
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v mkcert >/dev/null 2>&1; then
  echo "mkcert est introuvable : https://github.com/FiloSottile/mkcert#installation" >&2
  exit 1
fi

mkdir -p certs
mkcert -install
mkcert -cert-file certs/localhost.pem -key-file certs/localhost-key.pem localhost 127.0.0.1 ::1
# Le conteneur lit la clé sous un autre utilisateur (UID 10001) : elle doit être lisible par tous.
# Elle ne protège que ce certificat localhost ; la clé de l'autorité mkcert reste dans son dossier privé.
chmod 644 certs/localhost-key.pem

if [ ! -f .env ]; then
  cp .env.example .env
  echo ".env créé à partir de .env.example"
fi

# Remplit NOM= s'il est vide ; ne touche jamais une valeur déjà présente.
fill_secret() {
  local name=$1 value=$2 tmp
  if grep -q "^${name}=\$" .env; then
    tmp=$(mktemp)
    sed "s|^${name}=\$|${name}=${value}|" .env >"$tmp"
    cat "$tmp" >.env
    rm -f "$tmp"
    echo "${name} généré"
  fi
}

random_base64() {
  head -c "$1" /dev/urandom | base64 | tr -d '\n'
}

# SEC-22 : 32 octets aléatoires en base64.
fill_secret SEAL_DATA_KEY "$(random_base64 32)"
# Sans + ni / ni =, pour rester sûr dans une URL JDBC ou une ligne de commande.
fill_secret SEAL_DB_PASSWORD "$(random_base64 32 | tr -d '+/=')"
chmod 600 .env

echo "Prêt : lancez docker compose up --build, puis ouvrez https://localhost:8443"
