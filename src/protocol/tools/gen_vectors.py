#!/usr/bin/env python3
"""Produit les vecteurs de référence de SEAL, lus par les tests Java et TypeScript.

- vectors/canonical-encoding.json : encodage canonique (PROTOCOL.md, section 2 ; MSG-02, ADR 0006) ;
- vectors/password-policy.json : politique des mots de passe (PROTOCOL.md, section 3 ; AUTH-11, ADR 0024) ;
- vectors/ed25519-rfc8032.json : vecteurs officiels d'Ed25519 (RFC 8032, section 7.1 ; QA-01) ;
- vectors/identity.json : certificat, preuve de possession et liaison de sous-clé (PROTOCOL.md, section 4 ;
  KEY-03 à KEY-05, QA-02, ADR 0025).

Troisième implémentation, indépendante de Java et de TypeScript : les deux suites de tests relisent ces
vecteurs, aucune ne se valide elle-même. Bibliothèque standard de Python, plus `cryptography` pour signer en
Ed25519 (ADR 0025, point 3), installée par : python3 -m pip install --require-hashes -r tools/requirements.txt

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

PROTOCOL_DIR = Path(__file__).resolve().parent.parent
OUTPUT = PROTOCOL_DIR / "vectors" / "canonical-encoding.json"
PASSWORD_OUTPUT = PROTOCOL_DIR / "vectors" / "password-policy.json"
RFC8032_OUTPUT = PROTOCOL_DIR / "vectors" / "ed25519-rfc8032.json"
IDENTITY_OUTPUT = PROTOCOL_DIR / "vectors" / "identity.json"
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


def ed25519():
    """Module Ed25519 de `cryptography`, importé à la demande pour que l'erreur dise quoi installer."""
    try:
        from cryptography.hazmat.primitives.asymmetric import ed25519 as module
        from cryptography.hazmat.primitives import serialization
    except ImportError as error:
        raise SystemExit(
            "gen_vectors.py : la bibliothèque cryptography manque ; lancez "
            "python3 -m pip install --require-hashes -r tools/requirements.txt"
        ) from error
    return module, serialization


def sha256(data: bytes) -> bytes:
    return hashlib.sha256(data).digest()


def public_key(seed: bytes) -> bytes:
    module, serialization = ed25519()
    key = module.Ed25519PrivateKey.from_private_bytes(seed).public_key()
    return key.public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)


def sign(seed: bytes, message: bytes) -> bytes:
    module, _ = ed25519()
    return module.Ed25519PrivateKey.from_private_bytes(seed).sign(message)


def verifies(public: bytes, message: bytes, signature: bytes) -> bool:
    module, _ = ed25519()
    try:
        module.Ed25519PublicKey.from_public_bytes(public).verify(signature, message)
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


def certificate_struct(f: dict) -> dict:
    return STRUCT(
        "SEAL-cert-v1",
        BYTES(from_b64url(f["serial"])),
        BYTES(from_b64url(f["issuerKeyId"])),
        UUID(f["subjectId"]),
        TEXT(f["username"]),
        BYTES(from_b64url(f["ikPublic"])),
        U64(f["notBefore"]),
        U64(f["notAfter"]),
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
        U64(f["createdAt"]),
        U64(f["expiresAt"]),
    )


STRUCTURES = {"certificate": certificate_struct, "proof": proof_struct, "subkeyBinding": binding_struct}


def certificate_fields(username: str = "valentin.dupont") -> dict:
    return {
        "serial": b64url(SERIAL),
        "issuerKeyId": b64url(sha256(public_key(CA_SEED))[:8]),
        "subjectId": USER_ID,
        "username": username,
        "ikPublic": b64url(public_key(IK_SEED)),
        "notBefore": NOT_BEFORE,
        "notAfter": NOT_BEFORE + CERTIFICATE_VALIDITY_DAYS * DAY_MS,
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
        "createdAt": NOT_BEFORE,
        "expiresAt": NOT_BEFORE + SUBKEY_VALIDITY_DAYS * DAY_MS,
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
    longer = dict(certificate, notAfter=certificate["notAfter"] + DAY_MS)
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
        "forgeries": [
            forgery("certificat prolongé d'un jour après signature", "certificate", longer, sign(CA_SEED, cert_bytes), "ca"),
            forgery("certificat signé par une autre clé que la CA", "certificate", certificate, sign(OTHER_SEED, cert_bytes), "ca"),
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
        ],
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
