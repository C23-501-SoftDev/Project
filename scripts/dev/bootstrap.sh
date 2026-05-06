#!/bin/bash
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env"
ENV_EXAMPLE="$PROJECT_ROOT/.env.example"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
APP_CONTAINER="kb_app"
POSTGRES_CONTAINER="kb_postgres"
APP_PORT="${APP_PORT:-8080}"
HEALTH_CHECK_URL="http://localhost:${APP_PORT}/actuator/health"
MAX_RETRIES=15
RETRY_INTERVAL=10

echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Developer Environment Bootstrap${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"

# 1. Check Docker and Docker Compose
echo -e "\n${YELLOW}[1/6]${NC} Checking Docker and Docker Compose..."
if ! command -v docker &> /dev/null; then
    echo -e "${RED}✗ Docker is not installed${NC}"
    echo "Please install Docker from https://www.docker.com/products/docker-desktop"
    exit 1
fi
echo -e "${GREEN}✓ Docker installed: $(docker --version)${NC}"

if ! docker compose version &> /dev/null; then
    echo -e "${RED}✗ Docker Compose is not installed or not compatible${NC}"
    echo "Please ensure you have Docker Compose v2 installed"
    exit 1
fi
echo -e "${GREEN}✓ Docker Compose available${NC}"

# 2. Prepare .env file
echo -e "\n${YELLOW}[2/6]${NC} Preparing environment file..."
if [ ! -f "$ENV_FILE" ]; then
    if [ ! -f "$ENV_EXAMPLE" ]; then
        echo -e "${RED}✗ .env.example not found${NC}"
        exit 1
    fi
    echo "Creating .env from .env.example..."
    cp "$ENV_EXAMPLE" "$ENV_FILE"
    echo -e "${GREEN}✓ .env created${NC}"
else
    echo -e "${GREEN}✓ .env already exists${NC}"
fi

# 3. Validate and populate required variables
echo -e "\n${YELLOW}[3/6]${NC} Validating and populating environment variables..."

# Function to update .env variable
update_env_var() {
    local key="$1"
    local value="$2"
    if grep -q "^${key}=" "$ENV_FILE"; then
        # Check if value is default/empty
        local current=$(grep "^${key}=" "$ENV_FILE" | cut -d= -f2-)
        if [ -z "$current" ] || [[ "$current" == "change-me"* ]]; then
            sed -i '' "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
            return 0
        fi
    else
        echo "${key}=${value}" >> "$ENV_FILE"
        return 0
    fi
    return 1
}

# Check and generate JWT_SECRET_KEY if needed
if grep -q "JWT_SECRET_KEY=change-me" "$ENV_FILE"; then
    echo "Generating secure JWT_SECRET_KEY..."
    # Generate 64-character random string (secure for JWT)
    JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
    update_env_var "JWT_SECRET_KEY" "$JWT_SECRET"
    echo -e "${GREEN}✓ Generated secure JWT_SECRET_KEY${NC}"
else
    echo -e "${GREEN}✓ JWT_SECRET_KEY is set${NC}"
fi

# Set UID/GID for macOS/Linux if not present
if [[ "$OSTYPE" != "msys" && "$OSTYPE" != "cygwin" ]]; then
    if grep -q "^UID=1000" "$ENV_FILE" && ! grep -q "^UID=$(id -u)" "$ENV_FILE"; then
        echo "Setting UID/GID for current user..."
        update_env_var "UID" "$(id -u)"
        update_env_var "GID" "$(id -g)"
        echo -e "${GREEN}✓ UID/GID configured${NC}"
    else
        echo -e "${GREEN}✓ UID/GID already configured${NC}"
    fi
fi

# Check required variables
echo "Validating required environment variables..."
required_vars=("POSTGRES_DB" "POSTGRES_USER" "POSTGRES_PASSWORD" "JWT_SECRET_KEY")
for var in "${required_vars[@]}"; do
    if ! grep -q "^${var}=" "$ENV_FILE"; then
        echo -e "${RED}✗ Missing required variable: ${var}${NC}"
        exit 1
    fi
done
echo -e "${GREEN}✓ All required variables present${NC}"

# 4. Check SSH mode requirements
SOURCE_MODE="${SOURCE_MODE:-local}"
if [ "$SOURCE_MODE" == "ssh" ]; then
    echo -e "\n${YELLOW}[4/6]${NC} Checking SSH mode requirements..."
    if ! grep -q "GIT_REPO_URL=" "$ENV_FILE"; then
        echo -e "${RED}✗ SSH mode requires GIT_REPO_URL${NC}"
        exit 1
    fi
    GIT_REPO_URL=$(grep "^GIT_REPO_URL=" "$ENV_FILE" | cut -d= -f2-)
    if [ -z "$GIT_REPO_URL" ]; then
        echo -e "${RED}✗ GIT_REPO_URL is empty${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ SSH mode configured with GIT_REPO_URL=${GIT_REPO_URL}${NC}"
else
    echo -e "\n${YELLOW}[4/6]${NC} Operating in LOCAL mode (code mounted from host)${NC}"
fi

# 5. Start containers
echo -e "\n${YELLOW}[5/6]${NC} Starting containers..."
cd "$PROJECT_ROOT"

# Stop any existing containers first
echo "Stopping any existing containers..."
docker compose --env-file .env down 2>/dev/null || true

echo "Building and starting containers..."
if docker compose --env-file .env up -d --build 2>&1; then
    echo -e "${GREEN}✓ Containers started successfully${NC}"
else
    echo -e "${RED}✗ Failed to start containers${NC}"
    echo ""
    echo "Recent docker compose output:"
    docker compose --env-file .env logs --tail=20 app postgres 2>/dev/null || true
    exit 1
fi

# 6. Health check
echo -e "\n${YELLOW}[6/6]${NC} Waiting for application to be ready..."
echo "Checking Docker container status..."
if ! docker ps --filter "name=kb_app" --filter "status=running" | grep -q kb_app; then
    echo -e "${RED}✗ Application container is not running${NC}"
    echo ""
    echo "Container status:"
    docker ps -a --filter "name=kb_" --format "table {{.Names}}\t{{.Status}}"
    echo ""
    echo "Recent logs from app:"
    docker compose --env-file .env logs --tail=30 app 2>/dev/null || true
    exit 1
fi
echo -e "${GREEN}✓ Application container is running${NC}"

if ! docker ps --filter "name=kb_postgres" --filter "status=running" | grep -q kb_postgres; then
    echo -e "${RED}✗ PostgreSQL container is not running${NC}"
    exit 1
fi
echo -e "${GREEN}✓ PostgreSQL container is running${NC}"

# Wait for health check
echo "Waiting for application to be healthy (this may take a minute)..."
attempt=0
while [ $attempt -lt $MAX_RETRIES ]; do
    if curl -s "$HEALTH_CHECK_URL" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Application is healthy${NC}"
        break
    fi
    attempt=$((attempt + 1))
    if [ $attempt -eq 1 ]; then
        echo "  Attempt 1/$MAX_RETRIES..."
    else
        echo "  Attempt $attempt/$MAX_RETRIES..."
    fi
    sleep $RETRY_INTERVAL
done

if [ $attempt -ge $MAX_RETRIES ]; then
    echo -e "${YELLOW}⚠ Health check timed out after $((MAX_RETRIES * RETRY_INTERVAL))s${NC}"
    echo ""
    echo "Checking application logs..."
    docker compose --env-file .env logs --tail=50 app | tail -20
    echo ""
    echo -e "${YELLOW}Application may still be starting. Check logs with:${NC}"
    echo "  make dev-logs"
    echo ""
else
    # Success
    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}✓ SETUP COMPLETE!${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "${GREEN}Your development environment is ready:${NC}"
    echo ""
    echo "  Application:    ${BLUE}http://localhost:${APP_PORT}${NC}"
    echo "  Health Check:   ${BLUE}http://localhost:${APP_PORT}/actuator/health${NC}"
    echo "  Swagger API:    ${BLUE}http://localhost:${APP_PORT}/swagger-ui.html${NC}"
    echo ""
    echo -e "${GREEN}Useful commands:${NC}"
    echo "  View logs:      ${BLUE}make dev-logs${NC}"
    echo "  Stop containers: ${BLUE}make dev-down${NC}"
    echo "  Access shell:   ${BLUE}make docker-shell${NC}"
    echo ""
    echo -e "${YELLOW}Next steps:${NC}"
    echo "  1. Open http://localhost:${APP_PORT} in your browser"
    echo "  2. Log in with credentials from the app documentation"
    echo "  3. Start editing code in backend/src"
    echo "  4. Changes will be reflected after Spring Boot recompiles (10-30 sec)"
    echo ""
fi

exit 0
