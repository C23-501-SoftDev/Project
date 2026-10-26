# Java development container

This configuration opens the source checkout in an independent Maven/JDK 21 container. In VS Code, use **Dev Containers: Reopen in Container**. The checkout is mounted at `/workspaces/<checkout-directory>` so edits remain on the host.

Run Java checks in its terminal:

```sh
cd backend
mvn clean verify
```

This is a development environment, separate from the packaged application in `docker-compose.yml`. It does not start PostgreSQL, Ollama or the application, does not mount the host Docker socket or SSH credentials, and does not provide a Docker engine inside the container. Run the [complete stack](../docs/DOCKER_LOCAL_RU.md) from a host terminal. PostgreSQL/pgvector integration checks still require their dedicated test database; browser and real-model acceptance are separate checks.

The Maven image uses its default root user inside this development container. The application runtime uses UID 1000. Closing this development container stops only this container, not the user's Compose stack.

The image's ARM64 manifest and the JSON/workspace configuration were checked on this Mac. Interactive VS Code Dev Containers startup was not exercised as part of the scripted helper tests.
