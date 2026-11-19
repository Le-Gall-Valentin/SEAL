# Protocole SEAL v1

Ce document est la référence publique du protocole de SEAL : conformément au principe de Kerckhoffs,
sa sécurité ne repose que sur les clés. Il précise, à l'octet près, les sections 6 à 9 du cahier des
charges ; client (TypeScript) et serveur (Java) DOIVENT produire exactement les encodages décrits ici.

Les mots DOIT, NE DOIT PAS et PEUT ont le sens de la RFC 2119. Les sections sur les messages, les
groupes et la non-répudiation s'ajouteront avec leurs lots ; toute modification d'un format met à
jour, dans le même commit, ce document, les vecteurs de `vectors/` et les tests Java et TypeScript.

## 1. Conventions

- Octets dans le JSON : base64url sans remplissage (RFC 4648, section 5). L'hexadécimal est réservé à
  l'affichage des empreintes.
- Identifiants : UUID (RFC 9562), UUIDv7 pour ceux que crée SEAL.
- Dates : millisecondes Unix dans toute structure signée, ISO 8601 UTC dans l'API.
- Texte : UTF-8, normalisé NFC avant toute signature.

## 2. Encodage canonique

Toute structure signée ou hachée est encodée en octets selon la grammaire ci-dessous, identique en
Java (`fr.vlegall.seal.crypto.Canonical`) et en TypeScript (`src/client/src/shared/crypto/canonical.ts`).
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

Les vecteurs sont produits par `tools/gen_vectors.py`, une troisième implémentation en Python,
indépendante de Java et de TypeScript : bibliothèque standard seule, plus `cryptography` pour les signatures
de la section 4 et le coffre de la section 5. Pour les régénérer, depuis `src/protocol` :

```bash
python3 tools/gen_vectors.py           # réécrit les fichiers de vectors/
python3 tools/gen_vectors.py --check   # échoue si un fichier n'est pas à jour (CI)
```

## 3. Mots de passe (AUTH-11)

Client et serveur DOIVENT appliquer la même politique : le client affiche les règles en direct, le
serveur refuse (`PASSWORD_TOO_WEAK`). Algorithme (ADR 0019, point 2) :

1. `p` = mot de passe normalisé en NFKC. `p` est une suite de points de code valide, sans demi-paire
   isolée (`isWellFormed()` en TypeScript), et sa longueur en points de code est comprise entre 15
   et 128.
2. `m` = `p` en minuscules, par la correspondance de casse par défaut d'Unicode (`Locale.ROOT` en
   Java, `toLowerCase()` en TypeScript). `m` ne figure pas dans la liste de blocage, dont chaque
   entrée est normalisée de la même façon.
3. Mots du contexte : l'identifiant, la partie locale du courriel (avant le premier `+` ou `@`),
   `seal` et `uqac`, normalisés comme `m`. Un mot de moins de 3 points de code est ignoré, et un
   doublon n'est gardé qu'une fois, à sa première place.
4. Les mots sont rangés du plus long au plus court en points de code ; à longueur égale, ils gardent
   l'ordre de l'étape 3. Une passe prend chaque mot dans cet ordre et retire toutes ses occurrences
   de `m`, de gauche à droite et sans chevauchement (`String.replace` en Java, `replaceAll` avec une
   chaîne en TypeScript). On recommence tant qu'une passe a retiré quelque chose. Il reste au moins
   15 points de code.

L'ordre de l'étape 4 change le résultat quand deux mots se chevauchent, même à longueur égale (deux
dernières lignes du tableau). Avec l'identifiant `ann` et
la partie locale `annabelle`, `annabelle sur le pont` est refusé : retirer `annabelle` d'abord
laisse ` sur le pont`, 12 points de code. Retirer `ann` d'abord aurait laissé `abelle sur le pont`,
18 points de code, et accepté un mot de passe bâti sur la partie locale.

