#!/usr/bin/env python3
"""Produit les vecteurs de référence de SEAL, lus par les tests Java et TypeScript.

- vectors/canonical-encoding.json : encodage canonique (PROTOCOL.md, section 2 ; MSG-02, ADR 0006) ;
- vectors/password-policy.json : politique des mots de passe (PROTOCOL.md, section 3 ; AUTH-11, ADR 0024).

Troisième implémentation, indépendante de Java et de TypeScript : les deux suites de tests relisent ces
vecteurs, aucune ne se valide elle-même. Bibliothèque standard de Python uniquement.

Usage, depuis src/protocol :
    python3 tools/gen_vectors.py           réécrit les fichiers de vectors/
    python3 tools/gen_vectors.py --check   échoue si un fichier diffère (CI)
"""

from __future__ import annotations

import argparse
import base64
import json
import re
import struct as binary
import sys
import unicodedata
from pathlib import Path

PROTOCOL_DIR = Path(__file__).resolve().parent.parent
OUTPUT = PROTOCOL_DIR / "vectors" / "canonical-encoding.json"
PASSWORD_OUTPUT = PROTOCOL_DIR / "vectors" / "password-policy.json"
BLOCKLIST = PROTOCOL_DIR / "password-blocklist.txt"

U32_MAX = 2**32 - 1
U64_MAX = 2**63 - 1  # ADR 0006 : le long de Java est signé
UUID_PATTERN = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")


class Rejected(Exception):
    """Valeur hors du domaine de l'encodage : elle doit être rejetée par toute implémentation."""


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def from_b64url(text: str) -> bytes:
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def is_integer(value: object) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def u8(value: object) -> bytes:
    if not is_integer(value) or not 0 <= value <= 0xFF:
        raise Rejected("u8 hors de 0..255")
    return binary.pack(">B", value)


def u32(value: object) -> bytes:
    if not is_integer(value) or not 0 <= value <= U32_MAX:
        raise Rejected("u32 hors de 0..2^32-1")
    return binary.pack(">I", value)


def u64(value: int) -> bytes:
    if not 0 <= value <= U64_MAX:
        raise Rejected("u64 hors de 0..2^63-1")
    return binary.pack(">Q", value)


def raw_bytes(value: bytes) -> bytes:
    return u32(len(value)) + value


def text(value: str) -> bytes:
    try:
        # encode() échoue sur un demi-caractère isolé : c'est le refus voulu (ADR 0006).
        encoded = unicodedata.normalize("NFC", value).encode("utf-8")
    except UnicodeEncodeError as error:
        raise Rejected("demi-caractère Unicode isolé") from error
    return raw_bytes(encoded)


def uuid(value: str) -> bytes:
    if not UUID_PATTERN.fullmatch(value):
        raise Rejected("UUID hors de la forme 8-4-4-4-12")
    return bytes.fromhex(value.replace("-", ""))


def encode(descriptor: dict) -> bytes:
    kind = descriptor["type"]
    if kind == "u8":
        return u8(descriptor["value"])
    if kind == "u32":
        return u32(descriptor["value"])
    if kind == "u64":
        return u64(int(descriptor["value"]))
    if kind == "bytes":
        return raw_bytes(from_b64url(descriptor["value"]))
    if kind == "text":
        return text(descriptor["value"])
    if kind == "uuid":
        return uuid(descriptor["value"])
    if kind == "list":
        items = descriptor["items"]
        return u32(len(items)) + b"".join(encode(item) for item in items)
    if kind == "concat":
        return b"".join(encode(part) for part in descriptor["parts"])
    if kind == "struct":
        if descriptor["label"] == "":
            raise Rejected("étiquette vide")
        return text(descriptor["label"]) + b"".join(encode(field) for field in descriptor["fields"])
    raise ValueError(f"type inconnu : {kind}")


# ---------------------------------------------------------------------------------------------
# Cas de test. Les valeurs d'exemple sont fixes, jamais aléatoires, pour que le fichier soit stable.
# ---------------------------------------------------------------------------------------------


def U8(value: object) -> dict:
    return {"type": "u8", "value": value}


def U32(value: object) -> dict:
    return {"type": "u32", "value": value}


