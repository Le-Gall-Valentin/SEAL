# Protocole SEAL v1

Ce document est la référence publique du protocole de SEAL : conformément au principe de Kerckhoffs,
sa sécurité ne repose que sur les clés. Il précise, à l'octet près, les sections 6 à 9 du cahier des
charges ; client (TypeScript) et serveur (Java) DOIVENT produire exactement les encodages décrits ici.

Les mots DOIT, NE DOIT PAS et PEUT ont le sens de la RFC 2119. Les sections sur les groupes et la
non-répudiation s'ajouteront avec leurs lots ; toute modification d'un format met à
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

**Version d'Unicode (ADR 0006, point 2).** NFC dépend de la version d'Unicode du moteur.
- La référence est Unicode 15.0, celle du générateur des vecteurs, et toute implémentation testée est au moins
  à cette version.
- Un texte fait de caractères attribués dans une version garde le même verdict NFC dans toutes les versions
  suivantes. Le générateur s'arrête donc sur tout caractère non attribué dans la sienne.
- Limite admise : un texte qui contient un caractère inconnu du moteur de l'émetteur peut être jugé hors NFC par
  un lecteur plus récent. Le message est alors refusé (`integrity_failed`), jamais affiché.

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
indépendante de Java et de TypeScript : bibliothèque standard seule, plus `cryptography` 50.0.2 pour
les signatures de la section 4, le coffre de la section 5 et l'HPKE de la section 6. Pour les régénérer,
depuis `src/protocol` :

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
est une réinitialisation, réservée au mode sudo (KEY-12, KEY-34, ADR 0025, point 4 ; section 5.6).

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

`GET /api/v1/users/{userId}/keys` sert les sous-clés sans leur `certSerial` : ce sont celles du certificat servi avec
elles, et un client qui vérifie l'une de ces liaisons la reconstruit avec le numéro de série de ce certificat
(ADR 0029, point 4).

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
authentique, qui s'ouvre avec moins de sous-clés. La restauration (KEY-33) et la reprise d'une publication
(ADR 0029, point 3) vérifient donc que le trousseau ouvert contient toutes les sous-clés non expirées que
`GET /api/v1/users/{moi}/keys` sert pour le certificat courant (ADR 0027, point 10) ; sinon le coffre est refusé.
Ce contrôle ne détecte qu'un serveur incohérent : un serveur qui resservirait une ancienne version en taisant, de façon
cohérente, les sous-clés publiées depuis (ou en répondant 404 sur les clés) ferait perdre à l'appareil restauré les
messages chiffrés pour elles. Ni la confidentialité ni l'IK, authentifiée par RK, ne sont atteintes.

### 5.4 Vecteurs (QA-02)

`vectors/vault.json` donne RK et ses groupes, la VDK, les trois structures (`kekInfo`, `kekAad`, `vaultAad`), la KEK,
le trousseau et ses octets, les clés publiques attendues, la charge envoyée au serveur (`payload`, corps de
`PUT /api/v1/keys/vault`) et onze falsifications (`forgeries`) qu'aucune implémentation NE DOIT ouvrir. Chacune nomme
l'étage qui DOIT la refuser (`rejectedBy`) : `key` (la VDK ne se désenveloppe pas), `blob` (le trousseau ne se
déchiffre pas) ou `keyring` (il se déchiffre, et sa relecture stricte le refuse) ; le générateur le vérifie. IK est la
graine du test 2 de la RFC 8032, EK la clé privée d'Alice de la RFC 7748 (section 6.1) : jamais de vraies clés.

`vectors/hkdf-rfc5869.json` (RFC 5869, annexe A, cas 1 à 3) et `vectors/x25519-rfc7748.json` (RFC 7748, section 6.1)
sont les vecteurs officiels des deux primitives du coffre et des sous-clés (QA-01). `gen_vectors.py` les recopie des
RFC et les recalcule avec `cryptography` avant de les écrire ; WebCrypto (Node et Chromium) et la JCA les rejouent.

### 5.5 Version suivante (KEY-32, ADR 0029)