| Mot de passe | Identifiant | Partie locale | Résultat |
| --- | --- | --- | --- |
| `sealsealsealseal` | `valentin` | `valentin` | refusé (rien ne reste) |
| `sesealalsesealalsesealal` | `valentin` | `valentin` | refusé (passes répétées) |
| `valentin.le.gall2026` | `valentin.le.gall` | `v` | refusé (`2026` reste) |
| `annabelle sur le pont` | `ann` | `annabelle` | refusé (12 points de code restent) |
| `annabelle sur le pont du lac` | `ann` | `annabelle` | accepté (19 restent) |
| `abababzzzzzzzzzzzzz` | `aba` | `bab` | refusé (`aba` d'abord : 13 restent) |
| `abababzzzzzzzzzzzzz` | `bab` | `aba` | accepté (`bab` d'abord : 16 restent) |
| `le phoque nage dans le port de seal` | `valentin` | `valentin` | accepté |
| `fjord-tuque-raquette` précédé de U+D800 seul | `valentin` | `valentin` | refusé (demi-paire isolée) |

`vectors/password-policy.json`, produit par le même générateur (ADR 0024, point 6), reprend ce tableau et
y ajoute les bornes : 14, 15, 128 et 129 points de code, en ASCII comme en phoques U+1F9AD (deux unités UTF-16
chacun), pleine chasse et ligatures ramenées par NFKC, entrées de la liste de blocage, casse, `+` dans le courriel,
mot du contexte trop court, « uqac », partie locale en majuscules (les mots du contexte sont repliés), « seal » logé
dans l'identifiant (la repasse retire l'identifiant reformé). Son format est `seal-password-policy-v1` ; son champ
`unicode` donne la version d'Unicode du générateur, dont NFKC et les minuscules dépendent : régénérer sous une autre
version fait échouer `--check`, et la CI épingle Python. Chaque cas donne `password`, `username`, `email`,
`localPart`, l'état des trois règles affichées par le client et le verdict du serveur :

```json
{
  "name": "longueur : 15 points de code",
  "password": "fjord-tuque-rak",
  "username": "valentin",
  "email": "valentin@exemple.ca",
  "localPart": "valentin",
  "rules": { "length": true, "context": true, "blocklist": true },
  "accepted": true
}
```

`rules.length` vaut l'étape 1, `rules.context` l'étape 4 (sur une suite valide) et `rules.blocklist`
l'étape 2, comptée respectée seulement quand l'étape 1 l'est. `accepted` est vrai si les trois le sont.
Le serveur relit `accepted` et `localPart` (par `EmailAddress`), le client les trois règles, `localPart` et
`accepted`.

### 3.1 Liste de blocage

`password-blocklist.txt` compte les 10 000 premiers mots de passe de 15 caractères ou plus, dans
l'ordre de `Passwords/Common-Credentials/xato-net-10-million-passwords-1000000.txt` de
[SecLists](https://github.com/danielmiessler/SecLists) (ADR 0007, point 4), qui en compte 10 908.
Une entrée par ligne, en UTF-8, fins de ligne `\n`, toutes en ASCII.

| Élément | Valeur |
| --- | --- |
| Fichier source, SHA-256 | `424a3e03a17df0a2bc2b3ca749d81b04e79d59cb7aeec8876a5a3f308d0caf51` |
| `password-blocklist.txt`, SHA-256 | `0e44bfef3adb46364f09225c8e8bc2299169b1df1f020c49538a6aeafe12006d` |
| Taille | 176 195 octets, 10 000 lignes |
| Licence | MIT, copyright 2018 Daniel Miessler (`password-blocklist.LICENSE`) |

Le serveur l'embarque comme ressource Maven et un test vérifie son empreinte ; le client l'importe en
morceau séparé, chargé seulement par les écrans qui affichent les règles (ADR 0024, point 6 bis).


## 4. Identité : certificat, preuve de possession, sous-clé (KEY-03 à KEY-05, ADR 0025)

Chaque compte a une clé d'identité Ed25519 (IK), certifiée par l'Autorité SEAL (CA, Ed25519), qui signe une
sous-clé de chiffrement X25519 (EK). Toute signature est Ed25519 pur (RFC 8032), sur les octets canoniques de
la structure. L'étiquette de chaque structure fixe son algorithme : aucun champ ne le négocie, et un autre
algorithme serait une nouvelle version de l'étiquette (ADR 0025, point 11).

Toute vérification refuse d'abord une clé publique ou un `R` (les 32 premiers octets de la signature) d'ordre
faible ou non canonique, comme libsodium : bit de signe effacé, `y` lu en petit-boutiste ne DOIT être ni supérieur ou
égal à p = 2²⁵⁵ − 19, ni l'un des `y` des 8 points d'ordre faible (0, 1, p − 1 et les deux `y` d'ordre 8). La section
`smallOrder` de `vectors/identity.json` donne ces 8 points et 6 autres encodages des mêmes points. Avec eux, l'équation de vérification
tient pour tout message, et des bibliothèques courantes, dont la JCA de la JDK 25, acceptent la signature : une clé
sans clé privée serait certifiée (ADR 0025, point 14).

### 4.1 Autorité

| Élément | Valeur |
| --- | --- |
| Clé | Ed25519, générée au premier démarrage : `data/ca/seal-ca.p8` (clé privée, PKCS#8) et `data/ca/seal-ca.pub` (clé publique, SubjectPublicKeyInfo), droits 600 dans un dossier en 700 (KEY-10) |
| `issuerKeyId` | les 8 premiers octets de SHA-256(clé publique brute de 32 octets) |
| Empreinte | SHA-256(clé publique brute), 64 caractères hexadécimaux majuscules |
| `GET /api/v1/pki/ca` | `{publicKey, keyId, fingerprint}`, octets en base64url |

### 4.2 Certificat d'identité (KEY-04)

```
SealCertificate = struct("SEAL-cert-v1",
    bytes(serial),        // 16 octets aléatoires
    bytes(issuerKeyId),   // 8 octets
    uuid(subjectId),      // identifiant interne du compte
    text(username),       // identifiant définitif (AUTH-01, AUTH-03)
    bytes(ikPublic),      // 32 octets, Ed25519
    u64(notBefore),       // millisecondes Unix
    u64(notAfter),        // notBefore + 397 jours
    u8(usage))            // 0b11 : bit 0 signature et non-répudiation, bit 1 certification de sous-clés
signature = Ed25519.sign(clé CA, SealCertificate)
```

Un compte a au plus un certificat non révoqué. Après son expiration, une nouvelle publication de la **même** clé
d'identité révoque l'ancien, motif « remplacé » (code 4 de X.509) ; une autre clé est refusée, car changer de clé
est une réinitialisation, réservée au mode sudo (KEY-12, KEY-34, ADR 0025, point 4).

### 4.3 Preuve de possession (KEY-03)

```
ProofOfPossession = struct("SEAL-pop-v1", uuid(userId), bytes(ikPublic), bytes(challenge))
signature = Ed25519.sign(IK, ProofOfPossession)
```

`challenge` fait 32 octets, tirés par le serveur (`POST /api/v1/keys/challenge`), valables 5 minutes et pour
un seul usage. `userId` est celui de la session : une preuve faite pour un compte ne vaut pour aucun autre.

### 4.4 Liaison de sous-clé (KEY-05)

```
SubkeyBinding = struct("SEAL-subkey-v1",
    uuid(userId), bytes(certSerial), bytes(ekId),   // ekId : 16 octets aléatoires
    text(kem),            // "DHKEM-X25519-HKDF-SHA256"
    bytes(ekPublic),      // 32 octets, X25519
    u64(createdAt), u64(expiresAt))                 // expiresAt = createdAt + 45 jours
signature = Ed25519.sign(IK, SubkeyBinding)
```

Le serveur ramène `createdAt` et `expiresAt` à la milliseconde, comme la signature, puis refuse une liaison dont
`certSerial` n'est pas le certificat courant de l'appelant, un `ekId` déjà utilisé, un `createdAt` à plus de
5 minutes de son horloge ou une validité autre que 45 jours (ADR 0025, point 7). Un compte a au plus trois
sous-clés non expirées pour son certificat courant : au-delà, la publication est refusée (403, ADR 0025,
point 15) ; celles d'un ancien certificat ne comptent pas. Le serveur ne vérifie de
`ekPublic` que sa longueur : l'émetteur rejette un secret partagé nul (RFC 9180).

### 4.5 Vecteurs (QA-01, QA-02)

- `vectors/ed25519-rfc8032.json` : les tests 1 à 3 de la RFC 8032, section 7.1, en hexadécimal.
- `vectors/identity.json` : la CA, deux certificats, une preuve, une liaison, chacun avec ses champs, ses
  octets canoniques et sa signature, plus huit falsifications que toute implémentation DOIT refuser, et les 8 points
  d'ordre faible (`smallOrder`) : aucune clé ni aucun `R` pris parmi eux ne DOIT vérifier. Les dates (`u64`) y sont
  des chaînes décimales, comme en 2.5 ; les octets, du base64url. Les clés sont les graines publiques de la
  RFC 8032 (CA : test 1, IK : test 2, autre clé : test 3).

`gen_vectors.py` les signe, et chiffre le coffre de la section 5, avec la bibliothèque `cryptography`, épinglée avec
ses empreintes :

```bash
python3 -m pip install --require-hashes -r tools/requirements.txt
```

## 5. Coffre de clés (KEY-30 à KEY-33, ADR 0027)

Le trousseau est sauvegardé chiffré sur le serveur, qui ne peut pas le lire (CDC, section 6.4). La clé de coffre
VDK chiffre le trousseau ; la clé de récupération RK, que seul l'utilisateur connaît, chiffre la VDK. RK ne quitte
jamais le client (KEY-31). Aucune signature : AES-GCM authentifie chaque niveau, et son AAD l'attache au compte.

### 5.1 Clé de récupération (KEY-30)

RK vaut 20 octets de `crypto.getRandomValues`, affichés en base32 de Crockford (alphabet
`0123456789ABCDEFGHJKMNPQRSTVWXYZ`, 5 bits par caractère, poids fort d'abord) : 32 caractères en 8 groupes de 4. À la
saisie, la casse, les espaces et les tirets sont ignorés, `O` se lit `0`, `I` et `L` se lisent `1` ; `U` et tout
autre caractère sont refusés, et la clé DOIT faire exactement 32 caractères utiles.

### 5.2 Trousseau

```
{ "v": 1, "userId": "<UUID>", "ik": "<PKCS#8 d'IK>",
  "eks": [{ "ekId": "<16 octets>", "kem": "DHKEM-X25519-HKDF-SHA256", "pkcs8": "<PKCS#8 d'EK>",
            "createdAt": <ms>, "expiresAt": <ms> }],
  "createdAt": <ms> }
```

JSON compact, champs dans cet ordre, octets en base64url, dates en millisecondes Unix (nombres JSON). À l'ouverture,
DOIVENT être refusés : un champ inconnu ou manquant ; un `v` autre que 1 ; un `userId` autre que celui du compte ;
une liste `eks` vide ; un `ekId` d'une autre taille que 16 octets ; un `kem` autre que `DHKEM-X25519-HKDF-SHA256` ;
un base64url invalide ou non canonique ; une clé privée que WebCrypto refuse. Les clés publiques ne sont pas
stockées : elles se relisent dans la clé privée (le `x` de son JWK).

### 5.3 Chiffrement

```
blob       = AES-256-GCM(VDK, nonceB, trousseau,
                 AAD = struct("SEAL-vault-v1", uuid(userId), u64(vaultVersion)))
KEK        = HKDF-SHA256(ikm = RK, salt, info = struct("SEAL-kek-v1", uuid(userId)), longueur 32)
wrappedVdk = AES-256-GCM(KEK, nonceK, VDK, AAD = struct("SEAL-kek-aad-v1", uuid(userId)))
serveur   ← { vaultVersion, salt, nonceK, wrappedVdk, nonceB, blob }
```

VDK : 32 octets aléatoires ; sel de 32 octets ; nonces de 12 ; `wrappedVdk` de 48 (VDK et étiquette GCM). Chaque
nouvelle version tire un nouveau `nonceB`. Le premier coffre a la version 1, chaque écriture la suivante (ADR 0025,
point 6). Une étiquette GCM fausse, à l'un ou l'autre niveau, fait échouer l'ouverture, sans trousseau partiel.

**Limite : le rejeu d'une ancienne version.** L'AAD lie le blob à la version que porte la charge, pas à une version
attendue : une version réétiquetée est refusée, mais un serveur malveillant peut resservir une ancienne version
authentique, qui s'ouvre avec moins de sous-clés. La restauration (KEY-33) DEVRA vérifier que le trousseau restauré
contient toutes les sous-clés non expirées publiées pour le certificat courant (ADR 0027, point 10).

### 5.4 Vecteurs (QA-02)

`vectors/vault.json` donne RK et ses groupes, la VDK, les trois structures (`kekInfo`, `kekAad`, `vaultAad`), la KEK,
le trousseau et ses octets, les clés publiques attendues, la charge envoyée au serveur (`payload`, corps de
`PUT /api/v1/keys/vault`) et onze falsifications (`forgeries`) qu'aucune implémentation NE DOIT ouvrir. Chacune nomme
l'étage qui DOIT la refuser (`rejectedBy`) : `key` (la VDK ne se désenveloppe pas), `blob` (le trousseau ne se
déchiffre pas) ou `keyring` (il se déchiffre, et sa relecture stricte le refuse) ; le générateur le vérifie. IK est la graine du test 2
de la RFC 8032, EK la clé privée d'Alice de la RFC 7748 (section 6.1) : jamais de vraies clés.

`vectors/hkdf-rfc5869.json` (RFC 5869, annexe A, cas 1 à 3) et `vectors/x25519-rfc7748.json` (RFC 7748, section 6.1)
sont les vecteurs officiels des deux primitives du coffre et des sous-clés (QA-01). `gen_vectors.py` les recopie des
RFC et les recalcule avec `cryptography` avant de les écrire ; WebCrypto (Node et Chromium) et la JCA les rejouent.