def U64(value: int) -> dict:
    return {"type": "u64", "value": str(value)}


def BYTES(value: bytes) -> dict:
    return {"type": "bytes", "value": b64url(value)}


def TEXT(value: str) -> dict:
    return {"type": "text", "value": value}


def UUID(value: str) -> dict:
    return {"type": "uuid", "value": value}


def LIST(*items: dict) -> dict:
    return {"type": "list", "items": list(items)}


def CONCAT(*parts: dict) -> dict:
    return {"type": "concat", "parts": list(parts)}


def STRUCT(label: str, *fields: dict) -> dict:
    return {"type": "struct", "label": label, "fields": list(fields)}


MESSAGE_ID = "0192f7a2-3c4d-7e5f-8a6b-1c2d3e4f5a6b"
CONVERSATION_ID = "0192f7a2-0000-7abc-9def-000000000001"
SENDER_ID = "0192f7a1-ffff-7000-8000-00000000a11c"
RECIPIENT_ID = "0192f7a1-ffff-7000-8000-000000000b0b"
SUITE = "SEAL1-X25519-A256GCM-ED25519"
SERIAL = bytes(range(0x10, 0x20))

VALID: list[tuple[str, dict]] = [
    ("u8 : zéro", U8(0)),
    ("u8 : maximum 255", U8(255)),
    ("u32 : zéro", U32(0)),
    ("u32 : gros-boutiste", U32(0x01020304)),
    ("u32 : maximum 2^32-1", U32(U32_MAX)),
    ("u64 : zéro", U64(0)),
    ("u64 : millisecondes Unix, gros-boutiste", U64(1_794_009_600_000)),
    ("u64 : au-delà de 2^53, hors de portée d'un number", U64(2**53 + 1)),
    ("u64 : maximum 2^63-1", U64(U64_MAX)),
    ("bytes : vide", BYTES(b"")),
    ("bytes : un octet nul", BYTES(b"\x00")),
    ("bytes : longueur de 300, sur deux octets du préfixe", BYTES(bytes(i % 256 for i in range(300)))),
    ("text : vide", TEXT("")),
    ("text : ASCII", TEXT("SEAL")),
    ("text : é précomposé", TEXT("é")),
    ("text : e + accent combinant, normalisé en é précomposé", TEXT("é")),
    ("text : signe ångström U+212B, normalisé en Å U+00C5", TEXT("Å")),
    ("text : jamos hangeul composés en une syllabe", TEXT("가")),
    ("text : ligature ﬁ conservée (NFC, pas NFKC)", TEXT("ﬁ")),
    ("text : phoque U+1F9AD, quatre octets UTF-8", TEXT("\U0001f9ad")),
    ("uuid : minuscules", UUID(MESSAGE_ID)),
    ("uuid : majuscules, mêmes octets", UUID(MESSAGE_ID.upper())),
    ("uuid : nul", UUID("00000000-0000-0000-0000-000000000000")),
    ("uuid : maximal", UUID("ffffffff-ffff-ffff-ffff-ffffffffffff")),
    ("list : vide", LIST()),
    ("list : deux uuid", LIST(UUID(RECIPIENT_ID), UUID(SENDER_ID))),
    ("list : u64", LIST(U64(1), U64(2), U64(3))),
    ("list : textes", LIST(TEXT("a"), TEXT(""), TEXT("é"))),
    (
        "list : éléments composés, comme une entrée de liste de révocation",
        LIST(CONCAT(BYTES(SERIAL), U64(1_794_009_600_000), U8(1))),
    ),
    ("concat : vide", CONCAT()),
    ("struct : étiquette seule", STRUCT("SEAL-test-v1")),
    (
        "struct : tous les types, dans l'ordre",
        STRUCT(
            "SEAL-test-v1",
            U8(1),
            U32(2),
            U64(3),
            BYTES(b"\x04"),
            TEXT("cinq"),
            UUID(MESSAGE_ID),
            LIST(UUID(SENDER_ID)),
            BYTES(b""),
        ),
    ),
    (
        "struct : forme de l'AAD d'un message (section 7.3)",
        STRUCT("SEAL-aad-v1", U8(1), TEXT(SUITE), UUID(MESSAGE_ID), UUID(CONVERSATION_ID), UUID(SENDER_ID), BYTES(SERIAL)),
    ),
    (
        "struct : forme de l'enveloppe hachée, avec une liste d'encapsulations (section 9)",
        STRUCT(
            "SEAL-env-v1",
            U8(1),
            TEXT(SUITE),
            UUID(MESSAGE_ID),
            UUID(CONVERSATION_ID),
            UUID(SENDER_ID),
            BYTES(SERIAL),
            BYTES(bytes(12)),
            BYTES(b"chiffre"),
            LIST(
                CONCAT(UUID(RECIPIENT_ID), BYTES(bytes(16)), BYTES(bytes(32)), BYTES(bytes(48))),
                CONCAT(UUID(SENDER_ID), BYTES(bytes(16)), BYTES(bytes(32)), BYTES(bytes(48))),
            ),
        ),
    ),
]

