FROM maven:3.9.9-eclipse-temurin-17

ARG APP_USER=app
ARG APP_UID=1000
ARG APP_GID=1000
ARG SOURCE_MODE=local

# Install git and ssh for SSH mode support
RUN apt-get update \
    && apt-get install -y --no-install-recommends git openssh-client bash-completion \
    && rm -rf /var/lib/apt/lists/*


# Copy source - behavior depends on SOURCE_MODE
# In LOCAL mode: source is mounted at runtime
# In SSH mode: source will be cloned at startup

ENV MAVEN_CONFIG=/home/${APP_USER}/.m2
ENV SOURCE_MODE=${SOURCE_MODE}
EXPOSE 8080

ENV HOME=/home/${APP_USER}

RUN if ! getent group "${APP_GID}" >/dev/null 2>&1; then groupadd -o -g "${APP_GID}" "${APP_USER}"; fi \
    && if ! id -u "${APP_USER}" >/dev/null 2>&1; then useradd -o -m -u "${APP_UID}" -g "${APP_GID}" -s /bin/sh "${APP_USER}"; fi

RUN mkdir -p /home/${APP_USER}/.m2 /workspace \
    && chown -R "${APP_UID}:${APP_GID}" /home/${APP_USER} \
    && chown "${APP_UID}:${APP_GID}" /workspace \
    && chmod 755 /home/${APP_USER} /workspace

# Startup script that handles both LOCAL and SSH modes
RUN cat > /usr/local/bin/startup <<'EOF'
#!/bin/sh
set -e

BRANCH="${GIT_BRANCH:-main}"

cd /workspace

if [ "$SOURCE_MODE" = "local" ]; then
    # LOCAL mode: source is mounted from host or GIT_REPO_URL is provided
    if [ -n "${GIT_REPO_URL:-}" ]; then
        # Convert SSH URL to HTTPS if needed: git@github.com:owner/repo.git -> https://github.com/owner/repo.git
        HTTPS_URL="$GIT_REPO_URL"
        if printf '%s' "$GIT_REPO_URL" | grep -q "^git@"; then
            HTTPS_URL=$(printf '%s' "$GIT_REPO_URL" | sed 's#^git@\([^:]*\):\(.*\)\.git$#https://\1/\2.git#')
        fi

        if [ ! -d ".git" ]; then
            echo "LOCAL mode: Инициализирую репозиторий в /workspace"
            git init
            git remote add origin "$HTTPS_URL"
            git fetch --prune origin "$BRANCH"
            git checkout -f -B "$BRANCH" "origin/$BRANCH"
            echo "LOCAL mode: Репозиторий загружен"
        else
            echo "LOCAL mode: Репозиторий уже в /workspace, обновляю"
            git remote set-url origin "$HTTPS_URL"
            git fetch --prune origin "$BRANCH"
            if [ "${AUTO_PULL:-false}" = "true" ]; then
                git checkout -f "$BRANCH"
                git pull --ff-only origin "$BRANCH"
            fi
        fi
    else
        echo "LOCAL mode: Ожидаю исходный код в /workspace (монтируется с хоста)"
        echo "LOCAL mode: Если код еще не готов, монтируйте директорию или установите GIT_REPO_URL"
    fi

elif [ "$SOURCE_MODE" = "ssh" ]; then
    if [ -z "${GIT_REPO_URL:-}" ]; then
        echo "SOURCE_MODE=ssh requires GIT_REPO_URL"
        exit 1
    fi

    GIT_DIR="${GIT_DIR:-/tmp/repo.git}"
    export GIT_DIR
    export GIT_WORK_TREE="/workspace"

    if [ -z "${SSH_AUTH_SOCK:-}" ] || [ ! -S "$SSH_AUTH_SOCK" ]; then
        echo "SOURCE_MODE=ssh requires a reachable SSH_AUTH_SOCK and loaded SSH key"
        exit 1
    fi

    REPO_HOST="${GIT_HOST:-$(printf '%s' "$GIT_REPO_URL" | sed -n 's#.*@\([^:/]*\).*#\1#p')}"
    if [ -z "$REPO_HOST" ]; then
        echo "SOURCE_MODE=ssh requires a parseable host in GIT_REPO_URL or explicit GIT_HOST"
        exit 1
    fi

    mkdir -p "$HOME/.ssh"
    chmod 700 "$HOME/.ssh"
    if ! ssh-keygen -F "$REPO_HOST" >/dev/null 2>&1; then
        echo "SSH mode: Добавляю host key для $REPO_HOST"
        ssh-keyscan -H "$REPO_HOST" >> "$HOME/.ssh/known_hosts" 2>/dev/null || {
            echo "SOURCE_MODE=ssh requires known_hosts access for $REPO_HOST"
            exit 1
        }
    fi

    if [ ! -d "$GIT_DIR/objects" ]; then
        echo "SSH mode: Инициализирую отдельный git-dir в $GIT_DIR"
        rm -rf "$GIT_DIR"
        git init "$GIT_DIR"
        git --git-dir="$GIT_DIR" remote add origin "$GIT_REPO_URL"
    else
        git --git-dir="$GIT_DIR" remote set-url origin "$GIT_REPO_URL" 2>/dev/null \
            || git --git-dir="$GIT_DIR" remote add origin "$GIT_REPO_URL"
    fi

    echo "SSH mode: Загружаю содержимое репозитория в /workspace"
    git --git-dir="$GIT_DIR" fetch --prune origin "$BRANCH"
    git --git-dir="$GIT_DIR" --work-tree="/workspace" checkout -f -B "$BRANCH" "origin/$BRANCH"

    if [ "${AUTO_PULL:-false}" = "true" ]; then
        echo "SSH mode: AUTO_PULL=true, репозиторий будет обновляться при каждом старте"
    fi
fi

tail -f /dev/null
EOF
RUN chmod +x /usr/local/bin/startup && chmod 755 /usr/local/bin/startup

# Override Maven base image entrypoint with our startup script
ENTRYPOINT ["/usr/local/bin/startup"]
CMD []

USER ${APP_UID}:${APP_GID}
