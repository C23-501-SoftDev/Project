## Context

В системе уже есть `Document` и blob-хранилище для изображений/вложений. Для прикрепляемых файлов нужен отдельный жизненный цикл: метаданные в БД, физический файл в blob-хранилище и управление через REST API.

## Goals / Non-Goals

**Goals:**
- Реализовать загрузку вложений к документу через `multipart/form-data`.
- Сохранять бинарные данные в blob-хранилище, которое может быть SMB-шарой, смонтированной на сервере.
- Поддержать список и удаление вложений.
- Возвращать понятные ошибки для превышения размера и запрещённых расширений.

**Non-Goals:**
- Версионирование самих вложений.
- Предпросмотр файлов в браузере.
- Полная файловая система поверх SMB SDK, если достаточно смонтированного тома.

## Decisions

### 1. Схема данных (Database)
- Новая доменная модель `Attachment` с JPA-таблицей `attachments`.
- Метаданные: `document_id`, `filename`, `content_type`, `size_bytes`, `storage_path`, `uploaded_by`, `uploaded_at`.

### 2. Файловое хранилище (Infrastructure Layer)
- Потоковая запись через `InputStream` в blob-хранилище.
- Хранилище реализуется через файловую абстракцию и может быть размещено на SMB-mounted path.

### 3. Слой приложения (Application Layer)
- Отдельный `AttachmentService` координирует метаданные и физический файл.
- Валидация размера и расширения файла выполняется до записи.

### 4. REST API (Interfaces Layer)
- `GET /api/documents/{id}/attachments`
- `POST /api/documents/{id}/attachments`
- `GET /api/attachments/{attachmentId}/download`
- `DELETE /api/documents/{id}/attachments/{attachmentId}`

## Risks / Trade-offs

- **[Risk]** Несогласованность между БД и файловым хранилищем при падении операции.
  - **Mitigation**: cleanup файлов при ошибке сохранения метаданных и rollback БД при ошибке удаления файла.
- **[Risk]** Ограничения SMB-доступа сервисного аккаунта.
  - **Mitigation**: хранить путь и права доступа как конфигурацию окружения.

## Proposed Code Changes

- **Domain**: `Attachment.java`, `AttachmentRepository.java`, `AttachmentFileStorageRepository.java`, новые исключения.
- **Application**: `AttachmentService.java`.
- **Infrastructure**: JPA entity/repository/mapper, файловая реализация storage repository.
- **Interfaces**: `AttachmentController.java`, DTO ответа, обновление страниц документа.
- **Resources**: конфигурация лимита размера и списка разрешённых расширений.
