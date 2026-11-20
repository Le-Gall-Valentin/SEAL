#!/usr/bin/env python3
"""Produit les vecteurs de référence de SEAL, lus par les tests Java et TypeScript.

- vectors/canonical-encoding.json : encodage canonique (PROTOCOL.md, section 2 ; MSG-02, ADR 0006) ;
- vectors/password-policy.json : politique des mots de passe (PROTOCOL.md, section 3 ; AUTH-11, ADR 0024) ;
- vectors/ed25519-rfc8032.json : vecteurs officiels d'Ed25519 (RFC 8032, section 7.1 ; QA-01) ;
- vectors/identity.json : certificat, preuve de possession et liaison de sous-clé (PROTOCOL.md, section 4 ;
  KEY-03 à KEY-05, QA-02, ADR 0025) ;
- vectors/vault.json : clé de récupération et coffre de clés (PROTOCOL.md, section 5 ; KEY-30 à KEY-33, QA-02,
  ADR 0027) ;
- vectors/hkdf-rfc5869.json et vectors/x25519-rfc7748.json : vecteurs officiels de HKDF-SHA256 (RFC 5869, annexe A,
  cas 1 à 3) et de X25519 (RFC 7748, section 6.1) (QA-01, ADR 0027) ;
- vectors/envelope.json : enveloppe d'un message, encapsulations HPKE et falsifications (PROTOCOL.md, section 6 ;
  CDC 7, QA-02 à QA-04, ADR 0030).

Troisième implémentation, indépendante de Java et de TypeScript : les deux suites de tests relisent ces
vecteurs, aucune ne se valide elle-même. Bibliothèque standard de Python, plus `cryptography` pour signer en
Ed25519 (ADR 0025, point 3), pour HKDF, AES-GCM et les clés du trousseau (ADR 0027), et pour vérifier l'HPKE de
la section 6 contre cryptography.hpke (50.0.2, ADR 0030, point 2), installée par :
python3 -m pip install --require-hashes -r tools/requirements.txt

Usage, depuis src/protocol :
    python3 tools/gen_vectors.py           réécrit les fichiers de vectors/
    python3 tools/gen_vectors.py --check   échoue si un fichier diffère (CI)
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import re
import struct as binary
import sys
import unicodedata
from pathlib import Path
from types import SimpleNamespace

PROTOCOL_DIR = Path(__file__).resolve().parent.parent
OUTPUT = PROTOCOL_DIR / "vectors" / "canonical-encoding.json"
PASSWORD_OUTPUT = PROTOCOL_DIR / "vectors" / "password-policy.json"
RFC8032_OUTPUT = PROTOCOL_DIR / "vectors" / "ed25519-rfc8032.json"
IDENTITY_OUTPUT = PROTOCOL_DIR / "vectors" / "identity.json"
VAULT_OUTPUT = PROTOCOL_DIR / "vectors" / "vault.json"
HKDF_OUTPUT = PROTOCOL_DIR / "vectors" / "hkdf-rfc5869.json"
X25519_OUTPUT = PROTOCOL_DIR / "vectors" / "x25519-rfc7748.json"
ENVELOPE_OUTPUT = PROTOCOL_DIR / "vectors" / "envelope.json"
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


# ---------------------------------------------------------------------------------------------
# Ed25519 et identité (QA-01, QA-02, PROTOCOL.md section 4, ADR 0025). Ed25519 est déterministe : les signatures
# sont stables d'une génération à l'autre, et chaque implémentation doit les reproduire à l'octet près.
# ---------------------------------------------------------------------------------------------


def crypto_lib() -> SimpleNamespace:
    """Modules de `cryptography`, importés à la demande pour que l'erreur dise quoi installer."""
    try:
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import ed25519, x25519
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
        from cryptography.hazmat.primitives.kdf.hkdf import HKDF, HKDFExpand
    except ImportError as error:
        raise SystemExit(
            "gen_vectors.py : la bibliothèque cryptography manque ; lancez "
            "python3 -m pip install --require-hashes -r tools/requirements.txt"
        ) from error
    return SimpleNamespace(
        hashes=hashes,
        serialization=serialization,
        ed25519=ed25519,
        x25519=x25519,
        AESGCM=AESGCM,
        HKDF=HKDF,
        HKDFExpand=HKDFExpand,
    )


def sha256(data: bytes) -> bytes:
    return hashlib.sha256(data).digest()


def public_key(seed: bytes) -> bytes:
    return raw_public(crypto_lib().ed25519.Ed25519PrivateKey.from_private_bytes(seed))


def raw_public(private_key) -> bytes:
    serialization = crypto_lib().serialization
    return private_key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)


def sign(seed: bytes, message: bytes) -> bytes:
    return crypto_lib().ed25519.Ed25519PrivateKey.from_private_bytes(seed).sign(message)


def verifies(public: bytes, message: bytes, signature: bytes) -> bool:
    try:
        crypto_lib().ed25519.Ed25519PublicKey.from_public_bytes(public).verify(signature, message)
        return True
    except Exception:  # InvalidSignature : la seule réponse attendue d'une signature fausse
        return False


# RFC 8032, section 7.1, tests 1 à 3 : clé secrète, clé publique, message, signature, en hexadécimal comme la RFC.
RFC8032_TESTS = [
    (
        "TEST 1, message vide",
        "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60",
        "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
        "",
        "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
    ),
    (
        "TEST 2, un octet",
        "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb",
        "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
        "72",
        "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00",
    ),
    (
        "TEST 3, deux octets",
        "c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7",
        "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025",
        "af82",
        "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a",
    ),
]


def build_rfc8032() -> str:
    tests = []
    for name, secret, public, message, signature in RFC8032_TESTS:
        # La bibliothèque doit retrouver la RFC : sinon, ni elle ni les vecteurs SEAL qu'elle signe ne font foi.
        if public_key(bytes.fromhex(secret)).hex() != public or sign(bytes.fromhex(secret), bytes.fromhex(message)).hex() != signature:
            raise AssertionError(f"cryptography ne reproduit pas la RFC 8032, {name}")
        tests.append({"name": name, "secretKey": secret, "publicKey": public, "message": message, "signature": signature})
    document = {
        "format": "seal-ed25519-rfc8032-v1",
        "subject": "Vecteurs officiels d'Ed25519 : RFC 8032, section 7.1, tests 1 à 3 (QA-01), en hexadécimal",
        "generator": "src/protocol/tools/gen_vectors.py",
        "tests": tests,
    }
    return json.dumps(document, ensure_ascii=True, indent=2) + "\n"


# Clés des vecteurs d'identité : les graines publiques des tests 1 à 3 de la RFC 8032, jamais de vraies clés.
CA_SEED = bytes.fromhex(RFC8032_TESTS[0][1])
IK_SEED = bytes.fromhex(RFC8032_TESTS[1][1])
OTHER_SEED = bytes.fromhex(RFC8032_TESTS[2][1])
SIGNERS = {"ca": CA_SEED, "ik": IK_SEED, "other": OTHER_SEED}

USER_ID = SENDER_ID
VICTIM_ID = RECIPIENT_ID
NOT_BEFORE = 1_794_009_600_000
DAY_MS = 86_400_000
CERTIFICATE_VALIDITY_DAYS = 397  # KEY-04, section 6.2
SUBKEY_VALIDITY_DAYS = 45  # KEY-21
KEM = "DHKEM-X25519-HKDF-SHA256"
USAGE = 0b11  # bit 0 : signature et non-répudiation ; bit 1 : certification de sous-clés
EK_ID = bytes(range(0x20, 0x30))
EK_PUBLIC = bytes(range(0x40, 0x60))  # 32 octets : la liaison ne vérifie pas le point X25519 (ADR 0025)
CHALLENGE = bytes(range(0x60, 0x80))

# Les 8 points d'ordre faible d'Ed25519, en encodage canonique (la liste de libsodium). Avec une telle clé, et R pris
# parmi eux, l'équation de vérification tient pour tout message : certaines bibliothèques, dont la JCA de la JDK 25,
# acceptent alors une signature sans clé privée. SEAL refuse ces clés et ces R (PROTOCOL.md, section 4).
SMALL_ORDER_POINTS = [
    "0100000000000000000000000000000000000000000000000000000000000000",  # neutre, ordre 1
    "ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",  # ordre 2
    "0000000000000000000000000000000000000000000000000000000000000000",  # ordre 4
    "0000000000000000000000000000000000000000000000000000000000000080",  # ordre 4
    "c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac037a",  # ordre 8
    "c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac03fa",  # ordre 8
    "26e8958fc2b227b045c3f489f2ef98f0d5dfac05d3c63339b13802886d53fc05",  # ordre 8
    "26e8958fc2b227b045c3f489f2ef98f0d5dfac05d3c63339b13802886d53fc85",  # ordre 8
]

