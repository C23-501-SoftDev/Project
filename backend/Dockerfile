FROM maven:3.9.9-eclipse-temurin-17

ARG APP_USER=app
ARG APP_UID=1000
ARG APP_GID=1000

RUN apt-get update \
    && apt-get install -y --no-install-recommends git nodejs npm \
    && rm -rf /var/lib/apt/lists/*

RUN npm install -g openspec

ENV MAVEN_CONFIG=/home/${APP_USER}/.m2
EXPOSE 8080

RUN if ! getent group "${APP_GID}" >/dev/null 2>&1; then groupadd -o -g "${APP_GID}" "${APP_USER}"; fi \
    && if ! id -u "${APP_USER}" >/dev/null 2>&1; then useradd -o -m -u "${APP_UID}" -g "${APP_GID}" -s /bin/sh "${APP_USER}"; fi

RUN mkdir -p /home/${APP_USER}/.m2 /workspace \
    && chown -R "${APP_UID}:${APP_GID}" /home/${APP_USER} /workspace

RUN mkdir -p /home/${APP_USER}/.m2 /workspace \
    && chown -R "${APP_UID}:${APP_GID}" /home/${APP_USER} /workspace

# Создаём startup скрипт динамически через printf
RUN printf '#!/bin/sh\n\nif [ -n "$GIT_REPO_SSH_URL" ]; then\n    echo "[startup.sh] Переключаем git remote на SSH: $GIT_REPO_SSH_URL"\n    cd /workspace\n    git remote set-url origin "$GIT_REPO_SSH_URL"\n    echo "[startup.sh] Git remote успешно обновлен"\nelse\n    echo "[startup.sh] GIT_REPO_SSH_URL не задана, пропускаем переключение remote"\nfi\n\ntail -f /dev/null\n' > /startup.sh && chmod +x /startup.sh

WORKDIR /workspace

USER ${APP_UID}:${APP_GID}

CMD ["/startup.sh"]
