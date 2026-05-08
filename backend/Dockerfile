FROM maven:3.9.9-eclipse-temurin-17

ARG APP_USER=app
ARG APP_UID=1000
ARG APP_GID=1000
ARG SOURCE_MODE=local

WORKDIR /workspace

# Install git and ssh for SSH mode support
RUN apt-get update \
    && apt-get install -y --no-install-recommends git openssh-client \
    && rm -rf /var/lib/apt/lists/*

# Copy Maven project file first to maximize dependency cache reuse.
COPY --chown=${APP_UID}:${APP_GID} pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline

# Copy source - behavior depends on SOURCE_MODE
# In LOCAL mode: source is mounted at runtime
# In SSH mode: source will be cloned at startup
COPY src ./src 

ENV MAVEN_CONFIG=/home/${APP_USER}/.m2
ENV SOURCE_MODE=${SOURCE_MODE}
EXPOSE 8080

ENV HOME=/home/${APP_USER}

RUN mkdir -p /home/${APP_USER}/.m2 \
    && chown -R "${APP_UID}:${APP_GID}" /home/${APP_USER} \
    && chown -R "${APP_UID}:${APP_GID}" /workspace

# Startup script that handles both LOCAL and SSH modes
RUN cat > /workspace/startup.sh <<'EOF'
#!/bin/sh
set -e

APP_ROOT="/workspace"

if [ "$SOURCE_MODE" = "ssh" ]; then
    if [ -z "${GIT_REPO_URL:-}" ]; then
        echo "SOURCE_MODE=ssh requires GIT_REPO_URL"
        exit 1
    fi

    REPO_ROOT="${GIT_REPO_PATH:-/workspace/data/git-repo}"
    BRANCH="${GIT_BRANCH:-main}"

    mkdir -p "$REPO_ROOT"

    if [ ! -d "$REPO_ROOT/.git" ]; then
        echo "SSH mode: Выполняю полный clone репозитория в $REPO_ROOT"
        rm -rf "$REPO_ROOT"
        git clone -b "$BRANCH" "$GIT_REPO_URL" "$REPO_ROOT"
    elif [ "${AUTO_PULL:-false}" = "true" ]; then
        echo "SSH mode: Обновляю репозиторий (AUTO_PULL=true)"
        git -C "$REPO_ROOT" fetch origin "$BRANCH"
        git -C "$REPO_ROOT" checkout "$BRANCH"
        git -C "$REPO_ROOT" pull --ff-only origin "$BRANCH"
    fi

    APP_ROOT="$REPO_ROOT/backend"
fi

if [ ! -f "$APP_ROOT/pom.xml" ]; then
    echo "pom.xml not found at $APP_ROOT"
    exit 1
fi

echo "Starting Maven Spring Boot from $APP_ROOT..."
mkdir -p /tmp/m2/repository
cd "$APP_ROOT"
exec mvn -B -Dmaven.repo.local=/tmp/m2/repository spring-boot:run
EOF
RUN chmod +x /workspace/startup.sh && \
    chown "${APP_UID}:${APP_GID}" /workspace/startup.sh

USER ${APP_UID}:${APP_GID}

# Dev runtime with Spring profile configured from compose env.
CMD ["/workspace/startup.sh"]
