FROM maven:3.9.9-eclipse-temurin-17

ARG APP_USER=app
ARG APP_UID=1000
ARG APP_GID=1000

# Ensure the host-mapped UID/GID can be used even if that GID already exists
# in the base image (common on Debian-based images).
RUN set -e; \
    groupadd --gid "${APP_GID}" "${APP_USER}" >/dev/null 2>&1 || true; \
    if ! id -u "${APP_USER}" >/dev/null 2>&1; then \
      useradd \
        --uid "${APP_UID}" \
        --gid "${APP_GID}" \
        --home-dir /home/"${APP_USER}" \
        --create-home \
        --shell /bin/sh \
        "${APP_USER}"; \
    fi

WORKDIR /workspace

# Copy Maven project file first to maximize dependency cache reuse.
COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline

# Copy source after dependencies layer.
COPY src ./src

ENV MAVEN_CONFIG=/home/${APP_USER}/.m2
EXPOSE 8080

USER ${APP_USER}

# Dev runtime with Spring profile configured from compose env.
CMD ["mvn", "-B", "spring-boot:run"]
