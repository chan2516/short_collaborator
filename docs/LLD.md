# Low Level Design (LLD) — Team Hub Platform

> **Purpose:** Class-level, API-level, and database-level design for implementation and interviews.

---

## 1. Package Structure (Modular Monolith)

```
com.teamhub
├── TeamHubApplication.java
├── common/
│   ├── exception/          GlobalExceptionHandler, ErrorResponse
│   ├── security/           JwtFilter, SecurityConfig, UserPrincipal
│   └── util/               Base62, DateUtils
├── auth/
│   ├── controller/         AuthController
│   ├── service/            AuthService, JwtTokenProvider
│   ├── dto/                LoginRequest, RegisterRequest, TokenResponse
│   ├── entity/             User
│   └── repository/         UserRepository
├── build/
│   ├── controller/         BuildController, InviteController
│   ├── service/            BuildService, InviteService
│   ├── entity/             Build, BuildMember, MemberRole (enum)
│   └── repository/         BuildRepository, BuildMemberRepository
├── chat/
│   ├── controller/         ChatRestController, ChatWsController
│   ├── service/            ChatService, PresenceService
│   ├── entity/             ChatMessage
│   ├── dto/                SendMessageRequest, MessageResponse
│   └── config/             WebSocketConfig, StompAuthInterceptor
├── board/
│   ├── controller/         BoardController
│   ├── service/            BoardService, CardService
│   ├── entity/             BoardColumn, BoardCard
│   └── repository/         ColumnRepository, CardRepository
├── file/
│   ├── controller/         FileController
│   ├── service/            FileStorageService (MinIO adapter)
│   ├── entity/             SharedFile
│   └── repository/         FileRepository
└── shortener/
    ├── controller/         ShortUrlController, RedirectController
    ├── service/            UrlShorteningService
    ├── entity/             UrlMapping
    └── util/               Base62
```

**Design pattern:** Package-by-feature (not layer-by-layer) — each module owns its MVC stack.

---

## 2. Entity Relationship Diagram

```mermaid
erDiagram
    USERS ||--o{ BUILD_MEMBERS : joins
    BUILDS ||--o{ BUILD_MEMBERS : has
    USERS ||--o{ BUILDS : owns
    BUILDS ||--o{ CHAT_MESSAGES : contains
    USERS ||--o{ CHAT_MESSAGES : sends
    BUILDS ||--o{ BOARD_COLUMNS : has
    BOARD_COLUMNS ||--o{ BOARD_CARDS : contains
    USERS ||--o{ BOARD_CARDS : assigned
    BUILDS ||--o{ SHARED_FILES : stores
    USERS ||--o{ SHARED_FILES : uploads
    BUILDS ||--o{ URL_MAPPINGS : scopes

    USERS {
        bigint id PK
        varchar username UK
        varchar email UK
        varchar password_hash
        varchar display_name
        timestamp created_at
    }

    BUILDS {
        bigint id PK
        varchar name
        text description
        varchar invite_code UK
        bigint owner_id FK
        timestamp created_at
    }

    BUILD_MEMBERS {
        bigint build_id PK,FK
        bigint user_id PK,FK
        varchar role
        timestamp joined_at
    }

    CHAT_MESSAGES {
        bigint id PK
        bigint build_id FK
        bigint user_id FK
        text content
        bigint reply_to_id FK
        timestamp created_at
    }

    BOARD_COLUMNS {
        bigint id PK
        bigint build_id FK
        varchar title
        int position
    }

    BOARD_CARDS {
        bigint id PK
        bigint column_id FK
        bigint build_id FK
        varchar title
        text description
        bigint assignee_id FK
        int position
        timestamp created_at
    }

    SHARED_FILES {
        bigint id PK
        bigint build_id FK
        bigint user_id FK
        varchar filename
        varchar storage_key
        bigint size_bytes
        varchar mime_type
        timestamp created_at
    }

    URL_MAPPINGS {
        bigint id PK
        bigint build_id FK
        varchar short_code UK
        varchar original_url
        varchar url_hash
        bigint click_count
        timestamp created_at
    }
```

---

## 3. Class Diagram — Build Module (Example)

```mermaid
classDiagram
    class BuildController {
        +createBuild(request): BuildResponse
        +listMyBuilds(): List~BuildResponse~
        +getBuild(id): BuildResponse
    }

    class InviteController {
        +joinBuild(inviteCode): BuildResponse
        +regenerateInvite(buildId): InviteResponse
    }

    class BuildService {
        -buildRepository: BuildRepository
        -memberRepository: BuildMemberRepository
        +create(userId, request): Build
        +addMember(buildId, userId, role): void
        +isMember(buildId, userId): boolean
    }

    class InviteService {
        +generateCode(): String
        +joinByCode(userId, code): Build
    }

    class Build {
        -id: Long
        -name: String
        -inviteCode: String
        -ownerId: Long
    }

    class BuildMember {
        -buildId: Long
        -userId: Long
        -role: MemberRole
    }

    class MemberRole {
        <<enumeration>>
        OWNER
        ADMIN
        MEMBER
    }

    BuildController --> BuildService
    InviteController --> InviteService
    BuildService --> Build
    BuildService --> BuildMember
    InviteService --> BuildService
    BuildMember --> MemberRole
```

---

## 4. Sequence Diagram — Send Chat Message

