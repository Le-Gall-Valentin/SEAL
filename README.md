# Projet: Application Web de Messagerie Sécurisée

**SEAL** (*Secure Encrypted Authenticated Link*) est une messagerie web chiffrée de bout en bout :
chaque message est signé en Ed25519 et chiffré en AES-256-GCM avec une clé à usage unique,
encapsulée par HPKE pour chaque destinataire. Le serveur authentifie, certifie les clés et relaie
des enveloppes qu'il ne peut pas lire.

Projet de session du cours 8INF138, Sécurité des réseaux et du Web (UQAC, automne 2026).

## Auteurs

- LEGV17030400, Le Gall, Valentin
- GIRB04080500, Girard, Benoit

## Compatibilité

- Java – 25 (Temurin 25 LTS), Spring Boot 4.1
- TypeScript – 6.0, React 19, sur Node.js 24 LTS
- PostgreSQL – 18
- Docker – Engine 24 ou plus récent, avec Compose v2
- Navigateurs – Chrome 137, Firefox 129 ou Safari 17 au minimum (Ed25519 et X25519 dans WebCrypto)

## Utilisation

### Prérequis

Pour lancer SEAL, seuls Docker et mkcert sont nécessaires :

| Outil | Rôle | Installation |
| --- | --- | --- |
| Docker avec Compose v2 | Construit et lance SEAL, PostgreSQL et Mailpit | [docs.docker.com/get-docker](https://docs.docker.com/get-docker/) |
| mkcert | Crée une autorité locale et le certificat HTTPS de `localhost` | Linux : `sudo apt install mkcert libnss3-tools` ; macOS : `brew install mkcert nss` ; Windows : `choco install mkcert` ([autres méthodes](https://github.com/FiloSottile/mkcert#installation)) |

Aucune dépendance n'est à installer à la main : l'image Docker compile le client et le serveur.

### Lancement

Depuis le dossier `chat/src` :

```bash
./scripts/gen-certs.sh
docker compose up --build
```

Sous Windows, dans PowerShell (script non testé, faute d'environnement Windows dans l'équipe) :

```powershell
powershell -ExecutionPolicy Bypass -File scripts\gen-certs.ps1
docker compose up --build
```

`gen-certs` installe l'autorité locale de mkcert (une demande de mot de passe administrateur est
possible), crée le certificat dans `certs/`, puis crée `.env` et y génère les secrets. Sous Linux,
il crée au besoin la base de certificats de Chrome (`~/.pki/nssdb`). Si le navigateur était ouvert,
redémarrez-le pour qu'il reconnaisse le certificat. Le premier lancement télécharge les images et
compile SEAL, ce qui prend quelques minutes.

Ouvrez ensuite :

- SEAL : <https://localhost:8443>
- les courriels envoyés par SEAL, capturés par Mailpit : <http://localhost:8025>

Pour arrêter SEAL : `docker compose down`. Pour effacer aussi la base et les données :
`docker compose down -v`.

## Développement

Il faut JDK 25 et Node.js 24 LTS (`sdk env` et `nvm use` lisent `.sdkmanrc` et `.nvmrc`).

| Tâche | Dossier | Commande |
| --- | --- | --- |
| Serveur, avec un PostgreSQL jetable (Testcontainers) | `src/server` | `./mvnw spring-boot:test-run` |
| Client, sur <https://localhost:5173>, relayé vers le serveur | `src/client` | `npm ci`, puis `npm run dev` |
| Navigateur des tests du client et de bout en bout, une fois après `npm ci` | `src/client` | `npx playwright install --with-deps --only-shell chromium` (sans `--with-deps` sur macOS et Windows) |
| Tests, formatage, analyse statique et couverture du serveur | `src/server` | `./mvnw verify` |
| Typage, lint, formatage et tests du client | `src/client` | `npm run lint && npm test` |
| Tests de bout en bout, SEAL lancé par docker compose | `src/client` | `npx playwright test` |
| Archive de remise `projet.zip` | `src` | `./scripts/package.sh` (Windows, non testé : `scripts\package.ps1`) |

Le serveur et les tests d'intégration ont besoin de Docker (Testcontainers), et le serveur en
développement des certificats de `src/certs`.