INVALID: list[tuple[str, dict]] = [
    ("u8 : négatif", U8(-1)),
    ("u8 : 256", U8(256)),
    ("u32 : négatif", U32(-1)),
    ("u32 : 2^32", U32(2**32)),
    ("u64 : négatif", U64(-1)),
    ("u64 : 2^63, hors du long de Java", U64(2**63)),
    ("u64 : 2^64-1", U64(2**64 - 1)),
    ("text : demi-caractère haut isolé", TEXT("a\ud800b")),
    ("text : demi-caractère bas isolé", TEXT("\udc00")),
    ("text : paire inversée", TEXT("\udc00\ud800")),
    ("uuid : vide", UUID("")),
    ("uuid : sans tirets", UUID(MESSAGE_ID.replace("-", ""))),
    ("uuid : entre accolades", UUID("{" + MESSAGE_ID + "}")),
    ("uuid : préfixe urn:uuid:", UUID("urn:uuid:" + MESSAGE_ID)),
    ("uuid : caractère non hexadécimal", UUID(MESSAGE_ID[:-1] + "g")),
    ("uuid : tiret déplacé", UUID("0192f7a23-c4d-7e5f-8a6b-1c2d3e4f5a6b")),
    ("uuid : groupes raccourcis acceptés par UUID.fromString", UUID("1-2-3-4-5")),
    ("uuid : trop long", UUID(MESSAGE_ID + "0")),
    ("struct : étiquette vide", STRUCT("", U8(1))),
    ("list : un élément invalide rejette toute la liste", LIST(UUID(SENDER_ID), UUID("pas-un-uuid"))),
    ("struct : un champ invalide rejette toute la structure", STRUCT("SEAL-test-v1", U8(1), TEXT("\ud800"))),
]


def build() -> str:
    valid = []
    for name, descriptor in VALID:
        valid.append({"name": name, "input": descriptor, "expected": b64url(encode(descriptor))})
    invalid = []
    for name, descriptor in INVALID:
        try:
            encode(descriptor)
        except Rejected as reason:
            invalid.append({"name": name, "input": descriptor, "reason": str(reason)})
        else:
            raise AssertionError(f"le cas « {name} » devrait être rejeté")
    document = {
        "format": "seal-vectors-v1",
        "subject": "Encodage canonique : PROTOCOL.md, section 2 (MSG-01, MSG-02, ADR 0006)",
        "generator": "src/protocol/tools/gen_vectors.py",
        "valid": valid,
        "invalid": invalid,
    }
    # ensure_ascii : les caractères hors ASCII restent visibles sous leur forme \uXXXX (cas NFC).
    return json.dumps(document, ensure_ascii=True, indent=2) + "\n"


# Politique des mots de passe (PROTOCOL.md, section 3 ; AUTH-11).

MIN_LENGTH = 15
MAX_LENGTH = 128
MIN_WORD_LENGTH = 3
SERVICE_WORDS = ("seal", "uqac")


def fold(value: str) -> str:
    """NFKC puis minuscules par la correspondance de casse par défaut d'Unicode."""
    return unicodedata.normalize("NFKC", value).lower()


def well_formed(value: str) -> bool:
    return not any(0xD800 <= ord(char) <= 0xDFFF for char in value)