```mermaid
sequenceDiagram
    actor User
    participant WS as WebSocket Client
    participant STOMP as StompController
    participant CS as ChatService
    participant DB as PostgreSQL
    participant Redis as Redis Pub/Sub
    participant Others as Other Clients

    User->>WS: Type message + Send
    WS->>STOMP: SEND /app/build/5/chat
    STOMP->>STOMP: Validate JWT + membership
    STOMP->>CS: saveMessage(buildId, userId, content)
    CS->>DB: INSERT chat_messages
    DB-->>CS: message id
    CS->>Redis: PUBLISH build:5:chat
    CS-->>STOMP: MessageResponse
    STOMP->>Others: BROADCAST /topic/build/5
    Others-->>User: Display new message
```

---

## 5. Sequence Diagram — CI/CD Deploy

```mermaid
sequenceDiagram
    actor Dev as Developer
    participant GH as GitHub
    participant CI as GitHub Actions
    participant VM as Ubuntu VM
    participant APP as Spring Boot

    Dev->>GH: git push main
    GH->>CI: Trigger workflow
    CI->>CI: mvn verify (unit + integration)
    CI->>CI: SonarQube / CodeQL scan
    CI->>CI: mvn package → JAR
    CI->>VM: SCP JAR + deploy.sh
    VM->>VM: Backup current JAR
    VM->>APP: systemctl restart
    APP->>APP: /actuator/health → UP
    alt Health check fails
        VM->>VM: Rollback to backup JAR
        VM->>CI: Notify failure
    else Health check passes
        CI->>Dev: Deploy success notification
    end
```

---

## 6. API Contract (OpenAPI Summary)

### Auth
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Create account |
| POST | `/api/auth/login` | Returns JWT + refresh token |
| POST | `/api/auth/refresh` | Refresh access token |

### Builds
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/builds` | Create new Build |
| GET | `/api/builds` | List user's Builds |
| GET | `/api/builds/{id}` | Build details |
| POST | `/api/builds/join/{inviteCode}` | Join via invite link |
| GET | `/api/builds/{id}/members` | List members |

### Chat
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/builds/{id}/messages?limit=50` | Message history |
| WS | `/ws` → `/app/build/{id}/chat` | Send message (STOMP) |
| SUB | `/topic/build/{id}` | Receive messages |

### Board
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/builds/{id}/board` | Columns + cards |
| POST | `/api/builds/{id}/board/columns` | Add column |
| POST | `/api/builds/{id}/board/cards` | Add card |
| PATCH | `/api/board/cards/{id}/move` | Move card to column |

### Files
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/builds/{id}/files` | Upload (multipart) |
| GET | `/api/builds/{id}/files` | List files |
| GET | `/api/files/{id}/download` | Download stream |

### Shortener
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/builds/{id}/shorten` | Create short URL |
| GET | `/{shortCode}` | Redirect (public) |

---

## 7. Design Patterns Used

| Pattern | Where | Why |
|---------|-------|-----|
| **Repository** | All JPA modules | Decouple persistence |
| **Service Layer** | Business logic | Transaction boundaries |
| **DTO / Record** | API boundary | Hide entity internals |
| **Strategy** | FileStorageService | Swap MinIO ↔ AWS S3 |
| **Observer / Pub-Sub** | Redis + WebSocket | Broadcast chat events |
| **Factory** | InviteService | Generate unique invite codes |
| **Builder** | Complex API responses | Readable object construction |
| **Filter Chain** | JwtFilter | Cross-cutting auth |
| **Circuit Breaker** | Resilience4j on MinIO calls | Fault tolerance |

---

## 8. Key Algorithms

### 8.1 Invite Code Generation
```
code = Base62(random 48-bit) → 8 chars, collision check in DB
Complexity: O(1) average with unique index
```

### 8.2 URL Shortening (existing)
```
id = auto-increment → shortCode = Base62(id)
Dedup: SHA-256(originalUrl) indexed lookup
```

### 8.3 Rate Limiter (Redis)
```
Key: rate:{userId}:{endpoint}
Algorithm: Token bucket, 100 req/min per user
```

---

## 9. Database Indexes

```sql
CREATE UNIQUE INDEX idx_builds_invite_code ON builds(invite_code);
CREATE INDEX idx_members_user ON build_members(user_id);
CREATE INDEX idx_messages_build_created ON chat_messages(build_id, created_at DESC);
CREATE INDEX idx_cards_column ON board_cards(column_id, position);
CREATE UNIQUE INDEX idx_url_short_code ON url_mappings(short_code);
CREATE INDEX idx_files_build ON shared_files(build_id);
```

---

## 10. Error Handling Contract

```json
{
  "timestamp": "2026-06-05T10:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "User is not a member of this build",
  "path": "/api/builds/5/chat"
}
```

Global handler: `@RestControllerAdvice` — consistent ProblemDetail JSON.

---

## 11. Configuration Properties

```yaml
teamhub:
  jwt:
    secret: ${JWT_SECRET}
    access-expiry-minutes: 15
    refresh-expiry-days: 7
  invite:
    code-length: 8
  file:
    max-size-mb: 50
    allowed-types: pdf,png,jpg,zip,docx
  minio:
    endpoint: http://localhost:9000
    bucket: teamhub-files
```

---

## 12. Testing Strategy (LLD Level)

| Layer | Tool | Example |
|-------|------|---------|
| Unit | JUnit 5 + Mockito | `BuildServiceTest` |
| Integration | Testcontainers (PG, Redis) | `ChatRepositoryIT` |
| API | MockMvc / RestAssured | `AuthControllerTest` |
| WebSocket | Spring WebSocket test | `ChatWsTest` |
| E2E | Playwright (optional) | Join build + send message |

---

*Document version: 1.0 | Companion to HLD.md*
