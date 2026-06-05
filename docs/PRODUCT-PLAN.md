# Team Hub — Product & Development Plan

## Vision

A self-hosted collaboration platform where people create a **Build** (workspace), share an invite link, and work together through **chat**, **board**, and **files** — with URL shortening built in.

Think: **WhatsApp group + Trello board + Google Drive**, running on your Ubuntu VM.

---

## Core Concept: The "Build"

A **Build** is a shared workspace (like a WhatsApp group or Slack channel).

```
Build: "Mobile App Idea"
├── Members: Rahul, Priya, Amit (invited via link)
├── Chat: real-time group conversation
├── Board: ideas, tasks, votes, status
├── Files: shared uploads (PDF, images, zip)
└── Links: shorten & share URLs scoped to this build
```

**Invite flow:**
1. Owner creates a Build → gets `https://yourhub.com/build/abc123`
2. Owner shares link → new user opens → joins Build
3. Everyone sees same chat + board + files instantly

---

## Features Inspired by Other Apps

| Feature | Inspired by | Priority | Phase |
|---------|-------------|----------|-------|
| User login / register | WhatsApp, Slack | Must-have | 1 |
| Create & join Builds | Discord invite, WhatsApp group | Must-have | 1 |
| Invite link sharing | WhatsApp group link | Must-have | 1 |
| Real-time group chat | WhatsApp, Slack | Must-have | 2 |
| Online presence (who's active) | WhatsApp, Discord | High | 2 |
| Collaboration board (ideas/tasks) | Trello, Notion | Must-have | 3 |
| Drag-and-drop task columns | Trello | High | 3 |
| File upload & download | Google Drive, WhatsApp media | High | 4 |
| @mentions in chat | Slack, Discord | Medium | 5 |
| Reply to message (threads) | WhatsApp, Slack | Medium | 5 |
| URL shortener per Build | Bitly (existing) | Medium | 5 |
| Roles: Owner, Admin, Member | Discord, Slack | High | 6 |
| Notifications (new message, task assigned) | WhatsApp, Slack | Medium | 6 |
| Search messages & files | Slack | Low | 7 |
| Activity feed | GitHub, Notion | Low | 7 |
| Pinned messages / announcements | WhatsApp, Slack | Medium | 6 |
| Dark/light theme | Most modern apps | Low | 7 |

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Browser (Tabbed UI)                      │
│  [My Builds]  [Chat]  [Board]  [Files]  [Shortener]         │
└──────────────────────────┬──────────────────────────────────┘
                           │ REST + WebSocket
┌──────────────────────────▼──────────────────────────────────┐
│                   Spring Boot Backend                        │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐           │
│  │  Auth   │ │  Build  │ │  Chat   │ │  Board  │  ...      │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘           │
└──────┬──────────────┬──────────────┬────────────────────────┘
       │              │              │
  PostgreSQL        MinIO          Redis (optional)
  (users, builds,   (files)       (presence, pub/sub)
   messages, tasks)
```

---

## Database Model (Core Tables)

```
users
  id, username, email, password_hash, display_name, created_at

builds
  id, name, description, invite_code, owner_id, created_at

build_members
  build_id, user_id, role (OWNER/ADMIN/MEMBER), joined_at

chat_messages
  id, build_id, user_id, content, reply_to_id, created_at

board_columns
  id, build_id, title, position (e.g. Idea / In Progress / Done)

board_cards
  id, column_id, build_id, title, description, assignee_id, position, created_at

files
  id, build_id, user_id, filename, storage_path, size, mime_type, created_at

url_mappings (existing — add build_id column)
  id, build_id, short_code, original_url, ...
```

---

## Development Phases

### Phase 1 — Foundation (Week 1–2)
**Goal:** Users can register, create a Build, share invite link, join.

| Task | Details |
|------|---------|
| Migrate H2 → PostgreSQL | Required for multi-user |
| Add Spring Security + JWT | Login, register, token auth |
| User module | Register, login, profile |
| Build module | CRUD builds, generate invite code |
| Join via link | `GET /join/{inviteCode}` → add member |
| Basic dashboard UI | List my builds, create build, join build |

**Deliverable:** User creates "Startup Idea" build, shares link, friend joins.

---

### Phase 2 — Real-Time Chat (Week 3–4)
**Goal:** WhatsApp-style group chat inside each Build.

| Task | Details |
|------|---------|
| WebSocket + STOMP setup | `/ws` endpoint |
| Chat room per Build | `/topic/build/{buildId}` |
| Send/receive messages | Persist to DB + broadcast |
| Chat UI | Message list, input, scroll, timestamps |
| Message history API | Load last 50 messages on join |
| Online presence | Green dot who's connected (Redis optional) |

**Deliverable:** Multiple users chat live in the same Build.

---

### Phase 3 — Collaboration Board (Week 5–6)
**Goal:** Trello-style board to develop ideas together.

| Task | Details |
|------|---------|
| Board columns | Idea → Research → Building → Done |
| Board cards | Title, description, assignee |
| Drag-and-drop UI | Move cards between columns |
| Card comments | Discuss specific idea on card |
| Link chat to board | "Created card from message" (later) |

**Deliverable:** Team brainstorms on board while chatting.

---

### Phase 4 — File Sharing (Week 7)
**Goal:** Upload on one PC, download on another — scoped to Build.

| Task | Details |
|------|---------|
| Install MinIO on VM | S3-compatible storage |
| Upload API | Multipart, max size limit |
| File list per Build | Name, uploader, date, size |
| Download API | Presigned URL or stream |
| Files tab UI | Upload button, file table |

**Deliverable:** User A uploads deck.pdf, User B downloads it.

---

### Phase 5 — Integrate Shortener + Polish (Week 8)
**Goal:** Shortener becomes a tab inside each Build.

| Task | Details |
|------|---------|
| Scope URLs to Build | `build_id` on url_mappings |
| Shortener tab in Build view | Existing UI embedded |
| @mentions in chat | Notify specific user |
| Pinned messages | Owner pins announcement |
| Invite link in Shortener tab | Quick copy build invite |

**Deliverable:** Full tabbed Build experience.

---

### Phase 6 — Roles & Notifications (Week 9–10)
**Goal:** Production-ready access control.

| Task | Details |
|------|---------|
| Roles | Owner, Admin, Member permissions |
| Remove member | Owner kicks user |
| In-app notifications | Bell icon, unread count |
| Email notifications (optional) | New message digest |

---

### Phase 7 — Advanced (Future)
- Search across messages and files
- Activity timeline ("Priya moved card X")
- Voice/video call (WebRTC — complex)
- Mobile-friendly PWA
- Public API for integrations

---

## UI Layout (Target)

```
┌──────────────────────────────────────────────────────────────┐
│  Team Hub                              🔔  👤 Rahul  ▼      │
├────────────┬─────────────────────────────────────────────────┤
│ My Builds  │  Build: "Mobile App Idea"          [Copy Invite]│
│            ├─────────────────────────────────────────────────┤
│ ▶ Mobile   │  [Chat]  [Board]  [Files]  [Shortener]          │
│   App Idea │─────────────────────────────────────────────────│
│ ▶ Startup  │                                                 │
│   Plan     │   (active tab — Chat / Board / Files / Links)   │
│            │                                                 │
│ + New Build│                                                 │
└────────────┴─────────────────────────────────────────────────┘
```

---

## Tech Stack (Final)

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 3.2, Java 17 |
| Auth | Spring Security + JWT |
| Real-time | WebSocket + STOMP |
| Database | PostgreSQL |
| File storage | MinIO |
| Cache / presence | Redis (Phase 2+) |
| Frontend | HTML/CSS/JS → later Vue or React |
| Deploy | Ubuntu VM + Nginx + systemd + GitHub Actions CI/CD |

---

## What to Build FIRST (Recommended Order)

```
1. PostgreSQL + User login          ← start here
2. Build create + invite link
3. Chat (WebSocket)
4. Board (kanban)
5. Files (MinIO)
6. Shortener tab (already exists)
7. Roles + notifications
```

**Do NOT build everything at once.** Each phase is a working product.

---

## MVP Definition (Minimum Viable Product)

After Phase 3, you have an MVP:

- ✅ Register / login
- ✅ Create Build, share invite link
- ✅ Group chat (real-time)
- ✅ Idea board (collaborate)
- ⏳ Files and shortener come in Phase 4–5

---

## Estimated Timeline (Solo Developer)

| Phase | Duration | Cumulative |
|-------|----------|------------|
| Phase 1 — Foundation | 2 weeks | 2 weeks |
| Phase 2 — Chat | 2 weeks | 4 weeks |
| Phase 3 — Board | 2 weeks | 6 weeks |
| Phase 4 — Files | 1 week | 7 weeks |
| Phase 5 — Shortener + polish | 1 week | 8 weeks |
| Phase 6 — Roles | 2 weeks | 10 weeks |

**MVP (Phases 1–3): ~6 weeks**

---

## Next Action

Start **Phase 1**:
1. Add PostgreSQL to VM
2. Add user registration + JWT auth
3. Add Build entity + invite link
4. New dashboard UI

Say **"start Phase 1"** to begin implementation.