Une nouvelle sous-clé (rotation, ou reprise d'une publication interrompue) s'écrit dans une nouvelle version du coffre,
sans la clé de récupération : le client déchiffre le blob avec la VDK de son trousseau, ajoute la sous-clé au trousseau
et le rechiffre avec la même VDK, sous l'AAD de la version suivante (`vaultVersion + 1`) et avec un `nonceB` neuf.
`salt`, `nonceK` et `wrappedVdk` ne changent pas ; seules la régénération de RK (KEY-35) et la réinitialisation (5.6)
les renouvellent. Le trousseau relu DOIT contenir toutes les sous-clés du trousseau de l'appareil : une version plus
ancienne, resservie, en perdrait. Le serveur n'accepte que la version qui suit l'enregistrée (409
`VAULT_VERSION_CONFLICT` sinon).

**Sous-clé jamais publiée (ADR 0029, point 11).** Quand la sous-clé la plus récente a plus de 4 minutes, la reprise
la redate au lieu d'en ajouter une, si elle est tenue pour jamais publiée : `GET /api/v1/users/{moi}/keys` ne la sert
pas, et elle a été créée plus de 5 minutes après le `notBefore` du certificat servi et il y a moins de 30 jours
(`createdAt` et horloge de l'appareil). Une sous-clé d'un certificat précédent, ou expirée, n'est plus servie mais a pu
servir : les deux bornes la gardent, et une sous-clé neuve est ajoutée. Redater écrit, dans la version suivante, la
même sous-clé (même `ekId`, même clé) avec `createdAt` à l'heure de l'appareil et 45 jours de validité ; aucune
sous-clé n'est retirée, et la relecture garde tout son contrôle anti-rejeu. Des reprises qui échouent (horloge
décalée, panne, refus du serveur) ne font donc plus grossir le coffre. Une erreur de jugement, par un serveur qui tait
une sous-clé publiée ou par un autre appareil qui la publie au même moment, ne retire rien : le serveur refuse de
republier un `ekId` (400 `VALIDATION_FAILED`), et la reprise suivante relit la sous-clé publiée. Les dates du coffre
diffèrent alors de la liaison publiée, qui seule fait foi pour les correspondants. Un serveur qui continue de taire la
sous-clé bloque la reprise, ce qu'il peut déjà faire en refusant toute publication.

Un client qui reçoit 409 relit le coffre : identique à celui qu'il envoyait, il a été écrit par une requête dont la
réponse s'est perdue. Différent au premier coffre, un autre appareil a créé le trousseau entre-temps, et le client
passe à la restauration (KEY-33) ; différent à une version suivante, la reprise s'arrête en erreur, et la suivante
repart du coffre relu.

`vectors/vault.json` donne la version 2 dans `nextVersion` : l'AAD (`vaultAad`), les octets du trousseau à deux
sous-clés (`keyringBytes`), la clé publique de la seconde (`ekPublic`, celle de Bob dans la RFC 7748) et la charge
(`payload`), qui s'ouvre aussi par RK.

### 5.6 Réinitialisation de l'identité (KEY-34, ADR 0036)

Sans sa clé de récupération, un compte remplace son identité : le client tire une nouvelle RK, la fait confirmer
(5.1), puis génère une nouvelle IK, une nouvelle EK et une nouvelle VDK, et chiffre le nouveau trousseau comme en 5.3,
sous l'AAD de la version qui suit celle du coffre enregistré (`vaultVersion + 1`). Il envoie en une seule requête,
`POST /api/v1/keys/identity/reset`, en mode sudo, la preuve de possession de la nouvelle IK (4.3, sur un défi neuf) et
le nouveau coffre. Le serveur, dans une transaction, consomme le défi, vérifie la preuve, écrit le coffre s'il suit la
version enregistrée (409 `VAULT_VERSION_CONFLICT` sinon), révoque le certificat courant, motif « remplacé » (code 4),
et émet celui de la nouvelle IK. La sous-clé se publie ensuite comme à la première connexion (4.4, 5.5).

Le trousseau n'est enregistré sur l'appareil qu'une fois la réinitialisation acquise : une réponse perdue se tranche
en relisant `GET /api/v1/users/{moi}/keys`, dont le certificat porte alors la nouvelle IK. L'ancienne RK n'ouvre plus
le coffre ; les messages chiffrés pour les anciennes sous-clés deviennent illisibles, et les correspondants voient une
nouvelle IK (KEY-51). Les formats ne changent pas : aucun vecteur nouveau.

## 6. Messages (CDC 7 ; KEY-20, QA-02 à QA-04, ADR 0030)

Chaque message est signé par son auteur, chiffré avec une clé de contenu (CEK) à usage unique, et cette CEK est
encapsulée par HPKE pour chaque destinataire, émetteur compris. Le serveur ne voit que l'enveloppe.

### 6.1 Suite et enveloppe

Suite `SEAL1-X25519-A256GCM-ED25519` : signature Ed25519 ; encapsulation HPKE (RFC 9180) en mode base,
DHKEM(X25519, HKDF-SHA256), HKDF-SHA256, AES-256-GCM ; chiffrement du message en AES-256-GCM. L'enveloppe :

```json
{
  "v": 1,
  "suite": "SEAL1-X25519-A256GCM-ED25519",
  "messageId": "UUIDv7 généré par le client",
  "conversationId": "UUID",
  "senderId": "UUID",
  "senderCertSerial": "base64url, 16 octets",
  "nonce": "base64url, 12 octets",
  "ciphertext": "base64url",
  "wraps": [
    { "recipientId": "UUID", "ekId": "base64url, 16 octets",
      "enc": "base64url, 32 octets", "wrappedCek": "base64url, 48 octets" }
  ]
}
```

### 6.2 Structures

```
SignedPayload = struct("SEAL-msg-v1",
    u8(v), text(suite), uuid(messageId), uuid(conversationId),
    u8(conversationType),             // 1 : direct, 2 : groupe
    uuid(senderId), bytes(senderCertSerial),
    u64(sentAt),                      // horloge de l'émetteur, ms
    u64(senderSeq),                   // compteur de l'émetteur dans cette conversation, dès 1
    u64(groupSeq),                    // dernière opération de groupe connue, 0 en direct
    bytes(salt),                      // 16 octets aléatoires
    list(uuid, recipientIds),         // triés par ordre des octets
    text(contentType),                // "text/plain" en v1
    text(body))                       // 4 000 points de code au plus après NFC
signature  = Ed25519.sign(IK de l'émetteur, SignedPayload)
Plaintext  = bytes(SignedPayload) || bytes(signature) || bytes(padding)
AAD        = struct("SEAL-aad-v1", u8(v), text(suite), uuid(messageId),
                 uuid(conversationId), uuid(senderId), bytes(senderCertSerial))
ciphertext = AES-256-GCM(CEK, nonce, Plaintext, AAD)
info_r     = struct("SEAL-wrap-v1", text(suite), uuid(messageId),
                 uuid(conversationId), uuid(recipientId), bytes(ekId))
(enc, wrappedCek) = HPKE.SealBase(pkR = EK_r, info = info_r, aad = AAD, pt = CEK)
```

- `messageId` est un UUIDv7 dont l'horodatage est `sentAt` ; CEK de 32 octets, nonce de 12 et sel de 16, tirés par
  `crypto.getRandomValues` à chaque message.
- `recipientIds` compte de 1 à 50 identifiants, triés par ordre des octets et distincts ; l'émetteur y figure.
- `senderSeq` commence à 1 ; `groupSeq` vaut 0 en conversation directe. Le scellement refuse ce que la relecture
  refuserait, ces règles comprises (ADR 0030, point 4).
- Le corps se compte en points de code après NFC : un émoji compte pour un (ADR 0030, point 6).
- `padding` est vide au scellement en P0 ; à la lecture, toute longueur est acceptée et ignorée, dans la limite de
  MSG-21 (chiffré de 24 576 octets au plus) : il est chiffré mais non signé, et n'a aucun sens (ADR 0030, point 7).
- La CEK, `Plaintext` et `SignedPayload` sont remis à zéro dès qu'ils ont servi, refus compris : chaque copie que SEAL
  maîtrise, tampons de l'encodage canonique compris, et la CEK confiée à `@hpke` en `ArrayBuffer` exact, que la
  bibliothèque ne recopie pas. La CEK n'existe
  qu'en mémoire. Restent hors de portée : les chaînes JavaScript (le corps décodé), les copies internes de WebCrypto
  et le contenu rendu au lecteur (ADR 0030, point 9).

### 6.3 Sous-clé d'un destinataire (KEY-20)

L'émetteur relit `GET /api/v1/users/{userId}/keys` et vérifie :

1. le certificat : signature de l'Autorité épinglée, sujet égal au destinataire, usage, validité à cet instant avec
   5 minutes de tolérance d'horloge ;
2. chaque liaison de sous-clé, par l'IK du certificat, sur `SubkeyBinding` avec le numéro de série de ce
   certificat (4.4) : `kem` attendu, 45 jours exacts, `createdAt` au plus 5 minutes après cet instant (le serveur
   refuse une sous-clé datée dans le futur, ADR 0025, point 7).

Il prend, parmi les sous-clés non expirées, celle dont `createdAt` est le plus récent. Un certificat ou une seule
liaison invalides refusent toutes les clés du destinataire, et l'envoi est annulé (« clé indisponible ») : une
signature fausse n'est jamais normale (ADR 0030, point 5). Le scellement n'accepte que les sous-clés ainsi choisies,
et les refuse à leur tour si la sous-clé ou son certificat ont expiré à `sentAt` (ADR 0030, point 8).

### 6.4 Scellement (CDC 7.5, étapes 3 à 7)

1. `SignedPayload` : `messageId` en UUIDv7, `sentAt`, `senderSeq`, `groupSeq`, un sel neuf, les destinataires triés.
2. Signature par l'IK de l'émetteur.
3. CEK et nonce neufs ; `Plaintext` chiffré en AES-256-GCM sous l'AAD.
4. CEK encapsulée pour chaque destinataire, émetteur compris, par la sous-clé choisie en 6.3.
5. CEK, `Plaintext` et `SignedPayload` remis à zéro, avec leurs copies intermédiaires (6.2).

### 6.5 Ouverture (CDC 7.7)

Les vérifications s'exécutent dans cet ordre et échouent fermé : le contenu n'est rendu qu'aux statuts `verified` et
`recipients_mismatch`, ce dernier affiché sous l'avertissement ambre de la section 7.7 du CDC (ADR 0030, point 4).

| Étape | Vérification | Échec |
| --- | --- | --- |
| 1 | Enveloppe lisible : objet strict (aucun champ inconnu), UUID en minuscules, base64url canonique, tailles, `v` = 1, suite connue ; MSG-21 : chiffré de 24 576 octets au plus, 50 encapsulations au plus, une par destinataire | `integrity_failed` |
| 3 | L'encapsulation pour soi, dont l'`ekId` est dans le trousseau, ouverte par HPKE | `undecryptable` |
| 4 | `Plaintext` déchiffré par AES-256-GCM sous l'AAD | `integrity_failed` |
| 5 | `SignedPayload` relu strictement, invariants de 6.2 compris (`messageId` UUIDv7 daté de `sentAt`, `senderSeq` ≥ 1, `groupSeq` = 0 en direct, émetteur parmi les destinataires) ; version, suite, identifiants et numéro de série égaux à l'enveloppe | `integrity_failed` |
| 6 | Certificat de l'émetteur : Autorité, sujet, numéro de série, usage, validité à `serverReceivedAt` (un instant qui n'est pas un entier positif sûr est refusé, jamais ignoré) | `signature_invalid` |
| 7 | Signature Ed25519 de `SignedPayload` par l'IK du certificat | `signature_invalid` |
| 8 | Le lecteur figure dans `recipientIds` | `recipients_mismatch` |