# Autres encodages des mêmes points, refusés aussi : bit de signe posé sur x nul, et y non canonique (y = p, y = p + 1).
SMALL_ORDER_OTHER_ENCODINGS = [
    "0100000000000000000000000000000000000000000000000000000000000080",  # neutre, bit de signe
    "ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",  # ordre 2, bit de signe
    "edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",  # y = p, soit 0
    "edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",  # y = p, bit de signe
    "eeffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",  # y = p + 1, soit 1
    "eeffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",  # y = p + 1, bit de signe
]


def certificate_struct(f: dict) -> dict:
    return STRUCT(
        "SEAL-cert-v1",
        BYTES(from_b64url(f["serial"])),
        BYTES(from_b64url(f["issuerKeyId"])),
        UUID(f["subjectId"]),
        TEXT(f["username"]),
        BYTES(from_b64url(f["ikPublic"])),
        U64(int(f["notBefore"])),
        U64(int(f["notAfter"])),
        U8(f["usage"]),
    )


def proof_struct(f: dict, label: str = "SEAL-pop-v1") -> dict:
    return STRUCT(label, UUID(f["userId"]), BYTES(from_b64url(f["ikPublic"])), BYTES(from_b64url(f["challenge"])))


def binding_struct(f: dict) -> dict:
    return STRUCT(
        "SEAL-subkey-v1",
        UUID(f["userId"]),
        BYTES(from_b64url(f["certSerial"])),
        BYTES(from_b64url(f["ekId"])),
        TEXT(f["kem"]),
        BYTES(from_b64url(f["ekPublic"])),
        U64(int(f["createdAt"])),
        U64(int(f["expiresAt"])),
    )


STRUCTURES = {"certificate": certificate_struct, "proof": proof_struct, "subkeyBinding": binding_struct}


def certificate_fields(username: str = "valentin.dupont") -> dict:
    return {
        "serial": b64url(SERIAL),
        "issuerKeyId": b64url(sha256(public_key(CA_SEED))[:8]),
        "subjectId": USER_ID,
        "username": username,
        "ikPublic": b64url(public_key(IK_SEED)),
        # u64 en chaîne décimale, comme dans canonical-encoding.json (ADR 0006, point 5 ; PROTOCOL.md, 2.5).
        "notBefore": str(NOT_BEFORE),
        "notAfter": str(NOT_BEFORE + CERTIFICATE_VALIDITY_DAYS * DAY_MS),
        "usage": USAGE,
    }


def proof_fields(user: str = USER_ID) -> dict:
    return {"userId": user, "ikPublic": b64url(public_key(IK_SEED)), "challenge": b64url(CHALLENGE)}


def binding_fields() -> dict:
    return {
        "userId": USER_ID,
        "certSerial": b64url(SERIAL),
        "ekId": b64url(EK_ID),
        "kem": KEM,
        "ekPublic": b64url(EK_PUBLIC),
        "createdAt": str(NOT_BEFORE),
        "expiresAt": str(NOT_BEFORE + SUBKEY_VALIDITY_DAYS * DAY_MS),
    }


def signed(name: str, structure: str, fields: dict, signer: str) -> dict:
    encoded = encode(STRUCTURES[structure](fields))
    return {"name": name, "fields": fields, "signer": signer, "encoded": b64url(encoded), "signature": b64url(sign(SIGNERS[signer], encoded))}


def forgery(name: str, structure: str, fields: dict, signature: bytes, verifier: str) -> dict:
    encoded = encode(STRUCTURES[structure](fields))
    public = public_key(SIGNERS[verifier])
    # Chaque falsification doit être refusée par la bibliothèque de référence elle-même.
    if verifies(public, encoded, signature):
        raise AssertionError(f"la falsification « {name} » passe la vérification")
    return {"name": name, "structure": structure, "fields": fields, "verifier": verifier, "signature": b64url(signature)}


def build_identity() -> str:
    certificate = certificate_fields()
    proof = proof_fields()
    binding = binding_fields()
    cert_bytes = encode(certificate_struct(certificate))
    proof_bytes = encode(proof_struct(proof))
    binding_bytes = encode(binding_struct(binding))
    longer = dict(certificate, notAfter=str(int(certificate["notAfter"]) + DAY_MS))
    signature_only = dict(certificate, usage=USAGE ^ 0b10)
    swapped = dict(binding, createdAt=binding["expiresAt"], expiresAt=binding["createdAt"])
    other_user = proof_fields(VICTIM_ID)
    flipped = bytearray(sign(IK_SEED, binding_bytes))
    flipped[0] ^= 0x01
    document = {
        "format": "seal-identity-v1",
        "subject": "Identité : PROTOCOL.md, section 4 (KEY-03 à KEY-05, KEY-10, QA-02, ADR 0025)",
        "generator": "src/protocol/tools/gen_vectors.py",
        "keys": {
            role: {"seed": b64url(seed), "publicKey": b64url(public_key(seed))} for role, seed in SIGNERS.items()
        },
        "authority": {
            "publicKey": b64url(public_key(CA_SEED)),
            "keyId": b64url(sha256(public_key(CA_SEED))[:8]),
            "fingerprint": sha256(public_key(CA_SEED)).hex().upper(),
        },
        "certificates": [
            signed("certificat : usage signature et certification", "certificate", certificate, "ca"),
            signed(
                "certificat : identifiant de 20 caractères, avec . et _ (AUTH-01)",
                "certificate",
                certificate_fields("anne_marie.tremblay2"),
                "ca",
            ),
        ],
        "proofs": [signed("preuve de possession du défi", "proof", proof, "ik")],
        "subkeyBindings": [signed("liaison de la sous-clé X25519, 45 jours", "subkeyBinding", binding, "ik")],
        "smallOrder": {
            "rule": "Une clé publique ou un R d'ordre faible est refusé avant toute vérification"
            " (PROTOCOL.md, introduction de la section 4)",
            "points": [b64url(bytes.fromhex(point)) for point in SMALL_ORDER_POINTS],
            "otherEncodings": [b64url(bytes.fromhex(encoding)) for encoding in SMALL_ORDER_OTHER_ENCODINGS],
            "message": b64url("n'importe quel message".encode("utf-8")),
        },
        "forgeries": [
            forgery("certificat prolongé d'un jour après signature", "certificate", longer, sign(CA_SEED, cert_bytes), "ca"),
            forgery("certificat signé par une autre clé que la CA", "certificate", certificate, sign(OTHER_SEED, cert_bytes), "ca"),
            forgery(
                "certificat dont le bit de certification est effacé après signature",
                "certificate",
                signature_only,
                sign(CA_SEED, cert_bytes),
                "ca",
            ),
            forgery(
                "preuve signée sous une autre étiquette (séparation de domaine)",
                "proof",
                proof,
                sign(IK_SEED, encode(proof_struct(proof, "SEAL-pop-v2"))),
                "ik",
            ),
            forgery("preuve d'un compte présentée pour un autre", "proof", other_user, sign(IK_SEED, proof_bytes), "ik"),
            forgery("liaison vérifiée avec une autre clé que l'IK", "subkeyBinding", binding, sign(IK_SEED, binding_bytes), "other"),
            forgery("liaison dont un bit de la signature est inversé", "subkeyBinding", binding, bytes(flipped), "ik"),
            forgery(
                "liaison dont createdAt et expiresAt sont permutés après signature",
                "subkeyBinding",
                swapped,
                sign(IK_SEED, binding_bytes),
                "ik",
            ),
        ],
    }
    return json.dumps(document, ensure_ascii=True, indent=2) + "\n"


# ---------------------------------------------------------------------------------------------
# Coffre de clés (KEY-30 à KEY-33, QA-02, PROTOCOL.md section 5, ADR 0027). AES-GCM est déterministe à nonce fixé :
# chaque implémentation doit reproduire la KEK, la VDK enveloppée et le blob à l'octet près.
# ---------------------------------------------------------------------------------------------

CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
RECOVERY_KEY = bytes.fromhex("8f3a0c11d2e45b6799aa01fe2b3c4d5e6f708192")  # KEY-30 : 20 octets, jamais une vraie clé
VDK = bytes(range(0x40, 0x60))
VAULT_SALT = bytes(range(0x60, 0x80))
NONCE_K = bytes(range(0x80, 0x8C))
NONCE_B = bytes(range(0x90, 0x9C))
VAULT_VERSION = 1
# EK du trousseau : clé privée d'Alice, RFC 7748, section 6.1 ; jamais une vraie clé.
EK_PRIVATE = bytes.fromhex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
VAULT_EK_ID = bytes(range(0xA0, 0xB0))
# Version suivante (KEY-32, PROTOCOL.md 5.5) : une seconde EK, la clé privée de Bob (RFC 7748, section 6.1), créée
# 30 jours plus tard, et un nonceB neuf ; jamais une vraie clé.
NEXT_EK_PRIVATE = bytes.fromhex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
NEXT_EK_ID = bytes(range(0xB0, 0xC0))
NEXT_NONCE_B = bytes(range(0xC0, 0xCC))
NEXT_CREATED_AT_DAYS = 30


def pkcs8(private_key) -> bytes:
    serialization = crypto_lib().serialization
    return private_key.private_bytes(
        serialization.Encoding.DER, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
    )


