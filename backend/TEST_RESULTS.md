# Integration tests report (backend)

Источник: `Project/backend/target/surefire-reports/TEST-*.xml`
Дата прогона: 2026-07-20 (ветка `feature/email-notifications`)

## Итог

- **Всего тестов**: 58
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Результат**: BUILD SUCCESS

## Сводка по suites

| Suite | Tests | Time (s) | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `AdminUserIntegrationTest` | 8 | 72.31 | 0 | 0 | 0 |
| `AttachmentIntegrationTest` | 6 | 2.66 | 0 | 0 | 0 |
| `AuditLogIntegrationTest` (US4.1.5) | 4 | 3.75 | 0 | 0 | 0 |
| `AuthIntegrationTest` | 5 | 1.10 | 0 | 0 | 0 |
| `DocumentValidationIntegrationTest` | 2 | 1.52 | 0 | 0 | 0 |
| `GrantPermissionTest` (US4.2.2) | 2 | 0.89 | 0 | 0 | 0 |
| `GroupIntegrationTest` (US4.1.8/4.1.9) | 3 | 1.73 | 0 | 0 | 0 |
| `GroupPermissionIntegrationTest` (US4.2.2/4.1.9) | 4 | 3.57 | 0 | 0 | 0 |
| `JGitDocumentContentRepositoryIntegrationTest` | 3 | 0.22 | 0 | 0 | 0 |
| `NotificationIntegrationTest` (US4.3.1) | 6 | 17.32 | 0 | 0 | 0 |
| `PermissionIntegrationTest` (US4.2.2) | 3 | 1.72 | 0 | 0 | 0 |
| `RequirementNumberGenerationIntegrationTest` | 1 | 0.36 | 0 | 0 | 0 |
| `SecurityIntegrationTest` | 2 | 2.19 | 0 | 0 | 0 |
| `SpaceIntegrationTest` (US4.2.1) | 4 | 2.13 | 0 | 0 | 0 |
| `StorageIntegrationTest` | 1 | 0.06 | 0 | 0 | 0 |
| `ValidationErrorIntegrationTest` | 3 | 0.62 | 0 | 0 | 0 |
| `KnowledgeBaseApplicationTests` | 1 | 14.14 | 0 | 0 | 0 |

Подробное покрытие сценариев по US4.1.5, US4.1.8/4.1.9, US4.2.1, US4.2.2, US4.3.1 —
см. [FEATURES_REPORT.md](../FEATURES_REPORT.md) в корне проекта.

## Команда запуска

```bash
docker exec kb_app mvn -f /workspace/backend/pom.xml test
```