def local_part(email: str) -> str:
    """Partie locale, avant le premier « + » ou « @ »."""
    at = email.index("@")
    plus = email.find("+")
    return email[: plus if 0 <= plus < at else at]


def without_context_words(folded: str, personal: list[str]) -> str:
    words: list[str] = []
    for word in [*personal, *SERVICE_WORDS]:
        candidate = fold(word)
        if len(candidate) >= MIN_WORD_LENGTH and candidate not in words:
            words.append(candidate)
    # Du plus long au plus court ; sorted est stable, même avec reverse.
    ordered = sorted(words, key=len, reverse=True)
    remaining = folded
    removed = True
    while removed:
        removed = False
        for word in ordered:
            shorter = remaining.replace(word, "")
            if len(shorter) < len(remaining):
                remaining = shorter
                removed = True
    return remaining


def load_blocklist() -> set[str]:
    return {fold(line) for line in BLOCKLIST.read_text(encoding="utf-8").splitlines()}


def evaluate(password: str, username: str, email: str, blocklist: set[str]) -> dict:
    """État de chaque règle affichée par le client, et verdict du serveur."""
    normalized = unicodedata.normalize("NFKC", password)
    valid = well_formed(normalized)
    length = valid and MIN_LENGTH <= len(normalized) <= MAX_LENGTH
    folded = normalized.lower()
    context = valid and len(without_context_words(folded, [username, local_part(email)])) >= MIN_LENGTH
    blocklist_rule = length and folded not in blocklist
    return {
        "rules": {"length": length, "context": context, "blocklist": blocklist_rule},
        "accepted": length and context and blocklist_rule,
    }


PASSWORD_CASES: list[tuple[str, str, str, str]] = [
    ("PROTOCOL.md : « seal » répété, rien ne reste", "sealsealsealseal", "valentin", "valentin@exemple.ca"),
    ("PROTOCOL.md : passes répétées", "sesealalsesealalsesealal", "valentin", "valentin@exemple.ca"),
    ("PROTOCOL.md : identifiant retiré, « 2026 » reste", "valentin.le.gall2026", "valentin.le.gall", "v@exemple.ca"),
    ("PROTOCOL.md : partie locale d'abord, 12 points de code restent", "annabelle sur le pont", "ann", "annabelle@exemple.ca"),
    ("PROTOCOL.md : partie locale d'abord, 19 restent", "annabelle sur le pont du lac", "ann", "annabelle@exemple.ca"),
    ("PROTOCOL.md : à longueur égale, l'identifiant d'abord (refusé)", "abababzzzzzzzzzzzzz", "aba", "bab@exemple.ca"),
    ("PROTOCOL.md : à longueur égale, l'identifiant d'abord (accepté)", "abababzzzzzzzzzzzzz", "bab", "aba@exemple.ca"),
    ("PROTOCOL.md : phrase de passe acceptée", "le phoque nage dans le port de seal", "valentin", "valentin@exemple.ca"),
    ("PROTOCOL.md : demi-paire isolée", "\ud800fjord-tuque-raquette", "valentin", "valentin@exemple.ca"),
    ("longueur : vide", "", "valentin", "valentin@exemple.ca"),
    ("longueur : 14 points de code", "fjord-tuque-ra", "valentin", "valentin@exemple.ca"),
    ("longueur : 15 points de code", "fjord-tuque-rak", "valentin", "valentin@exemple.ca"),
    ("longueur : 128 points de code", ("phoque " * 19)[:128].ljust(128, "x"), "valentin", "valentin@exemple.ca"),
    ("longueur : 129 points de code", ("phoque " * 19)[:128].ljust(129, "x"), "valentin", "valentin@exemple.ca"),
    ("longueur : 15 phoques U+1F9AD, 30 unités UTF-16", "\U0001f9ad" * 15, "valentin", "valentin@exemple.ca"),
    ("longueur : 14 phoques U+1F9AD", "\U0001f9ad" * 14, "valentin", "valentin@exemple.ca"),
    ("NFKC : pleine chasse ramenée à l'ASCII", "ｆｊｏｒｄ－ｔｕｑｕｅ－ｒａｑｕｅｔｔｅ", "valentin", "valentin@exemple.ca"),
    ("NFKC : 8 ligatures ﬁ comptent 16 points de code", "ﬁ" * 8, "valentin", "valentin@exemple.ca"),
    ("liste de blocage : entrée telle quelle", "Mailcreated5240", "valentin", "valentin@exemple.ca"),
    ("liste de blocage : comparée en minuscules", "MAILCREATED5240", "valentin", "valentin@exemple.ca"),
    ("contexte : identifiant retiré sans tenir compte de la casse", "Valentin aime le fjord", "valentin", "valentin@exemple.ca"),
    ("contexte : partie locale avant « + »", "annabelle sur le pont", "ann", "annabelle+seal@exemple.ca"),
    ("contexte : mot de moins de 3 points de code ignoré", "abababababababab", "zzz", "ab@exemple.ca"),
    ("contexte : « uqac » retiré", "uqacuqacuqacuqacuqac", "valentin", "valentin@exemple.ca"),
    # Audit de la PR 5 : chaque cas ci-dessous fait échouer une implémentation fautive que les précédents laissaient passer.
    ("contexte : partie locale en majuscules, repliée", "Valentin.Le-Gall et son phoque", "valentin", "Valentin.Le-Gall@exemple.ca"),
    ("contexte : « seal » dans l'identifiant, repasse après son retrait", "valenSEALtin du lac", "valentin", "valentin@exemple.ca"),
    ("longueur : 128 phoques U+1F9AD, 256 unités UTF-16", "\U0001f9ad" * 128, "valentin", "valentin@exemple.ca"),
    ("longueur : 129 phoques U+1F9AD", "\U0001f9ad" * 129, "valentin", "valentin@exemple.ca"),
]