L'AAD et `info_r` portent l'en-tête : un en-tête modifié par le serveur fait échouer HPKE dès l'étape 3
(`undecryptable`). Le client du lot 4 complète ces étapes avec l'état de la conversation : message déjà traité
(étape 2), membres attendus à `groupSeq` (étape 8), séquence et horloge (étapes 9 et 10), registre des contacts
(KEY-50) et révocation.

### 6.6 Vecteurs (QA-02, QA-04)

`vectors/envelope.json` donne un message d'un compte à lui-même et à Bob : les champs de `SignedPayload` (u64 en
chaîne décimale), ses octets et sa signature, la CEK, le nonce, `Plaintext`, l'AAD, le chiffré, chaque `info_r`,
`enc` et `wrappedCek`, l'enveloppe et son `serverReceivedAt` (`receivedAt`, chaîne décimale). L'émetteur est celui
de `identity.json` et de `vault.json` (IK : graine du test 2 de la RFC 8032 ; EK : clé privée d'Alice de la RFC
7748) ; l'EK de Bob dérive de « SEAL vecteurs, EK de Bob » (DeriveKeyPair), sans clé commune avec `vault.json`. Les
clés éphémères dérivent de `ikmE` (DeriveKeyPair, RFC 9180, 7.1.3), pour que chaque implémentation reproduise les
encapsulations à l'octet près. Hors du message principal, chaque enveloppe chiffre sous une CEK et un nonce propres.

