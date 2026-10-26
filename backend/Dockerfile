FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
# The complete regression suite is a separate quality gate; image builds package the same sources.
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 1000 app \
    && useradd --uid 1000 --gid 1000 --create-home --shell /usr/sbin/nologin app \
    && mkdir -p /app /data/git /data/blobs \
    && chown -R 1000:1000 /app /data
WORKDIR /app
COPY --from=build --chown=1000:1000 /build/target/knowledge-base-backend-1.0.0-SNAPSHOT.jar /app/application.jar
COPY --chmod=755 docker-entrypoint.sh /app/entrypoint.sh
USER 1000:1000
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