# Version d'Unicode du générateur : NFKC et minuscules en dépendent. Elle est écrite dans le fichier, si bien qu'une
# régénération sous une autre version fait échouer --check (la CI épingle Python).
UNICODE_VERSION = unicodedata.unidata_version


def build_password_policy() -> str:
    blocklist = load_blocklist()
    cases = []
    for name, password, username, email in PASSWORD_CASES:
        case = {
            "name": name,
            "password": password,
            "username": username,
            "email": email,
            "localPart": local_part(email),
            **evaluate(password, username, email, blocklist),
        }
        # Une paire de substitution écrite en deux échappements serait vue ici comme deux demi-paires, mais recombinée
        # par tout lecteur JSON : le cas doit survivre à l'aller-retour.
        if json.loads(json.dumps(case)) != case:
            raise AssertionError(f"le cas « {name} » ne survit pas à l'aller-retour JSON")
        reread = json.loads(json.dumps(password))
        if evaluate(reread, username, email, blocklist) != {k: case[k] for k in ("rules", "accepted")}:
            raise AssertionError(f"le cas « {name} » change de verdict une fois relu")
        cases.append(case)
    document = {
        "format": "seal-password-policy-v1",
        "subject": "Politique des mots de passe : PROTOCOL.md, section 3 (AUTH-11, ADR 0019, ADR 0024)",
        "generator": "src/protocol/tools/gen_vectors.py",
        "unicode": UNICODE_VERSION,
        "cases": cases,
    }
    return json.dumps(document, ensure_ascii=True, indent=2) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Vecteurs de référence de SEAL (PROTOCOL.md).")
    parser.add_argument("--check", action="store_true", help="échoue si un fichier n'est pas à jour")
    check = parser.parse_args().check
    outputs = [
        (OUTPUT, build(), f"{len(VALID)} cas valides, {len(INVALID)} cas à rejeter"),
        (PASSWORD_OUTPUT, build_password_policy(), f"{len(PASSWORD_CASES)} cas"),
    ]
    if check:
        stale = [path for path, content, _ in outputs if not path.exists() or path.read_text(encoding="ascii") != content]
        for path in stale:
            print(f"{path} n'est pas à jour : lancez python3 tools/gen_vectors.py", file=sys.stderr)
        if stale:
            return 1
        for path, _, summary in outputs:
            print(f"{path.name} est à jour ({summary})")
        return 0
    for path, content, summary in outputs:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="ascii")
        print(f"{path.name} écrit ({summary})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