L'API HPKE de `cryptography` ne prend pas d'AAD : `gen_vectors.py` compose le mode base de la RFC 9180 avec ses
primitives, se vérifie contre le vecteur A.1.1 de la RFC 9180, puis contre `cryptography.hpke` avec une AAD vide, dans
les deux sens (ADR 0030, point 2). Il ouvre ensuite chaque enveloppe selon 6.5 et refuse de publier un vecteur dont le
statut diffère de celui qu'il annonce ; ses vérifications de signature refusent, comme celles du client, une clé ou
un R d'ordre faible (section 4). Une falsification peut porter le certificat que sert le serveur pour elle
(`senderCertificate`). `accepted` donne des enveloppes qui s'ouvrent : padding de 7 octets, chiffré de
24 576 octets exactement. `forgeries` donne des enveloppes que Bob NE DOIT PAS accepter, chacune avec le statut
attendu (`rejectedBy`) : champ inconnu dans l'enveloppe ou dans une encapsulation ; `messageId` en majuscules ; deux
encapsulations pour Bob ; 51 encapsulations pour 51 destinataires distincts ; chiffré de 24 577 octets, pourtant
signé ; `messageId` UUIDv4, de version 8 ou de variante 11 datés de `sentAt`, ou UUIDv7 d'une autre milliseconde ;
`senderSeq` nul ; `groupSeq` non nul en direct ; émetteur absent des destinataires ; 51 destinataires ; `Plaintext`
sans son champ de padding, ou avec un octet après lui ; IK d'ordre faible certifiée, avec une signature universelle ;
numéro de série non canonique dans le certificat servi ; octet du chiffré, du
nonce, de `enc` ou de `wrappedCek` ; en-tête déplacé dans une autre conversation ; message signé rechiffré par Bob
dans une autre conversation (transfert subreptice), sous un autre `messageId`, sous un autre émetteur annoncé ou sous
un autre numéro de certificat ; signature d'une autre IK ; signature altérée ; Bob absent des
destinataires signés ; destinataires non triés, pourtant signés.

