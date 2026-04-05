# База Знаний

Проект по систематизации и управлению требованиями, процессами и бэклогом.

## Установка и настройка окружения

### 1. Кому что нужно устанавливать (памятка для команды)

| Роль | Необходимые программы |
|------|----------------------|
| **Все** | Git, браузер (Chrome/Firefox/Edge), live-server (Node.js) |
| **Backend-разработчик** | Java 17 или 21 JDK, Maven 3.9.14, PostgreSQL 18 |
| **Frontend-разработчик** | Node.js |
| **Тестировщик (QA)** | Браузер, Postman(или альтернатива, например, Insomnia, pgAdmin) |
| **Аналитик** | Только общий набор |
| **Техлид** | Всё вышеперечисленное |

### 2. Node.js, npm

#### Установка Node.js (версия 18+):

- Скачайте установщик с [nodejs.org](https://nodejs.org) (рекомендуется LTS-версия)

#### Проверка:

```bash
node -v
npm -v
```

### 3. Java 17/21 (JDK)

Для работы бэкенда необходима Java версии 17 или 21.

**Скачивание с официального сайта:**

1. Перейдите по ссылке:  
   [https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html]

2. Выберите установщик для вашей операционной системы:

   | ОС | Тип файла |
   |----|-----------|
   | Windows | `.exe` или `.msi` (x64 Installer) |
   | macOS | `.dmg` (Arm 64 или x64 в зависимости от процессора) |
   | Linux | `.tar.gz` (x64) |

3. Запустите скачанный файл и следуйте инструкциям установщика

**Проверка установки:**

Откройте командную строку (терминал) и выполните:

```bash
java -version
```

Вы должны увидеть что-то вроде:
java version "17.0.12" 2024-07-16 LTS
Java(TM) SE Runtime Environment (build 17.0.12+8-LTS-286)
Java HotSpot(TM) 64-Bit Server VM (build 17.0.12+8-LTS-286, mixed mode, sharing)

### 4. Maven (система сборки)

**Если используете IntelliJ IDEA и запускать проект планируете из неё, то этот пункт можно пропустить, эта IDE уже имеет встроенный maven**

Maven используется для управления зависимостями и сборки бэкенда.  
Устанавливается последняя стабильная версия (на текущий момент — **3.9.14**).

#### Windows

1. Скачайте архив `apache-maven-3.9.14-bin.zip` с официального сайта:  
   [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi)

2. Распакуйте архив в папку, например: `C:\maven`

3. Добавьте `C:\maven\bin` в переменную окружения `PATH`:
   - Нажмите `Win + R`, введите `sysdm.cpl`
   - Перейдите на вкладку `Advanced` → `Environment Variables`
   - В разделе `System variables` найдите `Path`, нажмите `Edit`
   - Добавьте новую строку: `C:\maven\bin`
   - Нажмите `OK` во всех окнах

#### macOS
```bash
brew install maven
```

#### Linux (Ubuntu/Debian)
```bash
sudo apt update
sudo apt install maven
```

#### Проверка после установки (для всех ОС):
```cmd
mvn -version
```

### 5. PostgreSQL

У большинства уже установлен через pgAdmin в рамках других дисциплин.  
Если нет — установите PostgreSQL 18 с официального сайта: https://www.postgresql.org/download/

#### Создание базы данных (через pgAdmin)

1. Откройте pgAdmin
2. Подключитесь к серверу
3. В окне Query Tools (Инструмент запросов) для базы postgres введите следующий SQL-запрос(создание пользователя для работы с БД):
```sql
CREATE USER kb_user WITH PASSWORD 'strong_password';
```
А затем этот (создание самой БД):
```sql
CREATE DATABASE knowledge_base
   WITH 
   OWNER = kb_user
   ENCODING = 'UTF8'
   CONNECTION LIMIT = -1;
```

## Запуск

В командной строке перейдите в директорию backend проекта и выполните:

```cmd
mvn clean compile
mvn spring-boot:run
```
После этого приложение соберётся и запустится.

### 🔐 Аутентификация

#### Через Swagger UI (браузер)

1. Откройте: http://localhost:8080/swagger-ui.html
2. Найдите раздел **"Authentication"** → `POST /api/auth/login`
3. Нажмите **"Try it out"**
4. Введите учетные данные:
```json
{
  "login": "admin",
  "password": "admin123"
}
```
5. Нажмите "Execute"
6. Скопируйте полученный `token` из ответа
7. Нажмите **"Authorize"** (кнопка в правом верхнем углу)
8. Введите: `Bearer <скопированный_токен>`
9. Теперь все защищенные эндпоинты доступны и через окно Swagger можно выполнить все запросы, представленные ниже

#### Через Insomnia / Postman / curl

**Запрос:**
```cmd
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"login\":\"admin\",\"password\":\"admin123\"}"
```

**Успешный ответ (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "user": {
    "id": 1,
    "login": "admin",
    "email": "admin@knowledgebase.local",
    "role": "ADMIN"
  }
}
```

**Ошибка (401 Unauthorized):**
```json
{
  "timestamp": "2026-04-05T...",
  "status": 401,
  "error": "Unauthorized",
  "message": "Неверный логин или пароль"
}
```

### 👥 Управление пользователями (только ADMIN)

#### Получить список всех пользователей

```cmd
curl -X GET http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer <токен>"
```

#### Создать нового пользователя

```cmd
curl -X POST http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer <токен>" \
  -H "Content-Type: application/json" \
  -d '{
    "login": "newuser",
    "email": "user@example.com",
    "password": "password123",
    "role": "EDITOR"
  }'
```

#### Обновить пользователя

```cmd
curl -X PUT http://localhost:8080/api/admin/users/2 \
  -H "Authorization: Bearer <токен>" \
  -H "Content-Type: application/json" \
  -d '{
    "login": "updateduser",
    "email": "updated@example.com",
    "role": "READER"
  }'
```

#### Удалить пользователя

```cmd
curl -X DELETE http://localhost:8080/api/admin/users/2 \
  -H "Authorization: Bearer <токен>"
```

#### Сменить пароль пользователя

```cmd
curl -X PUT http://localhost:8080/api/admin/users/2/password \
  -H "Authorization: Bearer <токен>" \
  -H "Content-Type: application/json" \
  -d '{"newPassword": "newSecurePass123"}'
```

### 📁 Управление пространствами (Spaces)

#### Получить список доступных пространств (для текущего пользователя)

```cmd
curl -X GET http://localhost:8080/api/spaces \
  -H "Authorization: Bearer <токен>"
```

#### Создать пространство (только ADMIN)

```cmd
curl -X POST http://localhost:8080/api/admin/spaces \
  -H "Authorization: Bearer <токен>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Мой проект",
    "description": "Документация проекта",
    "ownerId": 1
  }'
```

#### Назначить права на пространство (только ADMIN)

```cmd
curl -X POST http://localhost:8080/api/admin/spaces/1/permissions \
  -H "Authorization: Bearer <токен>" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 2,
    "permissionType": "WRITE"
  }'
```

Типы прав: READ, WRITE, OWNER

#### Получить права текущего пользователя в пространстве

```cmd
curl -X GET "http://localhost:8080/api/user/permissions?spaceId=1" \
  -H "Authorization: Bearer <токен>"
```

### 🔍 Проверка текущего пользователя

#### Получить информацию о себе

```cmd
curl -X GET http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer <токен>"
```

### 🩺 Health Check

#### Проверить состояние приложения

```cmd
curl -X GET http://localhost:8080/actuator/health
```

**Успешный ответ:**

```json
{
  "status": "UP"
}
```