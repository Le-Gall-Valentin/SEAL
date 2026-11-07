#!/usr/bin/env bash
# Produit projet.zip à la racine du dépôt (OPS-02, ADR 0004). L'archive contient exactement :
#   chat/README.md, chat/presentation.pdf et chat/src/ (fichiers suivis par Git, plus src/docs/,
#   gardé hors de Git et pris tel quel sur le disque).
# node_modules, target, certs, .env et data n'y entrent jamais : ils ne sont pas suivis par Git,
# et l'archive est vérifiée avant d'être livrée.
# Testé sous Linux ; non testé sous macOS. Sous Windows : scripts\package.ps1 (non testé).
# Usage, depuis src/ : ./scripts/package.sh
set -euo pipefail

root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"

fail() {
  echo "package.sh : $*" >&2
  exit 1
}

command -v git >/dev/null 2>&1 || fail "git est introuvable"
command -v zip >/dev/null 2>&1 || fail "zip est introuvable"
command -v unzip >/dev/null 2>&1 || fail "unzip est introuvable"
[ -f README.md ] || fail "README.md manquant à la racine du dépôt"
[ -f presentation.pdf ] || fail "presentation.pdf manquant à la racine du dépôt (export PDF de la présentation)"

if [ -n "$(git status --porcelain --untracked-files=all -- src README.md)" ]; then
  echo "Attention : des fichiers de src/ ou README.md ne sont pas commités. Les fichiers suivis sont" >&2
  echo "archivés dans leur état actuel ; les fichiers non suivis (hors src/docs/) sont ignorés." >&2
fi

stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
mkdir -p "$stage/chat"

copy_into_stage() {
  local file
  while IFS= read -r -d '' file; do
    [ -f "$file" ] || continue # suivi mais supprimé du disque
    mkdir -p "$stage/chat/$(dirname "$file")"
    cp -p "$file" "$stage/chat/$file"
  done
}

git ls-files -z -- src | copy_into_stage
if [ -d src/docs ]; then
  find src/docs -type f -print0 | copy_into_stage
fi
cp -p README.md presentation.pdf "$stage/chat/"

rm -f projet.zip
# -D : aucune entrée de dossier, seulement des fichiers.
(cd "$stage" && zip -q -r -X -D "$root/projet.zip" chat)

# Vérification de l'archive avant livraison.
entries=$(unzip -Z1 projet.zip)
unexpected=$(printf '%s\n' "$entries" | grep -Ev '^chat/(README\.md|presentation\.pdf|src/.*)$' || true)
[ -z "$unexpected" ] || fail "entrées inattendues dans l'archive : $unexpected"
forbidden=$(printf '%s\n' "$entries" \
  | grep -E '(^|/)(node_modules|target)/|^chat/src/(certs|data)/|(^|/)\.env$' || true)
[ -z "$forbidden" ] || fail "fichiers interdits dans l'archive : $forbidden"
for required in chat/README.md chat/presentation.pdf chat/src/client/package.json chat/src/server/pom.xml; do
  printf '%s\n' "$entries" | grep -qx "$required" || fail "$required manque dans l'archive"
done

echo "projet.zip créé : $(printf '%s\n' "$entries" | wc -l | tr -d ' ') fichiers sous chat/"