## 7. Trames WebSocket (CDC 12.2 ; TRN-10 à TRN-15, ADR 0043)

`/ws` est une WebSocket brute (pas de STOMP), ouverte à la seule session complète d'un compte actif dont la
poignée de main porte un en-tête `Origin` égal à l'origine de SEAL ; sinon 403 (TRN-11). Chaque trame est un texte
JSON :

```json
{ "type": "ping", "id": "choisi-par-le-client", "payload": {} }
```

- `type` : nom de la trame, en minuscules et points (`msg.send`) ; `id` : 1 à 64 caractères `[A-Za-z0-9_-]`,
  choisi par l'émetteur de la trame ; `payload` : un objet, vide s'il est absent. Tout autre champ est refusé.
- Le serveur répond à chaque trame du client au même `id` : `pong` à `ping` ; en cas de succès, l'accusé que nomme
  le type (`ack` par défaut, `msg.ack` pour `msg.send`, ADR 0044), avec sa charge propre ; sinon `error`
  (`{ "code", "retryAfter"? }`, code de la section 14.6 du CDC). Un type inconnu reçoit `error` `VALIDATION_FAILED`.
- Une chaîne UTF-16 mal formée (surrogat isolé, permis par un échappement JSON) fait refuser la trame : fermeture 1008.
- Une trame de plus de 64 Kio, comptés en octets UTF-8, ferme la connexion (TRN-13).
- La connexion est liée à sa session HTTP (TRN-12). Elle la suit quand la session change d'identifiant pour le même
  compte (ré-authentification, AUTH-23, AUTH-55) ; elle se ferme quand la session finit ou ne porte plus la session
  complète de ce compte.
- Une trame née d'une action de l'utilisateur (`msg.send`, puis `read`) prolonge la session HTTP si son dernier accès
  date d'une minute au moins ; `ping` et les autres trames ne la prolongent jamais (AUTH-52, ADR 0043). Sur une
  session finie, aucune trame autre que `ping` n'est traitée ni refusée pour son type : le serveur envoie
  `session.revoked`, puis ferme en 4401.
- Les trames du serveur (`session.revoked`, puis les événements) portent un `id` qu'il choisit.

Événements annoncés par le serveur, après validation de la transaction qui les cause, à toutes les connexions des
comptes concernés ; un compte hors ligne relit par l'API (ADR 0043) :

| Trame | Charge | Destinataires |
| --- | --- | --- |
| `friend.request` | `{ "requestId" }` | Le destinataire d'une invitation (FRD-04) |
| `friend.update` | `{ "userId" }`, l'autre compte | Les deux comptes : acceptation, refus, révocation, retrait (FRD-11) |
| `conv.update` | `{ "conversationId" }` | Chaque membre, anciens compris : création, ajout, retrait, départ, renommage (CNV-04, GRP-03) |

| Code de fermeture | Cause |
| --- | --- |
| 4401 | Session finie : déconnexion, révocation, expiration (TRN-12), précédée de `session.revoked` |
| 4408 | Aucune trame depuis 60 secondes |
| 4429 | Sixième connexion d'un même compte |
| 1011 | Envoi impossible : client trop lent (plus de 10 secondes ou de 512 Kio en attente) (ADR 0044) |
| 1009 | Trame de plus de 64 Kio |
| 1008 | Trame illisible, à champ inconnu ou binaire |
