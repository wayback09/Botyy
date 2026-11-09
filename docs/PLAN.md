# Pulse: Engineering Plan & Architecture Specification (Phase 0)

## 1. Executive Summary & Lead Engineer Assessment

Pulse is a unified mobile platform combining WhatsApp-style instant messaging with Instagram/Facebook-style social feeds, creator communities, and broadcast channels.

### Lead Engineer Note on Language & UI Framework
As requested ("If you strongly disagree with a choice, say so once with reasons, then continue unless I object"):
- **Language & UI Toolkit Evaluation:**
  - The project template is pre-configured with Android Gradle Plugin 8.9+, Jetpack Compose BoM, Kotlin 2.0+, and modern Kotlin Coroutines/Flow bindings for Firebase.
  - Using pure Java with XML ViewBinding and Hilt in modern Android introduces significant boilerplate (especially when handling reactive streams, lifecycle awareness, and nested RecyclerViews for complex multi-type feeds and chats) and increases APK method counts.
  - Furthermore, modern Firebase and Android Jetpack libraries (notably `androidx.credentials` Credential Manager, Flow snapshots, and Coroutine Tasks) are designed Kotlin-first.
  - **Recommendation:** Maintain strict Clean Architecture with Repository interfaces and Domain models decoupled from the UI. If you prefer to stay purely with Java and XML ViewBinding, we will structure all ViewModels, Repositories, and Services in Java, utilizing WorkManager and LiveData. If you are open to Kotlin for the data/UI layer, Jetpack Compose drastically reduces boilerplate, minimizes APK size, and ensures full interoperability with AI Studio's preview tooling. We will proceed under strict repository abstraction so the implementation remains completely modular.

---

## 2. Architecture Overview

```
                      +-----------------------------+
                      |     Presentation Layer      |
                      |  Activities / Screens / VM  |
                      +--------------+--------------+
                                     |
                                     v
                      +-----------------------------+
                      |     Domain / Use Cases      |
                      |     (Contract Interfaces)   |
                      +--------------+--------------+
                                     |
                                     v
                      +-----------------------------+
                      |      Repository Layer       |
                      | ChatRepo | FeedRepo | ...   |
                      +---+---------------------+---+
                          |                     |
         +----------------+--+               +--+----------------+
         |                   |               |                   |
         v                   v               v                   v
+-----------------+ +-----------------+ +-----------------+ +-----------------+
| Firestore Local | |   Remote Cloud  | |  WorkManager    | | Firebase Auth / |
|   Persistence   | |    Firestore    | | Outbox & Upload | |  Cloud Storage  |
+-----------------+ +-----------------+ +-----------------+ +-----------------+
```

### Key Principles
1. **Single-Activity Architecture:** Single container activity with standard Navigation Component or Compose Navigation.
2. **Repository Abstraction:** No UI layer class directly references Firebase SDK or Firestore classes. All interactions pass through interfaces (`ChatRepository`, `SocialFeedRepository`, `CommunityRepository`, `ChannelRepository`, `AuthRepository`).
3. **Optimistic UI & Offline-First:** Messages and feed reactions are reflected locally immediately; background sync and outbox queues handle transient network disconnects.
4. **Zero Unbounded Queries:** Every list query enforces `.limit(20)` to `.limit(50)` and paginates using Firestore document snapshots (`startAfter`).

---

## 3. Cloud Firestore Data Model & Schema

All document models specify fields, data types, indexes, and denormalized fields:

### 3.1 `users/{userId}`
- `uid`: `String` (Document ID == Auth UID)
- `username`: `String` (lowercased, alphanumeric)
- `displayName`: `String`
- `bio`: `String` (max 160 chars)
- `avatarUrl`: `String?`
- `followersCount`: `Long` (counter updated by Cloud Functions)
- `followingCount`: `Long` (counter updated by Cloud Functions)
- `postsCount`: `Long`
- `createdAt`: `Timestamp`
- `updatedAt`: `Timestamp`
- `isPrivate`: `Boolean`

### 3.2 `usernames/{username}` (Unique Namespace Lock)
- Document ID: lowercased username (e.g. `alex_dev`)
- `uid`: `String` (owner UID)
- `claimedAt`: `Timestamp`

