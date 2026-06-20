FROM mcr.microsoft.com/playwright:v1.59.1-jammy

WORKDIR /e2e

COPY package.json package-lock.json ./
RUN npm ci

# Chromium и системные зависимости уже в базовом образе; синхронизируем версию с @playwright/test
RUN npx playwright install chromium

COPY playwright.config.js ./
COPY tests ./tests

CMD ["npx", "playwright", "test", "--workers=1"]
