FROM maven:3.9.9-eclipse-temurin-17

ARG APP_USER=app
ARG APP_UID=1000
ARG APP_GID=1000
ARG SOURCE_MODE=local

RUN addgroup -g "${APP_GID}" "${APP_USER}" \
    && adduser -D -u "${APP_UID}" -G "${APP_USER}" "${APP_USER}"

WORKDIR /workspace

# Install git and ssh for SSH mode support
RUN apk add --no-cache git openssh-client

# Copy Maven project file first to maximize dependency cache reuse.
COPY --chown=${APP_UID}:${APP_GID} pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline

# Copy source - behavior depends on SOURCE_MODE
# In LOCAL mode: source is mounted at runtime
# In SSH mode: source will be cloned at startup
COPY src ./src 2>/dev/null || true

ENV MAVEN_CONFIG=/home/${APP_USER}/.m2
ENV SOURCE_MODE=${SOURCE_MODE}
EXPOSE 8080

USER ${APP_USER}

# Startup script that handles both LOCAL and SSH modes
RUN echo '#!/bin/sh' > /workspace/startup.sh && \
    echo 'set -e' >> /workspace/startup.sh && \
    echo '' >> /workspace/startup.sh && \
    echo 'if [ "$SOURCE_MODE" = "ssh" ]; then' >> /workspace/startup.sh && \
    echo '  if [ ! -d "/workspace/src" ] || [ -z "$(ls -A /workspace/src 2>/dev/null)" ]; then' >> /workspace/startup.sh && \
    echo '    echo "SSH mode: Cloning repository..."' >> /workspace/startup.sh && \
    echo '    git clone -b "${GIT_BRANCH:-main}" "$GIT_REPO_URL" /workspace-temp' >> /workspace/startup.sh && \
    echo '    mkdir -p /workspace/src' >> /workspace/startup.sh && \
    echo '    mv /workspace-temp/backend/src/* /workspace/src/ 2>/dev/null || true' >> /workspace/startup.sh && \
    echo '    rm -rf /workspace-temp' >> /workspace/startup.sh && \
    echo '  elif [ "$AUTO_PULL" = "true" ]; then' >> /workspace/startup.sh && \
    echo '    echo "SSH mode: Auto-pulling updates..."' >> /workspace/startup.sh && \
    echo '    cd /workspace/src && git pull origin "${GIT_BRANCH:-main}" 2>/dev/null || true' >> /workspace/startup.sh && \
    echo '  fi' >> /workspace/startup.sh && \
    echo 'fi' >> /workspace/startup.sh && \
    echo '' >> /workspace/startup.sh && \
    echo 'echo "Starting Maven Spring Boot..."' >> /workspace/startup.sh && \
    echo 'exec mvn -B spring-boot:run' >> /workspace/startup.sh && \
    chmod +x /workspace/startup.sh

# Dev runtime with Spring profile configured from compose env.
CMD ["/workspace/startup.sh"]
