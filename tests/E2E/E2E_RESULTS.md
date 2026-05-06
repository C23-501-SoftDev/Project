# E2E Results

## Где запускать

Рабочая папка:

`Project/tests/E2E`

## Команды запуска

### Baseline

```bash
npm test
```

### Strict acceptance

```bash
npm run test:strict
```

### Полный user функционал

```bash
npm run test:userfull
```

## Как посмотреть результаты

### 1) В консоли

После каждого запуска Playwright сразу печатает:
- количество `passed/failed`,
- детали ошибок,
- путь к trace и screenshot для каждого падения.

### 2) HTML-отчет

```bash
npm run report
```

Откроется Playwright HTML report со списком тестов, stacktrace, скриншотами и trace.

### 3) Trace конкретного падения

Из вывода теста берешь путь к `trace.zip` и запускаешь:

```bash
npx playwright show-trace <path-to-trace.zip>
```

## Последний фактический прогон

### `npm test` (полный suite)

- **51 total / 38 passed / 13 failed**
- Включает baseline + strict + userfull + depth/gaps сценарии.

### `npm run test:strict`

- **1 passed / 5 failed**
- Основные причины падений:
  - отсутствуют `main.css/main.js`,
  - user-страницы и admin settings содержат заглушки/WIP.

### `npm run test:userfull`

- **24 total / 16 passed / 8 failed**
- Падения в сценариях полноты пользовательского UI и глубинных negative-кейсах:
  - главная страница документов (заглушка),
  - поиск (заглушка),
  - просмотр документа (заглушка),
  - история документа (заглушка),
  - страница пространства (заглушка),
  - race duplicate create -> `500` вместо `409`,
  - invalid permission type -> `500`,
  - permissive sort по невалидному полю (ожидалось fail-safe поведение).

## Интерпретация

- Зеленый baseline показывает, что текущий этап запускается и основные smoke-потоки живы.
- Красные strict/userfull показывают реальные продуктовые gaps до полной пользовательской готовности.
- Для разбора каждого failure по приоритетам смотри `E2E_FAILURE_ANALYSIS.md`.

