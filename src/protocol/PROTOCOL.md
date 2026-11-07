# Protocole SEAL v1

Ce document est la référence publique du protocole de SEAL : conformément au principe de Kerckhoffs,
sa sécurité ne repose que sur les clés. Il précise, à l'octet près, les sections 6 à 9 du cahier des
charges ; client (TypeScript) et serveur (Java) DOIVENT produire exactement les encodages décrits ici.

Les mots DOIT, NE DOIT PAS et PEUT ont le sens de la RFC 2119. Les sections sur les clés et
certificats, les messages, les groupes et la non-répudiation s'ajouteront avec leurs lots ; toute
modification d'un format met à jour, dans le même commit, ce document, les vecteurs de `vectors/` et
les tests Java et TypeScript.

## 1. Conventions

- Octets dans le JSON : base64url sans remplissage (RFC 4648, section 5). L'hexadécimal est réservé à
  l'affichage des empreintes.
- Identifiants : UUID (RFC 9562), UUIDv7 pour ceux que crée SEAL.
- Dates : millisecondes Unix dans toute structure signée, ISO 8601 UTC dans l'API.
- Texte : UTF-8, normalisé NFC avant toute signature.

## 2. Encodage canonique

Toute structure signée ou hachée est encodée en octets selon la grammaire ci-dessous, identique en
Java (`fr.vlegall.seal.crypto.Canonical`) et en TypeScript (`src/client/src/crypto/canonical.ts`).
Le JSON n'est jamais signé ni haché directement.

### 2.1 Primitives

| Primitive | Octets | Domaine (ADR 0006) |
| --- | --- | --- |
| `u8(x)` | 1 octet | 0 à 255 |
| `u32(x)` | 4 octets, gros-boutiste | 0 à 2³²−1 |
| `u64(x)` | 8 octets, gros-boutiste | 0 à 2⁶³−1 |
| `bytes(b)` | `u32(longueur de b) ‖ b` | longueur au plus 2³²−1 |
| `text(s)` | `bytes(UTF-8(NFC(s)))` | chaîne sans demi-caractère Unicode isolé |
| `uuid(u)` | 16 octets, dans l'ordre réseau | forme textuelle `8-4-4-4-12` |
| `list(T, xs)` | `u32(nombre d'éléments) ‖ T(x1) ‖ … ‖ T(xn)` | au plus 2³²−1 éléments |
| `struct(label, f1, …, fn)` | `text(label) ‖ f1 ‖ … ‖ fn` | étiquette non vide |

`‖` désigne la concaténation. Un élément de liste PEUT être composé de plusieurs primitives
concaténées, comme une encapsulation `uuid(recipientId) ‖ bytes(ekId) ‖ bytes(enc) ‖ bytes(wrappedCek)`.

### 2.2 Règles (MSG-01)

1. Les champs sont encodés exactement dans l'ordre documenté par chaque structure.
2. Aucun champ n'est optionnel : un champ absent est une chaîne d'octets vide, `bytes()` ou `text("")`,
   soit `00 00 00 00`.
3. L'étiquette ouvre toujours la structure. Chaque type de structure a la sienne (`SEAL-msg-v1`,
   `SEAL-cert-v1`…), ce qui empêche de faire passer une signature d'un type pour un autre.
4. Le tri d'une liste, quand une structure l'exige (destinataires, encapsulations), est fait par la
   structure avant l'encodage, jamais par l'encodeur.

### 2.3 Valeurs rejetées (ADR 0006)

Une implémentation DOIT rejeter, sans rien encoder :

- un entier négatif, non entier ou au-delà du maximum de sa primitive ; en TypeScript, un `number`
  au-delà de 2⁵³−1 passé à `u64` (il faut alors un `bigint`) ;
- un texte contenant un demi-caractère Unicode isolé (*surrogate*), que Java et `TextEncoder`
  remplaceraient sinon en silence ;
- un UUID hors de la forme `8-4-4-4-12` en hexadécimal (majuscules ou minuscules acceptées) : sans
  tirets, entre accolades, préfixé par `urn:uuid:`, ou aux groupes raccourcis ;
- une `struct` d'étiquette vide.

La version d'un UUID n'est pas vérifiée par l'encodeur : les structures le font quand le CDC l'exige
(DATA-03).

### 2.4 Exemple

`struct("SEAL-test-v1", u8(1), text("é"))`, où `é` est écrit `e` suivi de l'accent combinant U+0301 :

```
00 00 00 0c  53 45 41 4c 2d 74 65 73 74 2d 76 31   text("SEAL-test-v1")
01                                                  u8(1)
00 00 00 02  c3 a9                                  text("é"), après NFC : U+00E9
```

### 2.5 Vecteurs de référence (MSG-02)

`vectors/canonical-encoding.json` contient des cas valides, chacun avec ses octets attendus, et des
cas à rejeter. Les tests Java et TypeScript les relisent tous.

```json
{
  "format": "seal-vectors-v1",
  "valid": [
    { "name": "…", "input": { "type": "u8", "value": 255 }, "expected": "_w" }
  ],
  "invalid": [
    { "name": "…", "input": { "type": "uuid", "value": "1-2-3-4-5" }, "reason": "…" }
  ]
}
```

Un descripteur `input` a pour `type` l'une des primitives : `u8` et `u32` portent un nombre, `u64`
une chaîne décimale (`JSON.parse` perd la précision au-delà de 2⁵³), `bytes` une chaîne base64url,
`text` et `uuid` une chaîne ; `list` porte ses `items`, `concat` ses `parts` et `struct` son `label`
et ses `fields`, eux-mêmes des descripteurs. Les caractères hors ASCII sont échappés en `\uXXXX`.

Les vecteurs sont produits par `tools/gen_vectors.py`, une troisième implémentation en Python, sans
dépendance, indépendante de Java et de TypeScript. Pour les régénérer, depuis `src/protocol` :

```bash
python3 tools/gen_vectors.py           # réécrit vectors/canonical-encoding.json
python3 tools/gen_vectors.py --check   # échoue si le fichier n'est pas à jour (CI)
```
