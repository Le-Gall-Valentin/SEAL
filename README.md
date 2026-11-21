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

- SEAL : <https://localhost:8443>, qui s'ouvre sur l'écran de connexion ; « Créer un compte » mène à
  l'inscription ;
- les courriels envoyés par SEAL, capturés par Mailpit : <http://localhost:8025>, où arrivent le code de
  vérification du courriel et le lien de réinitialisation du mot de passe.

À la première connexion d'un compte, SEAL affiche sa clé de récupération (8 groupes de 4 caractères) et la fait
confirmer avant de générer et de publier les clés (KEY-02, KEY-30). Notez-la : sur un nouveau navigateur, ou après
avoir effacé les données du site, c'est elle qui restaure les clés du compte (KEY-33), et SEAL ne peut pas la retrouver.

Pour arrêter SEAL : `docker compose down`. Pour effacer aussi la base et les données :
`docker compose down -v`.

Au premier démarrage, SEAL génère la clé de son Autorité de certification dans le volume `app-data`
(`/data/ca`) et écrit son empreinte dans le journal : `docker compose logs app | grep Autorité`. Ce volume
est à sauvegarder : `docker compose down -v` l'efface aussi, et une nouvelle Autorité rend invalides tous les
certificats déjà émis.

Chaque navigateur épingle la clé de l'Autorité au premier usage (KEY-11). Après une nouvelle Autorité
(`docker compose down -v`, ou volume `app-data` perdu), un navigateur déjà venu se bloque donc sur « Autorité SEAL
modifiée » : c'est voulu, une autre clé pouvant être celle d'un attaquant. Pour repartir en local, effacez les données
du site `https://localhost:8443` dans ce navigateur (dans Chrome : outils de développement, onglet Application,
« Clear site data »). Cela efface aussi le trousseau du navigateur : la connexion suivante demande la clé de
récupération du compte, ou un nouveau compte si la base a été effacée.

Le fichier `.env` est à sauvegarder lui aussi : il porte le poivre des mots de passe (`SEAL_PASSWORD_PEPPER`,
ADR 0026), gardé hors de la base. Sans lui, aucun mot de passe enregistré ne se vérifie plus, et chaque compte
devrait passer par « mot de passe oublié ».

### Connexion avec Google et GitHub

La connexion par un fournisseur est facultative. Sans application OAuth, SEAL démarre normalement et masque le bouton
du fournisseur (OPS-03). Pour l'activer, créez l'application chez le fournisseur, renseignez son identifiant et son
secret dans `.env`, puis relancez `docker compose up --build`.

- **Google** : Google Cloud Console, « API et services ». Configurez d'abord l'écran de consentement OAuth (type
  « Externe ») et, tant que l'application n'est pas publiée, ajoutez-y les comptes de démonstration comme utilisateurs
  de test. Puis « Identifiants », « ID client OAuth », type « Application Web » :
  - URI de redirection autorisée : `https://localhost:8443/login/oauth2/code/google` ;
  - variables : `SEAL_OAUTH_GOOGLE_CLIENT_ID` et `SEAL_OAUTH_GOOGLE_CLIENT_SECRET`.
- **GitHub** : Settings, « Developer settings », « OAuth Apps », « New OAuth App » :
  - Homepage URL : `https://localhost:8443` ;
  - Authorization callback URL : `https://localhost:8443/login/oauth2/code/github` ;
  - variables : `SEAL_OAUTH_GITHUB_CLIENT_ID` et `SEAL_OAUTH_GITHUB_CLIENT_SECRET`.

Renseignez les deux valeurs d'un fournisseur, ou aucune. Une seule arrête le démarrage, avec un message qui nomme la
variable manquante.

Le compte SEAL est lié au compte du fournisseur, jamais à son courriel. À la première connexion, SEAL fait choisir
l'identifiant, puis crée le compte. Si le courriel vérifié du fournisseur appartient déjà à un compte SEAL, aucune
liaison n'est faite : connectez-vous par votre méthode habituelle (AUTH-33). La liaison d'un fournisseur depuis les
paramètres viendra plus tard (ACC-04).

## Développement

Il faut JDK 25 et Node.js 24 LTS (`sdk env` et `nvm use` lisent `.sdkmanrc` et `.nvmrc`).

| Tâche | Dossier | Commande |
| --- | --- | --- |
| Serveur, avec un PostgreSQL jetable (Testcontainers) et, sans `SEAL_PASSWORD_PEPPER`, un poivre jetable | `src/server` | `./mvnw spring-boot:test-run` |
| Client, sur <https://localhost:5173>, relayé vers le serveur | `src/client` | `npm ci`, puis `npm run dev` |
| Navigateur des tests du client et de bout en bout, une fois après `npm ci` | `src/client` | `npx playwright install --with-deps --only-shell chromium` (sans `--with-deps` sur macOS et Windows) |
| Tests, formatage, analyse statique et couverture du serveur | `src/server` | `./mvnw verify` |
| Typage, lint, formatage et tests du client | `src/client` | `npm run lint && npm test` |
| Tests de bout en bout, SEAL lancé par docker compose | `src/client` | `npx playwright test` |
| Archive de remise `projet.zip` | `src` | `./scripts/package.sh` (Windows, non testé : `scripts\package.ps1`) |

Le serveur et les tests d'intégration ont besoin de Docker (Testcontainers), et le serveur en
développement des certificats de `src/certs`. Lancé depuis `src/server`, il range la clé de son Autorité dans
`src/data/ca`, hors de Git et de `projet.zip` ; les tests, dans `src/server/target/test-data`.
