## 1. Database & Domain

- [ ] 1.1 Создать доменную модель `Attachment`
- [ ] 1.2 Создать интерфейсы `AttachmentRepository` и `AttachmentFileStorageRepository`
- [ ] 1.3 Добавить доменные исключения для вложений

## 2. Infrastructure Layer

- [ ] 2.1 Реализовать JPA entity/repository/mapper для `attachments`
- [ ] 2.2 Реализовать файловое сохранение и удаление вложений в blob-хранилище
- [ ] 2.3 Настроить лимиты размера и допустимые расширения

## 3. Application Layer

- [ ] 3.1 Создать `AttachmentService`
- [ ] 3.2 Добавить валидацию размера и типа файла
- [ ] 3.3 Реализовать компенсацию при ошибках записи файла или метаданных

## 4. Interfaces Layer

- [ ] 4.1 Создать REST API для списка, загрузки, скачивания и удаления вложений
- [ ] 4.2 Обновить страницы документа для drag-and-drop и выбора файла
- [ ] 4.3 Добавить отображение списка вложений и удаление из UI
- [ ] 4.4 Добавить скачивание вложений по ссылке и защиту по правам READ

## 5. Verification & Documentation

- [ ] 5.1 Обновить `BACKTRACKER.md`
- [ ] 5.2 Проверка качества согласно `openspec/quality-gates.md`
- [ ] 5.3 Выполнить `mvn clean compile` и `mvn test`