### 3.3 `contacts/{userId}/userContacts/{contactUid}`
- `contactUid`: `String`
- `displayName`: `String`
- `addedAt`: `Timestamp`

### 3.4 `conversations/{conversationId}`
- `conversationId`: `String` (Document ID; for 1-to-1: deterministic `uid1_uid2` sorted)
- `type`: `String` ("DIRECT" | "GROUP")
- `memberUids`: `List<String>` (used for security rules array-contains queries)
- `adminUids`: `List<String>` (group admins)
- `title`: `String?` (for group chat)
- `iconUrl`: `String?` (group avatar)
- `lastMessageText`: `String?` (denormalized preview)
- `lastMessageSenderId`: `String?`
- `lastMessageTimestamp`: `Timestamp?`
- `unreadCounts`: `Map<String, Long>` (per-user unread badge count)
- `createdAt`: `Timestamp`
- `updatedAt`: `Timestamp`

### 3.5 `conversations/{conversationId}/messages/{messageId}`
- `messageId`: `String`
- `conversationId`: `String`
- `senderUid`: `String`
- `content`: `String` (text or media description)
- `mediaUrl`: `String?`
- `mediaType`: `String?` ("IMAGE" | "AUDIO" | "NONE")
- `status`: `String` ("SENT" | "DELIVERED" | "READ")
- `readBy`: `List<String>`
- `createdAt`: `Timestamp`

### 3.6 `posts/{postId}` (Global Post Store)
- `postId`: `String`
- `authorUid`: `String`
- `authorUsername`: `String` (denormalized)
- `authorAvatarUrl`: `String?` (denormalized)
- `caption`: `String`
- `mediaUrls`: `List<String>`
- `likesCount`: `Long`
- `commentsCount`: `Long`
- `createdAt`: `Timestamp`

### 3.7 `follows/{followId}`
- Document ID: `${followerUid}_${targetUid}`
- `followerUid`: `String`
- `targetUid`: `String`
- `createdAt`: `Timestamp`

### 3.8 `feeds/{userId}/items/{postId}` (Fan-Out Feed)
- Document ID: `${postId}`
- `postId`: `String`
- `authorUid`: `String`
- `createdAt`: `Timestamp`

### 3.9 `communities/{communityId}`
- `communityId`: `String`
- `creatorUid`: `String`
- `name`: `String`
- `description`: `String`
- `iconUrl`: `String?`
- `membersCount`: `Long`
- `isPrivate`: `Boolean`
- `createdAt`: `Timestamp`

### 3.10 `communities/{communityId}/members/{userId}`
- `userId`: `String`
- `role`: `String` ("MEMBER" | "MODERATOR" | "ADMIN")
- `joinedAt`: `Timestamp`
- `isBanned`: `Boolean`

### 3.11 `communities/{communityId}/posts/{postId}`
- `postId`: `String`
- `communityId`: `String`
- `authorUid`: `String`
- `authorUsername`: `String`
- `content`: `String`
- `mediaUrls`: `List<String>`
- `likesCount`: `Long`
- `createdAt`: `Timestamp`

### 3.12 `channels/{channelId}`
- `channelId`: `String`
- `ownerUid`: `String`
- `name`: `String`
- `description`: `String`
- `iconUrl`: `String?`
- `subscribersCount`: `Long`
- `createdAt`: `Timestamp`

### 3.13 `channels/{channelId}/subscribers/{userId}`
- `userId`: `String`
- `subscribedAt`: `Timestamp`

### 3.14 `channels/{channelId}/posts/{postId}`
- `postId`: `String`
- `channelId`: `String`
- `authorUid`: `String`
- `content`: `String`
- `mediaUrls`: `List<String>`
- `reactions`: `Map<String, Long>` (emoji reaction counters)
- `createdAt`: `Timestamp`

### 3.15 `blocks/{blockId}`
- Document ID: `${blockerUid}_${blockedUid}`
- `blockerUid`: `String`
- `blockedUid`: `String`
- `createdAt`: `Timestamp`

### 3.16 `reports/{reportId}`
- `reportId`: `String`
- `reporterUid`: `String`
- `targetType`: `String` ("POST" | "MESSAGE" | "USER" | "COMMUNITY")
- `targetId`: `String`
- `reason`: `String`
- `createdAt`: `Timestamp`

