# syntax=docker/dockerfile:1
# Image de SEAL en trois étapes (section 17 du CDC) : Node compile le client, Maven compile le serveur
# qui embarque le client, et seul un JRE 25 reste à l'exécution. Images épinglées par version (A03).

# ---- Client : React, TypeScript, Vite ----
FROM node:24.22.2-alpine3.24 AS client
WORKDIR /build/client
COPY client/package.json client/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY client/ ./
# ADR 0024 (point 6 bis) : le client importe la liste de blocage d'AUTH-11 depuis src/protocol.
COPY protocol/password-blocklist.txt /build/protocol/
RUN npm run build

# ---- Serveur : Spring Boot, Maven Wrapper ----
FROM eclipse-temurin:25.0.5_8-jdk-noble AS server
# Le Maven Wrapper vérifie l'empreinte SHA-256 de l'archive .zip de Maven ; sans unzip, il se rabattrait
# sur l'archive .tar.gz, dont l'empreinte diffère. Étape de build seulement : l'image finale n'en hérite pas.
RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /build/server
COPY server/.mvn/ .mvn/
COPY server/mvnw server/pom.xml server/spotbugs-exclude.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY server/src/ src/
# AUTH-11 : la liste de blocage, partagée avec le client, et sa licence MIT sont des ressources Maven lues depuis
# ../protocol.
COPY protocol/password-blocklist.txt protocol/password-blocklist.LICENSE /build/protocol/
# Le client compilé est servi par Spring sur l'origine unique (TRN-02).
COPY --from=client /build/client/dist/ src/main/resources/static/
# Les tests et l'analyse statique tournent dans la CI (./mvnw verify) ; l'image ne fait qu'empaqueter.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package

# ---- Exécution ----
FROM eclipse-temurin:25.0.5_8-jre-noble
# Utilisateur sans privilège, d'UID fixe. /data recevra ce que SEAL génère lui-même, comme la clé de
# l'Autorité (KEY-10) : un volume, jamais une couche de l'image.
RUN groupadd --system --gid 10001 seal \
 && useradd --system --uid 10001 --gid seal --no-create-home --shell /usr/sbin/nologin seal \
 && mkdir /data \
 && chown seal:seal /data
ENV SEAL_DATA_DIR=/data
VOLUME /data
COPY --from=server /build/server/target/seal-server.jar /app/seal.jar
USER seal
EXPOSE 8443
# Sonde sur le port de gestion, joignable seulement depuis le conteneur (127.0.0.1:8081). Bash et
# /dev/tcp suffisent : l'image n'embarque ni curl ni wget.
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8081 && printf 'GET /actuator/health HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/seal.jar"]
