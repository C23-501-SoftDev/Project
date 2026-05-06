FROM maven:3.9.9-eclipse-temurin-17-alpine

ARG APP_USER=app
ARG APP_UID=1000
ARG APP_GID=1000

RUN addgroup -g "${APP_GID}" "${APP_USER}" \
    && adduser -D -u "${APP_UID}" -G "${APP_USER}" "${APP_USER}"

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
