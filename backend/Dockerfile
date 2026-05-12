FROM maven:3.9.9-eclipse-temurin-17

ARG APP_USER=app
ARG APP_UID=1000
ARG APP_GID=1000

RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

ENV MAVEN_CONFIG=/home/${APP_USER}/.m2
EXPOSE 8080

RUN if ! getent group "${APP_GID}" >/dev/null 2>&1; then groupadd -o -g "${APP_GID}" "${APP_USER}"; fi \
    && if ! id -u "${APP_USER}" >/dev/null 2>&1; then useradd -o -m -u "${APP_UID}" -g "${APP_GID}" -s /bin/sh "${APP_USER}"; fi

RUN mkdir -p /home/${APP_USER}/.m2 /workspace \
    && chown -R "${APP_UID}:${APP_GID}" /home/${APP_USER} /workspace

WORKDIR /workspace

USER ${APP_UID}:${APP_GID}

CMD ["tail", "-f", "/dev/null"]