def crockford(data: bytes) -> str:
    value = int.from_bytes(data, "big")
    bits = len(data) * 8
    return "".join(CROCKFORD[(value >> (bits - 5 * (index + 1))) & 31] for index in range(bits // 5))


def vault_aad(user_id: str, version: int) -> bytes:
    return encode(STRUCT("SEAL-vault-v1", UUID(user_id), U64(version)))


def kek_info(user_id: str) -> bytes:
    return encode(STRUCT("SEAL-kek-v1", UUID(user_id)))


def kek_aad(user_id: str) -> bytes:
    return encode(STRUCT("SEAL-kek-aad-v1", UUID(user_id)))


def kek(recovery_key: bytes, salt: bytes, user_id: str) -> bytes:
    lib = crypto_lib()
    return lib.HKDF(algorithm=lib.hashes.SHA256(), length=32, salt=salt, info=kek_info(user_id)).derive(recovery_key)


def keyring_document(user_id: str, extra: dict | None = None, subkey_extra: dict | None = None) -> dict:
    """Trousseau de la section 6.4, champs dans l'ordre de PROTOCOL.md 5.2 (ADR 0027, point 3)."""
    lib = crypto_lib()
    created_at = NOT_BEFORE
    subkey = {
        "ekId": b64url(VAULT_EK_ID),
        "kem": KEM,
        "pkcs8": b64url(pkcs8(lib.x25519.X25519PrivateKey.from_private_bytes(EK_PRIVATE))),
        "createdAt": created_at,
        "expiresAt": created_at + SUBKEY_VALIDITY_DAYS * DAY_MS,
    }
    subkey.update(subkey_extra or {})
    document = {
        "v": 1,
        "userId": user_id,
        "ik": b64url(pkcs8(lib.ed25519.Ed25519PrivateKey.from_private_bytes(IK_SEED))),
        "eks": [subkey],
        "createdAt": created_at,
    }
    document.update(extra or {})
    return document


def keyring_bytes(document: dict) -> bytes:
    return json.dumps(document, separators=(",", ":"), ensure_ascii=True).encode("ascii")


def seal_vault(user_id: str, version: int, plaintext: bytes, recovery_key: bytes = RECOVERY_KEY) -> dict:
    aesgcm = crypto_lib().AESGCM
    return {
        "vaultVersion": version,
        "salt": b64url(VAULT_SALT),
        "nonceK": b64url(NONCE_K),
        "wrappedVdk": b64url(aesgcm(kek(recovery_key, VAULT_SALT, user_id)).encrypt(NONCE_K, VDK, kek_aad(user_id))),
        "nonceB": b64url(NONCE_B),
        "blob": b64url(aesgcm(VDK).encrypt(NONCE_B, plaintext, vault_aad(user_id, version))),
    }


def next_version(keyring: dict) -> dict:
    """KEY-32, PROTOCOL.md 5.5 : le trousseau augmenté d'une sous-clé, rechiffré par la même VDK sous l'AAD de la
    version suivante avec un nonceB neuf ; sel, nonceK et VDK enveloppée restent ceux de la version 1."""
    lib = crypto_lib()
    created_at = NOT_BEFORE + NEXT_CREATED_AT_DAYS * DAY_MS
    ek = lib.x25519.X25519PrivateKey.from_private_bytes(NEXT_EK_PRIVATE)
    document = dict(
        keyring,
        eks=keyring["eks"]
        + [
            {
                "ekId": b64url(NEXT_EK_ID),
                "kem": KEM,
                "pkcs8": b64url(pkcs8(ek)),
                "createdAt": created_at,
                "expiresAt": created_at + SUBKEY_VALIDITY_DAYS * DAY_MS,
            }
        ],
    )
    plaintext = keyring_bytes(document)
    version = VAULT_VERSION + 1
    payload = dict(
        seal_vault(USER_ID, VAULT_VERSION, keyring_bytes(keyring)),
        vaultVersion=version,
        nonceB=b64url(NEXT_NONCE_B),
        blob=b64url(lib.AESGCM(VDK).encrypt(NEXT_NONCE_B, plaintext, vault_aad(USER_ID, version))),
    )
    if rejecting_layer(USER_ID, RECOVERY_KEY, payload) != "keyring":
        raise SystemExit("gen_vectors.py : la version suivante du coffre ne s'ouvre pas")
    return {
        "vaultAad": b64url(vault_aad(USER_ID, version)),
        "keyringBytes": b64url(plaintext),
        "ekPublic": b64url(raw_public(ek)),
        "payload": payload,
    }


def rejecting_layer(user_id: str, recovery_key: bytes, payload: dict) -> str:
    """Étage qui refuse la charge, selon `cryptography` elle-même : key (la VDK ne se désenveloppe pas), blob (le
    trousseau ne se déchiffre pas) ou keyring (il se déchiffre ; c'est sa relecture stricte qui le refuse)."""
    aesgcm = crypto_lib().AESGCM
    try:
        vdk = aesgcm(kek(recovery_key, from_b64url(payload["salt"]), user_id)).decrypt(
            from_b64url(payload["nonceK"]), from_b64url(payload["wrappedVdk"]), kek_aad(user_id)
        )
    except Exception:  # InvalidTag : la seule réponse attendue d'une enveloppe fausse
        return "key"
    try:
        aesgcm(vdk).decrypt(
            from_b64url(payload["nonceB"]), from_b64url(payload["blob"]), vault_aad(user_id, payload["vaultVersion"])
        )
    except Exception:
        return "blob"
    return "keyring"


def vault_forgery(
    name: str, payload: dict, rejected_by: str, user_id: str = USER_ID, recovery_key: bytes = RECOVERY_KEY
) -> dict:
    """Une falsification et l'étage qui DOIT la refuser, contrôlé ici même (PROTOCOL.md 5.4)."""
    actual = rejecting_layer(user_id, recovery_key, payload)
    if actual != rejected_by:
        raise SystemExit(f"gen_vectors.py : « {name} » est refusée par {actual}, pas par {rejected_by}")
    return {
        "name": name,
        "userId": user_id,
        "recoveryKey": b64url(recovery_key),
        "payload": payload,
        "rejectedBy": rejected_by,
    }


def build_vault() -> str:
    lib = crypto_lib()
    keyring = keyring_document(USER_ID)
    plaintext = keyring_bytes(keyring)
    payload = seal_vault(USER_ID, VAULT_VERSION, plaintext)
    other_key = RECOVERY_KEY[:-1] + bytes([RECOVERY_KEY[-1] ^ 0x01])
    blob = bytearray(from_b64url(payload["blob"]))
    blob[0] ^= 0x01
    wrapped = bytearray(from_b64url(payload["wrappedVdk"]))
    wrapped[-1] ^= 0x80
    display = crockford(RECOVERY_KEY)

    def sealed(document: dict) -> dict:
        return seal_vault(USER_ID, VAULT_VERSION, keyring_bytes(document))

    if rejecting_layer(USER_ID, RECOVERY_KEY, payload) != "keyring":
        raise SystemExit("gen_vectors.py : le coffre de référence ne s'ouvre pas")
    document = {
        "format": "seal-vault-v1",
        "subject": "Coffre de clés : PROTOCOL.md, section 5 (KEY-30 à KEY-33, QA-02, ADR 0027)",
        "generator": "src/protocol/tools/gen_vectors.py",
        "recoveryKey": {
            "bytes": b64url(RECOVERY_KEY),
            "groups": [display[index : index + 4] for index in range(0, len(display), 4)],
        },
        "userId": USER_ID,
        "vdk": b64url(VDK),
        "kekInfo": b64url(kek_info(USER_ID)),
        "kekAad": b64url(kek_aad(USER_ID)),
        "vaultAad": b64url(vault_aad(USER_ID, VAULT_VERSION)),
        "kek": b64url(kek(RECOVERY_KEY, VAULT_SALT, USER_ID)),
        "keyring": keyring,
        "keyringBytes": b64url(plaintext),
        "publicKeys": {
            "ik": b64url(public_key(IK_SEED)),
            "ek": b64url(raw_public(lib.x25519.X25519PrivateKey.from_private_bytes(EK_PRIVATE))),
        },
        "payload": payload,
        "nextVersion": next_version(keyring),
        "forgeries": [
            vault_forgery("une autre clé de récupération (un bit)", payload, "key", recovery_key=other_key),
            vault_forgery("le coffre ouvert pour un autre compte", payload, "key", user_id=VICTIM_ID),
            vault_forgery(
                "une version réétiquetée (vaultVersion changée sans rechiffrer)",
                dict(payload, vaultVersion=VAULT_VERSION + 1),
                "blob",
            ),
            vault_forgery("un octet du blob modifié", dict(payload, blob=b64url(bytes(blob))), "blob"),
            vault_forgery("un bit de la VDK enveloppée inversé", dict(payload, wrappedVdk=b64url(bytes(wrapped))), "key"),
            vault_forgery(
                "un trousseau d'un autre compte, chiffré pour celui-ci", sealed(keyring_document(VICTIM_ID)), "keyring"
            ),
            vault_forgery("un trousseau avec un champ inconnu", sealed(keyring_document(USER_ID, {"extra": True})), "keyring"),
            vault_forgery("un trousseau d'une autre version de format", sealed(keyring_document(USER_ID, {"v": 2})), "keyring"),
            vault_forgery(
                "un trousseau dont l'ekId fait 15 octets",
                sealed(keyring_document(USER_ID, subkey_extra={"ekId": b64url(VAULT_EK_ID[:15])})),
                "keyring",
            ),
            vault_forgery(
                "un trousseau dont la sous-clé a un autre KEM",
                sealed(keyring_document(USER_ID, subkey_extra={"kem": "X-WING"})),
                "keyring",
            ),
            vault_forgery("un trousseau sans sous-clé", sealed(keyring_document(USER_ID, {"eks": []})), "keyring"),
        ],
    }
    return json.dumps(document, ensure_ascii=True, indent=2) + "\n"


# ---------------------------------------------------------------------------------------------
# Vecteurs officiels de HKDF-SHA256 et de X25519 (QA-01, ADR 0027, point 11), recopiés des RFC puis recalculés par
# `cryptography` : une valeur mal recopiée fait échouer la génération.
# ---------------------------------------------------------------------------------------------

# RFC 5869, annexe A, cas 1 à 3 (SHA-256) : nom, IKM, sel, info, longueur L, OKM.
HKDF_TESTS = [
    (
        "RFC 5869, A.1 : cas de base",
        "0b" * 22,
        "000102030405060708090a0b0c",
        "f0f1f2f3f4f5f6f7f8f9",
        42,
        "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
    ),
    (
        "RFC 5869, A.2 : entrées et sortie plus longues",
        bytes(range(0x00, 0x50)).hex(),
        bytes(range(0x60, 0xB0)).hex(),
        bytes(range(0xB0, 0x100)).hex(),
        82,
        "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c"
        "59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71"
        "cc30c58179ec3e87c14c01d5c1f3434f1d87",
    ),
    (
        "RFC 5869, A.3 : sel et info vides",
        "0b" * 22,
        "",
        "",
        42,
        "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
    ),
]

# RFC 7748, section 6.1 : clés privées et publiques d'Alice et de Bob, et leur secret partagé K.
X25519_ALICE_PRIVATE = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"
X25519_ALICE_PUBLIC = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"
X25519_BOB_PRIVATE = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb"
X25519_BOB_PUBLIC = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f"
X25519_SHARED = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"


def build_hkdf() -> str:
    lib = crypto_lib()
    tests = []
    for name, ikm, salt, info, length, okm in HKDF_TESTS:
        # Un sel vide vaut un sel de 32 octets nuls (RFC 5869, 2.2) ; cryptography le veut explicite (None).
        derived = lib.HKDF(
            algorithm=lib.hashes.SHA256(), length=length, salt=bytes.fromhex(salt) or None, info=bytes.fromhex(info)
        ).derive(bytes.fromhex(ikm))
        if derived.hex() != okm:
            raise AssertionError(f"cryptography ne reproduit pas la RFC 5869, {name}")
        tests.append({"name": name, "ikm": ikm, "salt": salt, "info": info, "length": length, "okm": okm})
    document = {
        "format": "seal-hkdf-rfc5869-v1",
        "subject": "Vecteurs officiels de HKDF-SHA256 : RFC 5869, annexe A, cas 1 à 3 (QA-01), en hexadécimal",
        "generator": "src/protocol/tools/gen_vectors.py",
        "tests": tests,
    }
    return json.dumps(document, ensure_ascii=True, indent=2) + "\n"


def build_x25519() -> str:
    x25519 = crypto_lib().x25519
    alice = x25519.X25519PrivateKey.from_private_bytes(bytes.fromhex(X25519_ALICE_PRIVATE))
    bob = x25519.X25519PrivateKey.from_private_bytes(bytes.fromhex(X25519_BOB_PRIVATE))
    if (
        raw_public(alice).hex() != X25519_ALICE_PUBLIC
        or raw_public(bob).hex() != X25519_BOB_PUBLIC
        or alice.exchange(bob.public_key()).hex() != X25519_SHARED
        or bob.exchange(alice.public_key()).hex() != X25519_SHARED
    ):
        raise AssertionError("cryptography ne reproduit pas la RFC 7748, section 6.1")
    document = {
        "format": "seal-x25519-rfc7748-v1",
        "subject": "Vecteurs officiels de X25519 : RFC 7748, section 6.1 (QA-01), en hexadécimal",
        "generator": "src/protocol/tools/gen_vectors.py",
        "alice": {"privateKey": X25519_ALICE_PRIVATE, "publicKey": X25519_ALICE_PUBLIC},
        "bob": {"privateKey": X25519_BOB_PRIVATE, "publicKey": X25519_BOB_PUBLIC},
        "sharedSecret": X25519_SHARED,
    }
    return json.dumps(document, ensure_ascii=True, indent=2) + "\n"


# ---------------------------------------------------------------------------------------------
# Messages (PROTOCOL.md, section 6 ; CDC 7 ; QA-02 à QA-04, ADR 0030). Le CDC encapsule la CEK avec aad = AAD, et
# l'API HPKE de `cryptography` ne prend pas d'AAD : le mode base de la RFC 9180 est composé ici avec ses primitives
# (X25519, HKDF, AES-GCM), comme le coffre (ADR 0030, point 2). Il est vérifié contre le vecteur A.1.1 de la
# RFC 9180, puis contre cryptography.hpke avec une AAD vide ; @hpke/core ouvre ensuite ses encapsulations.
# ---------------------------------------------------------------------------------------------

HPKE_KEM_ID = 0x0020  # DHKEM(X25519, HKDF-SHA256)
HPKE_KDF_ID = 0x0001  # HKDF-SHA256
HPKE_AES_128_GCM = 0x0001
HPKE_AES_256_GCM = 0x0002
HPKE_KEM_SUITE = b"KEM" + HPKE_KEM_ID.to_bytes(2, "big")

# RFC 9180, annexe A.1.1 : DHKEM(X25519, HKDF-SHA256), HKDF-SHA256, AES-128-GCM, mode base, première encryption.
RFC9180_A11 = {
    "info": "4f6465206f6e2061204772656369616e2055726e",
    "ikmE": "7268600d403fce431561aef583ee1613527cff655c1343f29812e66706df3234",
    "pkEm": "37fda3567bdbd628e88668c3c8d7e97d1d1253b6d4ea6d44c150f741f1bf4431",
    "skEm": "52c4a758a802cd8b936eceea314432798d5baf2d7e9235dc084ab1b9cfa2f736",
    "ikmR": "6db9df30aa07dd42ee5e8181afdb977e538f5e1fec8a06223f33f7013e525037",
    "pkRm": "3948cfe0ad1ddb695d780e59077195da6c56506b027329794ab02bca80815c4d",
    "skRm": "4612c550263fc8ad58375df3f557aac531d26850903e55a9f23f21d8534e8ac8",
    "shared_secret": "fe0e18c9f024ce43799ae393c7e8fe8fce9d218875e8227b0187c04e7d2ea1fc",
    "key_schedule_context": "00725611c9d98c07c03f60095cd32d400d8347d45ed67097bbad50fc56da742d07cb6cffde367bb0565ba28bb02c90744a20f5ef37f30523526106f637abb05449",
    "secret": "12fff91991e93b48de37e7daddb52981084bd8aa64289c3788471d9a9712f397",
    "key": "4531685d41d65f03dc48f6b8302c05b0",
    "base_nonce": "56d890e5accaaf011cff4b7d",
    "pt": "4265617574792069732074727574682c20747275746820626561757479",
    "aad": "436f756e742d30",
    "ct": "f938558b5d72f1a23810b4be2ab4f84331acc02fc97babc53a52ae8218a355a96d8770ac83d07bea87e13c512a",
}


def hpke_suite_id(aead_id: int) -> bytes:
    return b"HPKE" + HPKE_KEM_ID.to_bytes(2, "big") + HPKE_KDF_ID.to_bytes(2, "big") + aead_id.to_bytes(2, "big")


def hkdf_extract(salt: bytes, ikm: bytes) -> bytes:
    """RFC 5869 : un sel vide vaut 32 octets nuls, que HMAC complète de même."""
    return crypto_lib().HKDF.extract(crypto_lib().hashes.SHA256(), salt, ikm)


def hkdf_expand(prk: bytes, info: bytes, length: int) -> bytes:
    return crypto_lib().HKDFExpand(crypto_lib().hashes.SHA256(), length, info).derive(prk)


def labeled_extract(suite_id: bytes, salt: bytes, label: bytes, ikm: bytes) -> bytes:
    return hkdf_extract(salt, b"HPKE-v1" + suite_id + label + ikm)


def labeled_expand(suite_id: bytes, prk: bytes, label: bytes, info: bytes, length: int) -> bytes:
    return hkdf_expand(prk, length.to_bytes(2, "big") + b"HPKE-v1" + suite_id + label + info, length)


def x25519_public(private: bytes) -> bytes:
    return raw_public(crypto_lib().x25519.X25519PrivateKey.from_private_bytes(private))


def x25519_dh(private: bytes, public: bytes) -> bytes:
    x25519 = crypto_lib().x25519
    return x25519.X25519PrivateKey.from_private_bytes(private).exchange(x25519.X25519PublicKey.from_public_bytes(public))


def hpke_derive_key_pair(ikm: bytes) -> tuple[bytes, bytes]:
    """RFC 9180, 7.1.3 : DeriveKeyPair pour X25519."""
    dkp_prk = labeled_extract(HPKE_KEM_SUITE, b"", b"dkp_prk", ikm)
    private = labeled_expand(HPKE_KEM_SUITE, dkp_prk, b"sk", b"", 32)
    return private, x25519_public(private)


def hpke_shared_secret(dh: bytes, enc: bytes, recipient_public: bytes) -> bytes:
    eae_prk = labeled_extract(HPKE_KEM_SUITE, b"", b"eae_prk", dh)
    return labeled_expand(HPKE_KEM_SUITE, eae_prk, b"shared_secret", enc + recipient_public, 32)


def hpke_key_schedule(aead_id: int, shared_secret: bytes, info: bytes) -> SimpleNamespace:
    """RFC 9180, 5.1 : KeyScheduleS en mode base (psk et psk_id vides)."""
    suite = hpke_suite_id(aead_id)
    context = bytes([0]) + labeled_extract(suite, b"", b"psk_id_hash", b"") + labeled_extract(suite, b"", b"info_hash", info)
    secret = labeled_extract(suite, shared_secret, b"secret", b"")
    key_length = 16 if aead_id == HPKE_AES_128_GCM else 32
    return SimpleNamespace(
        context=context,
        secret=secret,
        key=labeled_expand(suite, secret, b"key", context, key_length),
        base_nonce=labeled_expand(suite, secret, b"base_nonce", context, 12),
    )


def hpke_seal(recipient_public: bytes, info: bytes, aad: bytes, plaintext: bytes, ikm_e: bytes,
              aead_id: int = HPKE_AES_256_GCM) -> SimpleNamespace:
    """SealBase : la clé éphémère dérive de ikm_e (DeriveKeyPair), pour des vecteurs stables."""
    private_e, enc = hpke_derive_key_pair(ikm_e)
    shared = hpke_shared_secret(x25519_dh(private_e, recipient_public), enc, recipient_public)
    schedule = hpke_key_schedule(aead_id, shared, info)
    ciphertext = crypto_lib().AESGCM(schedule.key).encrypt(schedule.base_nonce, plaintext, aad)
    return SimpleNamespace(enc=enc, ciphertext=ciphertext, shared=shared, schedule=schedule)


def hpke_open(recipient_private: bytes, enc: bytes, info: bytes, aad: bytes, ciphertext: bytes,
              aead_id: int = HPKE_AES_256_GCM) -> bytes:
    shared = hpke_shared_secret(x25519_dh(recipient_private, enc), enc, x25519_public(recipient_private))
    schedule = hpke_key_schedule(aead_id, shared, info)
    return crypto_lib().AESGCM(schedule.key).decrypt(schedule.base_nonce, ciphertext, aad)


def check_hpke() -> None:
    """L'HPKE composé ici reproduit la RFC 9180 (A.1.1), puis s'entend avec cryptography.hpke (AAD vide)."""
    v = {name: bytes.fromhex(value) for name, value in RFC9180_A11.items()}
    if hpke_derive_key_pair(v["ikmE"]) != (v["skEm"], v["pkEm"]) or hpke_derive_key_pair(v["ikmR"]) != (
        v["skRm"], v["pkRm"]
    ):
        raise SystemExit("gen_vectors.py : DeriveKeyPair ne reproduit pas la RFC 9180, A.1.1")
    sealed = hpke_seal(v["pkRm"], v["info"], v["aad"], v["pt"], v["ikmE"], HPKE_AES_128_GCM)
    reproduced = (
        sealed.enc == v["pkEm"]
        and sealed.shared == v["shared_secret"]
        and sealed.schedule.context == v["key_schedule_context"]
        and sealed.schedule.secret == v["secret"]
        and sealed.schedule.key == v["key"]
        and sealed.schedule.base_nonce == v["base_nonce"]
        and sealed.ciphertext == v["ct"]
        and hpke_open(v["skRm"], sealed.enc, v["info"], v["aad"], sealed.ciphertext, HPKE_AES_128_GCM) == v["pt"]
    )
    if not reproduced:
        raise SystemExit("gen_vectors.py : l'HPKE composé ne reproduit pas la RFC 9180, A.1.1")
    try:
        from cryptography.hazmat.primitives import hpke
    except ImportError as error:
        raise SystemExit(
            "gen_vectors.py : cryptography.hpke manque (cryptography 50.0.2) ; lancez "
            "python3 -m pip install --require-hashes -r tools/requirements.txt"
        ) from error
    x25519 = crypto_lib().x25519
    suite = hpke.Suite(hpke.KEM.X25519, hpke.KDF.HKDF_SHA256, hpke.AEAD.AES_256_GCM)
    recipient = x25519.X25519PrivateKey.from_private_bytes(v["skRm"])
    theirs = suite.encrypt(v["pt"], recipient.public_key(), info=v["info"])
    ours = hpke_seal(v["pkRm"], v["info"], b"", v["pt"], v["ikmE"])
    from cryptography.exceptions import InvalidTag

    try:
        agree = (
            hpke_open(v["skRm"], theirs[:32], v["info"], b"", theirs[32:]) == v["pt"]
            and suite.decrypt(ours.enc + ours.ciphertext, recipient, info=v["info"]) == v["pt"]
        )
    except InvalidTag:
        agree = False
    if not agree:
        raise SystemExit("gen_vectors.py : l'HPKE composé ne s'entend pas avec cryptography.hpke")


MESSAGE_SUITE = SUITE
U64_FIELDS = ("sentAt", "senderSeq", "groupSeq")
MESSAGE_VERSION = 1
MESSAGE_CONVERSATION_ID = "0192f7a1-ffff-7000-8000-00000000c0c0"
OTHER_CONVERSATION_ID = "0192f7a1-ffff-7000-8000-00000000c0c1"
SENT_AT = NOT_BEFORE + 60_000
MESSAGE_SALT = bytes(range(0x50, 0x60))
MESSAGE_CEK = bytes(range(0x60, 0x80))
MESSAGE_NONCE = bytes(range(0x80, 0x8C))
MESSAGE_BODY = "Rendez-vous à l’UQAC demain, 9 h 30 \U0001F9AD"
BOB_EK_ID = bytes(range(0xC0, 0xD0))
# EK propre à Bob : aucune clé privée n'est partagée avec le trousseau de vault.json.
BOB_EK_PRIVATE, _ = hpke_derive_key_pair(sha256(b"SEAL vecteurs, EK de Bob"))
RECEIVED_AT = SENT_AT + 1_500  # serverReceivedAt du message (CDC 7.7, étape 6)


def uuid_v7(millis: int, random: bytes) -> str:
    """UUIDv7 (RFC 9562) : 48 bits de millisecondes, version 7, variante 10, 74 bits pris dans `random`."""
    value = bytearray(millis.to_bytes(6, "big") + random[:10])
    value[6] = 0x70 | (value[6] & 0x0F)
    value[8] = 0x80 | (value[8] & 0x3F)
    hexa = value.hex()
    return f"{hexa[0:8]}-{hexa[8:12]}-{hexa[12:16]}-{hexa[16:20]}-{hexa[20:32]}"


MESSAGE_ID_V7 = uuid_v7(SENT_AT, bytes(range(0xD0, 0xDA)))


def ikm_for(label: str) -> bytes:
    """Matériau de la clé éphémère d'une encapsulation : fixe, propre à chaque encapsulation des vecteurs."""
    return sha256(("SEAL vecteurs, clé éphémère : " + label).encode("utf-8"))


def message_header(conversation_id: str = MESSAGE_CONVERSATION_ID) -> dict:
    return {
        "v": MESSAGE_VERSION,
        "suite": MESSAGE_SUITE,
        "messageId": MESSAGE_ID_V7,
        "conversationId": conversation_id,
        "senderId": USER_ID,
        "senderCertSerial": SERIAL,  # certificat de identity.json
    }


def signed_payload_fields(**changes) -> dict:
    fields = dict(
        message_header(),
        conversationType=1,
        sentAt=SENT_AT,
        senderSeq=1,
        groupSeq=0,
        salt=MESSAGE_SALT,
        recipientIds=sorted([USER_ID, VICTIM_ID], key=uuid),
        contentType="text/plain",
        body=MESSAGE_BODY,
    )
    fields.update(changes)
    return fields


def signed_payload_struct(f: dict) -> dict:
    return STRUCT(
        "SEAL-msg-v1",
        U8(f["v"]),
        TEXT(f["suite"]),
        UUID(f["messageId"]),
        UUID(f["conversationId"]),
        U8(f["conversationType"]),
        UUID(f["senderId"]),
        BYTES(f["senderCertSerial"]),
        U64(f["sentAt"]),
        U64(f["senderSeq"]),
        U64(f["groupSeq"]),
        BYTES(f["salt"]),
        LIST(*[UUID(recipient) for recipient in f["recipientIds"]]),
        TEXT(f["contentType"]),
        TEXT(f["body"]),
    )


def message_aad(h: dict) -> bytes:
    return encode(
        STRUCT(
            "SEAL-aad-v1",
            U8(h["v"]),
            TEXT(h["suite"]),
            UUID(h["messageId"]),
            UUID(h["conversationId"]),
            UUID(h["senderId"]),
            BYTES(h["senderCertSerial"]),
        )
    )


def wrap_info(h: dict, recipient_id: str, ek_id: bytes) -> bytes:
    return encode(
        STRUCT(
            "SEAL-wrap-v1",
            TEXT(h["suite"]),
            UUID(h["messageId"]),
            UUID(h["conversationId"]),
            UUID(recipient_id),
            BYTES(ek_id),
        )
    )


# Destinataires des vecteurs : l'émetteur (EK d'Alice, sous-clé du coffre de vault.json) et Bob (EK de Bob).
MESSAGE_RECIPIENTS = {
    USER_ID: (VAULT_EK_ID, EK_PRIVATE),
    VICTIM_ID: (BOB_EK_ID, BOB_EK_PRIVATE),
}


def seal_message(header: dict, signed_payload: bytes, signature: bytes, recipients: list[str], label: str,
                 padding: bytes = b"", cek: bytes | None = None, nonce: bytes | None = None) -> dict:
    """
    CDC 7.5, étapes 5 à 7 : Plaintext chiffré par la CEK sous l'AAD, CEK encapsulée pour chaque destinataire. Sauf
    mention contraire, la CEK et le nonce dérivent de `label` : aucun couple ne chiffre deux textes (CDC 7.1).
    """
    cek = cek if cek is not None else sha256(("SEAL vecteurs, CEK : " + label).encode("utf-8"))
    nonce = nonce if nonce is not None else sha256(("SEAL vecteurs, nonce : " + label).encode("utf-8"))[:12]
    plaintext = raw_bytes(signed_payload) + raw_bytes(signature) + raw_bytes(padding)
    aad = message_aad(header)
    ciphertext = crypto_lib().AESGCM(cek).encrypt(nonce, plaintext, aad)
    wraps = []
    for recipient_id in sorted(recipients, key=uuid):
        ek_id, ek_private = MESSAGE_RECIPIENTS[recipient_id]
        info = wrap_info(header, recipient_id, ek_id)
        ikm = ikm_for(f"{label}, {recipient_id}")
        sealed = hpke_seal(x25519_public(ek_private), info, aad, cek, ikm)
        if hpke_open(ek_private, sealed.enc, info, aad, sealed.ciphertext) != cek:
            raise SystemExit("gen_vectors.py : une encapsulation des vecteurs ne s'ouvre pas")
        wraps.append(
            {
                "recipientId": recipient_id,
                "ekId": b64url(ek_id),
                "enc": b64url(sealed.enc),
                "wrappedCek": b64url(sealed.ciphertext),
                "ikmE": b64url(ikm),
                "info": b64url(info),
            }
        )
    envelope = {
        "v": header["v"],
        "suite": header["suite"],
        "messageId": header["messageId"],
        "conversationId": header["conversationId"],
        "senderId": header["senderId"],
        "senderCertSerial": b64url(header["senderCertSerial"]),
        "nonce": b64url(nonce),
        "ciphertext": b64url(ciphertext),
        "wraps": [{key: wrap[key] for key in ("recipientId", "ekId", "enc", "wrappedCek")} for wrap in wraps],
    }
    return {"plaintext": plaintext, "aad": aad, "ciphertext": ciphertext, "wraps": wraps, "envelope": envelope}


def sender_certificate() -> dict:
    """Certificat de l'émetteur, celui de identity.json, tel que le sert le serveur."""
    return dict(certificate_fields(), signature=b64url(sign(CA_SEED, encode(certificate_struct(certificate_fields())))))


# Ouverture d'une enveloppe (PROTOCOL.md 6.5, ADR 0030, point 4) : troisième implémentation des étapes 1 et 3 à 8, à
# côté de TypeScript. Le générateur ne publie une falsification que si elle rend le statut qu'il annonce.

B64URL_ALPHABET = re.compile(r"^[A-Za-z0-9_-]*$")


class Refused(Exception):
    def __init__(self, status: str) -> None:
        super().__init__(status)
        self.status = status


class Reader:
    """Relecture canonique stricte (PROTOCOL.md, section 2), symétrique de canonicalReader.ts."""

    def __init__(self, data: bytes) -> None:
        self.data, self.offset = data, 0

    def take(self, length: int) -> bytes:
        if length > len(self.data) - self.offset:
            raise Rejected("structure tronquée")
        start, self.offset = self.offset, self.offset + length
        return self.data[start:self.offset]

    def u8(self) -> int:
        return self.take(1)[0]

    def u32(self) -> int:
        return binary.unpack(">I", self.take(4))[0]

    def safe_u64(self) -> int:
        value = binary.unpack(">Q", self.take(8))[0]
        if value > 2**53 - 1:
            raise Rejected("u64 au-delà de 2^53 - 1")
        return value

    def bytes(self) -> bytes:
        return self.take(self.u32())

    def text(self) -> str:
        value = self.bytes().decode("utf-8")  # strict ; un BOM reste U+FEFF, comme en Java et en TypeScript
        if unicodedata.normalize("NFC", value) != value:
            raise Rejected("texte hors de la forme NFC")
        return value

    def uuid(self) -> str:
        hexa = self.take(16).hex()
        return f"{hexa[0:8]}-{hexa[8:12]}-{hexa[12:16]}-{hexa[16:20]}-{hexa[20:32]}"

    def list(self, element) -> list:
        count = self.u32()
        if count > len(self.data) - self.offset:
            raise Rejected("liste plus longue que les octets restants")
        return [element() for _ in range(count)]

    def end(self) -> None:
        if self.offset != len(self.data):
            raise Rejected("octets en trop")


def strict_b64url(value: object, length: int | None = None) -> bytes:
    if not isinstance(value, str) or not B64URL_ALPHABET.fullmatch(value) or len(value) % 4 == 1:
        raise Rejected("base64url hors de l'alphabet")
    data = from_b64url(value)
    if b64url(data) != value or (length is not None and len(data) != length):
        raise Rejected("base64url non canonique ou de mauvaise taille")
    return data


def decode_signed_payload(data: bytes) -> dict:
    """CDC 7.7, étape 5 : relecture stricte de SignedPayload ; tout écart lève Rejected."""
    reader = Reader(data)
    if reader.text() != "SEAL-msg-v1":
        raise Rejected("étiquette")
    f = {
        "v": reader.u8(),
        "suite": reader.text(),
        "messageId": reader.uuid(),
        "conversationId": reader.uuid(),
        "conversationType": reader.u8(),
        "senderId": reader.uuid(),
        "senderCertSerial": reader.bytes(),
        "sentAt": reader.safe_u64(),
        "senderSeq": reader.safe_u64(),
        "groupSeq": reader.safe_u64(),
        "salt": reader.bytes(),
    }
    f["recipientIds"] = reader.list(reader.uuid)
    f["contentType"] = reader.text()
    f["body"] = reader.text()
    reader.end()
    recipients = f["recipientIds"]
    if (
        f["v"] != MESSAGE_VERSION
        or f["suite"] != MESSAGE_SUITE
        or f["conversationType"] not in (1, 2)
        or len(f["senderCertSerial"]) != 16
        or len(f["salt"]) != 16
        or f["contentType"] != "text/plain"
        or len(f["body"]) > 4000  # points de code (ADR 0030, point 6)
        or not 1 <= len(recipients) <= 50
        or any(uuid(a) >= uuid(b) for a, b in zip(recipients, recipients[1:]))
    ):
        raise Rejected("SignedPayload hors du format")
    return f


def decode_envelope(envelope: dict) -> dict:
    """CDC 7.7, étape 1 : base64url canonique, tailles, version et suite."""
    if envelope["v"] != MESSAGE_VERSION or envelope["suite"] != MESSAGE_SUITE:
        raise Rejected("version ou suite")
    def canonical(value: str) -> str:
        uuid(value)
        return value.lower()

    header = {
        "v": MESSAGE_VERSION,
        "suite": MESSAGE_SUITE,
        "messageId": canonical(envelope["messageId"]),
        "conversationId": canonical(envelope["conversationId"]),
        "senderId": canonical(envelope["senderId"]),
        "senderCertSerial": strict_b64url(envelope["senderCertSerial"], 16),
    }
    ciphertext = strict_b64url(envelope["ciphertext"])
    if len(ciphertext) < 16 or len(envelope["wraps"]) > 50:
        raise Rejected("chiffré trop court ou trop d'encapsulations")
    wraps = [
        {
            "recipientId": canonical(wrap["recipientId"]),
            "ekId": strict_b64url(wrap["ekId"], 16),
            "enc": strict_b64url(wrap["enc"], 32),
            "wrappedCek": strict_b64url(wrap["wrappedCek"], 48),
        }
        for wrap in envelope["wraps"]
    ]
    return {"header": header, "nonce": strict_b64url(envelope["nonce"], 12), "ciphertext": ciphertext, "wraps": wraps}


def verified_certificate(served: dict, subject_id: str, at: int) -> dict:
    """CDC 7.7, étape 6 : signé par l'Autorité, pour `subject_id`, usage et validité à `at`, sans tolérance."""
    f = {key: value for key, value in served.items() if key != "signature"}
    not_before, not_after = int(f["notBefore"]), int(f["notAfter"])
    if (
        not verifies(public_key(CA_SEED), encode(certificate_struct(f)), from_b64url(served["signature"]))
        or from_b64url(f["issuerKeyId"]) != sha256(public_key(CA_SEED))[:8]
        or len(from_b64url(f["serial"])) != 16
        or f["subjectId"] != subject_id
        or f["usage"] != USAGE
        or not_after - not_before != CERTIFICATE_VALIDITY_DAYS * DAY_MS
        or not not_before <= at < not_after
    ):
        raise Refused("signature_invalid")
    return f


def open_message(envelope: dict, reader_id: str, reader_keys: dict, served_certificate: dict, received_at: int) -> str:
    """CDC 7.7, étapes 1 et 3 à 8 (ADR 0030, point 4) : le statut de l'enveloppe pour `reader_id`."""
    from cryptography.exceptions import InvalidTag

    try:
        try:
            decoded = decode_envelope(envelope)
            aad = message_aad(decoded["header"])
        except (Rejected, KeyError, TypeError, ValueError) as error:
            raise Refused("integrity_failed") from error
        header = decoded["header"]
        cek = None
        for wrap in decoded["wraps"]:
            ek_private = reader_keys.get(wrap["ekId"])
            if wrap["recipientId"] != reader_id or ek_private is None:
                continue
            try:
                cek = hpke_open(ek_private, wrap["enc"], wrap_info(header, reader_id, wrap["ekId"]), aad,
                                wrap["wrappedCek"])
                break
            except (InvalidTag, ValueError):
                continue
        if cek is None:
            raise Refused("undecryptable")
        try:
            plaintext = crypto_lib().AESGCM(cek).decrypt(decoded["nonce"], decoded["ciphertext"], aad)
            parts = Reader(plaintext)
            signed_payload, signature = parts.bytes(), parts.bytes()
            parts.bytes()
            parts.end()
            fields = decode_signed_payload(signed_payload)
        except (InvalidTag, Rejected, UnicodeDecodeError) as error:
            raise Refused("integrity_failed") from error
        if any(fields[key] != header[key] for key in ("messageId", "conversationId", "senderId", "senderCertSerial")):
            raise Refused("integrity_failed")
        certificate = verified_certificate(served_certificate, header["senderId"], received_at)
        if from_b64url(certificate["serial"]) != header["senderCertSerial"]:
            raise Refused("signature_invalid")
        if not verifies(from_b64url(certificate["ikPublic"]), signed_payload, signature):
            raise Refused("signature_invalid")
        if reader_id not in fields["recipientIds"]:
            raise Refused("recipients_mismatch")
        return "verified"
    except Refused as refusal:
        return refusal.status


def opened_by(envelope: dict, reader_id: str) -> str:
    """Le statut de l'enveloppe des vecteurs pour l'émetteur ou pour Bob, avec la sous-clé de chacun."""
    ek_id, ek_private = MESSAGE_RECIPIENTS[reader_id]
    return open_message(envelope, reader_id, {ek_id: ek_private}, sender_certificate(), RECEIVED_AT)


def flip(text_b64: str, index: int) -> str:
    """L'octet `index` inversé (tous ses bits)."""
    value = bytearray(from_b64url(text_b64))
    value[index] ^= 0xFF
    return b64url(bytes(value))


def message_forgery(name: str, envelope: dict, rejected_by: str) -> dict:
    status = opened_by(envelope, VICTIM_ID)
    if status != rejected_by:
        raise SystemExit(f"gen_vectors.py : la falsification « {name} » rend {status}, et non {rejected_by}")
    return {"name": name, "reader": VICTIM_ID, "envelope": envelope, "rejectedBy": rejected_by}


def accepted_envelope(name: str, envelope: dict) -> dict:
    for reader_id in MESSAGE_RECIPIENTS:
        if (status := opened_by(envelope, reader_id)) != "verified":
            raise SystemExit(f"gen_vectors.py : l'enveloppe « {name} » rend {status} pour {reader_id}")
    return {"name": name, "reader": VICTIM_ID, "envelope": envelope}


def build_envelope() -> str:
    check_hpke()
    fields = signed_payload_fields()
    signed_payload = encode(signed_payload_struct(fields))
    signature = sign(IK_SEED, signed_payload)
    header = message_header()
    sealed = seal_message(header, signed_payload, signature, fields["recipientIds"], "message", cek=MESSAGE_CEK,
                          nonce=MESSAGE_NONCE)
    envelope = sealed["envelope"]
    bob_wrap = next(index for index, wrap in enumerate(envelope["wraps"]) if wrap["recipientId"] == VICTIM_ID)

    def with_wrap(**changes) -> dict:
        wraps = [dict(wrap) for wrap in envelope["wraps"]]
        wraps[bob_wrap].update(changes)
        return dict(envelope, wraps=wraps)

    unsorted = signed_payload_fields(recipientIds=list(reversed(fields["recipientIds"])))
    unsorted_payload = encode(signed_payload_struct(unsorted))
    absent_payload = encode(signed_payload_struct(signed_payload_fields(recipientIds=[USER_ID])))
    other_header = message_header(OTHER_CONVERSATION_ID)
    forgeries = [
        message_forgery("un octet du chiffré inversé", dict(envelope, ciphertext=flip(envelope["ciphertext"], 40)),
                        "integrity_failed"),
        message_forgery("un octet du nonce inversé", dict(envelope, nonce=flip(envelope["nonce"], 0)), "integrity_failed"),
        message_forgery("un octet de enc inversé", with_wrap(enc=flip(envelope["wraps"][bob_wrap]["enc"], 5)),
                        "undecryptable"),
        message_forgery(
            "un octet de la CEK encapsulée inversé",
            with_wrap(wrappedCek=flip(envelope["wraps"][bob_wrap]["wrappedCek"], 47)),
            "undecryptable",
        ),
        message_forgery("l'en-tête déplacé dans une autre conversation par le serveur",
                        dict(envelope, conversationId=OTHER_CONVERSATION_ID), "undecryptable"),
        message_forgery(
            "le message signé rechiffré par Bob dans une autre conversation (transfert subreptice)",
            seal_message(other_header, signed_payload, signature, [VICTIM_ID], "transfert")["envelope"],
            "integrity_failed",
        ),
        message_forgery(
            "le message signé rechiffré par Bob sous un autre messageId",
            seal_message(dict(header, messageId=uuid_v7(SENT_AT, bytes(range(0xF0, 0xFA)))), signed_payload, signature,
                         [VICTIM_ID], "autre messageId")["envelope"],
            "integrity_failed",
        ),
        message_forgery(
            "le message signé rechiffré par Bob, qui s'en annonce l'émetteur",
            seal_message(dict(header, senderId=VICTIM_ID), signed_payload, signature, [VICTIM_ID], "autre émetteur")[
                "envelope"
            ],
            "integrity_failed",
        ),
        message_forgery(
            "le message signé rechiffré sous un autre numéro de certificat",
            seal_message(dict(header, senderCertSerial=bytes(range(0x30, 0x40))), signed_payload, signature, [VICTIM_ID],
                         "autre certificat")["envelope"],
            "integrity_failed",
        ),
        message_forgery(
            "le message signé par une autre clé d'identité que celle du certificat",
            seal_message(header, signed_payload, sign(OTHER_SEED, signed_payload), [VICTIM_ID], "autre IK")["envelope"],
            "signature_invalid",
        ),
        message_forgery(
            "un octet de la signature inversé, puis rechiffré",
            seal_message(header, signed_payload, from_b64url(flip(b64url(signature), 10)), [VICTIM_ID], "signature")[
                "envelope"
            ],
            "signature_invalid",
        ),
        message_forgery(
            "Bob absent des destinataires signés, mais destinataire d'une encapsulation",
            seal_message(header, absent_payload, sign(IK_SEED, absent_payload), [VICTIM_ID], "absent")["envelope"],
            "recipients_mismatch",
        ),
        message_forgery(
            "une liste de destinataires non triée, pourtant signée",
            seal_message(header, unsorted_payload, sign(IK_SEED, unsorted_payload), [VICTIM_ID], "non triée")["envelope"],
            "integrity_failed",
        ),
    ]
    accepted_envelope("message des vecteurs", envelope)
    padded = seal_message(header, signed_payload, signature, fields["recipientIds"], "padding", padding=bytes(7))[
        "envelope"
    ]
    document = {
        "format": "seal-envelope-v1",
        "subject": "Enveloppe d'un message : PROTOCOL.md, section 6 (CDC 7 ; KEY-20, QA-02 à QA-04, ADR 0030)",
        "generator": "src/protocol/tools/gen_vectors.py",
        "suite": MESSAGE_SUITE,
        "sender": {
            "userId": USER_ID,
            "ikPublic": b64url(public_key(IK_SEED)),
            "certificate": sender_certificate(),
        },
        "recipients": [
            {
                "userId": recipient_id,
                "ekId": b64url(MESSAGE_RECIPIENTS[recipient_id][0]),
                "ekPrivate": b64url(MESSAGE_RECIPIENTS[recipient_id][1]),
                "ekPublic": b64url(x25519_public(MESSAGE_RECIPIENTS[recipient_id][1])),
            }
            for recipient_id in fields["recipientIds"]
        ],
        "message": {
            # u64 en chaîne décimale, comme dans canonical-encoding.json (ADR 0006, point 5 ; PROTOCOL.md, 2.5).
            "fields": {
                key: b64url(value) if isinstance(value, bytes) else str(value) if key in U64_FIELDS else value
                for key, value in fields.items()
            },
            "signedPayload": b64url(signed_payload),
            "signature": b64url(signature),
            "cek": b64url(MESSAGE_CEK),
            "nonce": b64url(MESSAGE_NONCE),
            "plaintext": b64url(sealed["plaintext"]),
            "aad": b64url(sealed["aad"]),
            "ciphertext": b64url(sealed["ciphertext"]),
            "wraps": sealed["wraps"],
        },
        # serverReceivedAt du message, en chaîne décimale comme tout u64 (PROTOCOL.md, 2.5).
        "receivedAt": str(RECEIVED_AT),
        "envelope": envelope,
        "accepted": [accepted_envelope("padding de 7 octets, ignoré (ADR 0030, point 7)", padded)],
        "forgeries": forgeries,
    }
    return json.dumps(document, ensure_ascii=True, indent=2) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Vecteurs de référence de SEAL (PROTOCOL.md).")
    parser.add_argument("--check", action="store_true", help="échoue si un fichier n'est pas à jour")
    check = parser.parse_args().check
    outputs = [
        (OUTPUT, build(), f"{len(VALID)} cas valides, {len(INVALID)} cas à rejeter"),
        (PASSWORD_OUTPUT, build_password_policy(), f"{len(PASSWORD_CASES)} cas"),
        (RFC8032_OUTPUT, build_rfc8032(), f"{len(RFC8032_TESTS)} tests"),
        (IDENTITY_OUTPUT, build_identity(), "certificats, preuve, liaison et falsifications"),
        (VAULT_OUTPUT, build_vault(), "clé de récupération, coffre et falsifications"),
        (HKDF_OUTPUT, build_hkdf(), f"{len(HKDF_TESTS)} cas"),
        (X25519_OUTPUT, build_x25519(), "Alice, Bob et leur secret partagé"),
        (ENVELOPE_OUTPUT, build_envelope(), "message, encapsulations et falsifications"),
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
