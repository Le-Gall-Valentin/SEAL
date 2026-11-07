#!/usr/bin/env python3
"""Produit les vecteurs de référence de l'encodage canonique (PROTOCOL.md, section 2 ; MSG-02).

Troisième implémentation de l'encodage, indépendante de Java et de TypeScript (ADR 0006) :
les deux suites de tests relisent ces vecteurs, aucune ne se valide elle-même.
Bibliothèque standard de Python uniquement.

Usage, depuis src/protocol :
    python3 tools/gen_vectors.py           réécrit vectors/canonical-encoding.json
    python3 tools/gen_vectors.py --check   échoue si le fichier diffère (CI)
"""

from __future__ import annotations

import base64
import json
import re
import struct as binary
import sys
import unicodedata
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent.parent / "vectors" / "canonical-encoding.json"

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


def main() -> int:
    content = build()
    if "--check" in sys.argv[1:]:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="ascii") != content:
            print(f"{OUTPUT} n'est pas à jour : lancez python3 tools/gen_vectors.py", file=sys.stderr)
            return 1
        print(f"{OUTPUT.name} est à jour ({len(VALID)} cas valides, {len(INVALID)} cas à rejeter)")
        return 0
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(content, encoding="ascii")
    print(f"{OUTPUT.name} écrit ({len(VALID)} cas valides, {len(INVALID)} cas à rejeter)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