### 3.17 `fcmTokens/{token}`
- `token`: `String`
- `userId`: `String`
- `platform`: `String` ("android")
- `updatedAt`: `Timestamp`

---

## 4. Security Rules Strategy (Zero-Trust)

1. **Authentication Enforcement:** `request.auth != null` mandatory across all operations.
2. **Conversation Access:** A user can read/write `conversations/{id}` and `messages` only if `request.auth.uid in resource.data.memberUids`.
3. **Content Integrity:** User profile updates restricted strictly to `request.auth.uid == userId`.
4. **Community Scoping:** Only active members (`!isBanned`) can write to `communities/{id}/posts`. Only creator/admins can delete posts or update community metadata.
5. **Channel Broadcast Scoping:** Only `resource.data.ownerUid == request.auth.uid` can write posts to `channels/{id}/posts`. Subscribers have read-only access and can only write to reactions.
6. **Block Filtering:** Users cannot message or follow users who have blocked them.
7. **Cloud Storage Rules:**
   - Image uploads limited to `image/*` MIME types.
   - Max file size: 5 MB per image (pre-compressed on client to <1 MB).
   - Upload path: `/users/{uid}/...` strictly owned by `request.auth.uid`.

---

## 5. Feed Strategy: Fan-Out vs. Fan-In

- **Hybrid Fan-Out Strategy:**
  - Standard users (< 5,000 followers): Cloud Functions handle write-time fan-out (`feeds/{followerUid}/items`). This gives users instant, single-query `$O(1)$` timeline reads.
  - Celebrity / High-follower accounts (> 5,000 followers): Ingestion into reader timelines uses fan-in query federation on client launch to prevent Firestore write storms.
- **Pagination & Caching:**
  - Client stores retrieved feed items with Firestore offline cache (`cacheSettings = PersistentCacheSettings`).
  - Feed queries are windowed with `.limit(20)` using cursor-based pagination with `startAfter(lastDocumentSnapshot)`.

---

## 6. Cost Controls & Abuse Prevention

- **Firestore Reads:**
  - Avoid redundant fetches by listening to top-level `conversations` with `lastMessage` denormalization.
  - Caching avatars and display names directly on `lastMessage` and `posts` prevents N+1 user profile queries.
- **Cloud Functions:**
  - Lightweight TypeScript functions using Firebase Admin SDK with batched writes (`db.batch()`) limited to 500 operations per batch.
- **Rate Limiting:**
  - App Check (Play Integrity) blocks automated bots and API scrapers.
  - Client-side debouncing on search and message send.

---

## 7. Top Risks & Mitigation Matrix

| Risk | Severity | Mitigation Strategy |
|---|---|---|
| **Abuse & Inappropriate Content** | HIGH | Client pre-upload checks, Content Reporting system (`reports` collection), User Blocking (`blocks`), Creator moderation tools in communities. |
| **Firestore Read Cost Explosions** | HIGH | Strict query limits (`limit(20)`), fan-out denormalization, snapshot listener lifecycle binding (stop listening when screen paused). |
| **Out-of-Order Message Delivery** | MEDIUM | Deterministic `FieldValue.serverTimestamp()` combined with client message sequence UUIDs. |
| **Large Media Upload Battery Drain** | MEDIUM | Local downsampling to max 1280px (JPEG 85% / WebP) using Glide / Bitmap before scheduling via WorkManager. |
| **Notification Spam / Battery** | MEDIUM | FCM high-priority data messages mapped to categorized Android Notification Channels (`messages`, `social`, `communities`). |

---

## 8. Phased Roadmap

- **Phase 0:** Plan documentation, project naming, and Firebase provisioning request. (Current)
- **Phase 1:** Project foundation, Firebase RPC provisioning, Firestore security rules, emulator setup, and repository interfaces.
- **Phase 2 (MVP):**
  1. Auth (Google Sign-In via Credential Manager + Profile setup).
  2. One-to-one messaging & contact directory.
  3. Group chats with admin controls.
  4. Social feed with photo posts, likes, comments, and following fan-out.
  5. Creator communities & moderation tools.
  6. Broadcast channels & subscriber feeds.
  7. Push notifications (FCM).
  8. Safety tools (blocking & reporting).
  9. Settings & offline persistence tuning.
