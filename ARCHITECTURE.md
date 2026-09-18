# Secure Stego Messenger — System Architecture

This document is the system design for the project, in the order requested. Where the
document describes something already implemented in this repository, it says so and
points at the file. Where it describes the recommended production path (PostgreSQL/
Supabase backend, OS keystore integration, etc.) that goes beyond what a zero-
infrastructure classroom demo needs, it says that explicitly instead of pretending the
demo already does it.

**Before anything else, the risk review the brief asked for:**

## 0. Security risks identified in the original brief, and how the design addresses them

| Risk in a naive version of this idea | This design's answer |
|---|---|
| Using a password or PIN directly as the encryption key | Password/PIN never touch AES directly. Password protects the user's *private key at rest* via PBKDF2; the actual message key is a random AES-256 key generated fresh per message. See §K/L. |
| Using JWT as an encryption key | This app has no separate backend/JWT in the runnable demo (see §F), but the principle is enforced in the code anyway: `CryptoService` only ever accepts a `SecretKey` object it generated itself or `KeyManager` unwrapped — there is no code path from a token string to an AES key. |
| 6-digit PIN "protecting" the whole account | PIN only unlocks a **local, device-only** copy of the private key (`DeviceKeyStore`). A remote database compromise gets an attacker nothing extra because of the PIN — see §J. |
| Hiding plaintext directly in the image | Impossible by construction: `SteganographyService.embed()` only ever receives already-AES-GCM-encrypted, key-wrapped bytes from `MessageService`. |
| IDOR — user A reading user B's message by guessing an ID | Every message/conversation read query is parameterized with the requesting user's ID as part of the `WHERE` clause itself (`MessageRepository.findByIdForUser`), so a wrong ID returns "not found," not "forbidden" — no distinguishable signal to enumerate with. See §I. |
| Trusting file extensions / MIME types for uploads | `ImageService.loadAndValidate` only trusts what `ImageIO` can actually decode, rejects non-PNG, and caps size/dimensions. See §X. |
| Overselling security ("100% anonymous", "military-grade") | §25/49 spell out exactly what the server can and cannot see, and where steganography's real limits are (detectable by steganalysis, destroyed by lossy re-compression). |

---

## A. Project overview

A privacy-oriented desktop messaging application. Users authenticate with email +
password, optionally enable a device-local 6-digit "Quick PIN," and exchange messages
that are encrypted (AES-256-GCM) and then hidden inside PNG images (LSB steganography)
before being sent. Multiple simultaneous conversations are supported through a chat-style
UI, not a one-shot "send an image" flow.

## B. Functional requirements

- Register/login with email + password; optional Quick PIN for local fast login.
- Multi-conversation chat UI: conversation list, per-conversation message thread.
- Compose: pick a recipient (by email, resolved to an existing account), type a message,
  pick a carrier image (built-in library or custom upload), see live capacity feedback,
  send.
- Receive: see an incoming stego-image message card, decode on demand.
- Image history: last 10 sent images with metadata, auto-pruned.
- Settings: account info, Quick PIN management, privacy/retention info, about.

## C. Non-functional requirements

- **Security**: authenticated encryption, salted+hashed credentials, parameterized SQL,
  IDOR-safe queries, rate-limited authentication, no plaintext secrets anywhere at rest.
- **Privacy**: minimal data collection (§25); the server/database never sees plaintext
  message content, and email is not exposed in chat UI beyond what look-up requires.
- **Usability**: single-shell UI, live capacity feedback, no manual size math.
- **Maintainability**: layered architecture (`model`/`security`/`stego`/`db`/`service`/`ui`),
  no business logic inside JavaFX controllers.
- **Portability**: builds and runs with just `mvn javafx:run` — no external services,
  containers, or manual DB setup required for the classroom demo.

## D. Recommended technology stack

- **Language/UI**: Java 21, JavaFX 21 (`javafx-controls`, `javafx-fxml`, `javafx-swing`
  for thumbnail rendering), FXML for the two static auth screens, programmatic Java for
  the dynamic dashboard/chat/settings screens (see §T for the rationale).
- **Styling**: JavaFX CSS with looked-up custom properties acting as theme variables
  (`src/main/resources/com/stegomsg/css/theme-dark.css`).
- **Database (demo)**: embedded SQLite via `sqlite-jdbc` — zero setup, one file
  (`~/.stegomsg/app.db`).
- **Database (recommended production target)**: PostgreSQL/Supabase — see §E/F.
- **Build**: Maven, `javafx-maven-plugin` for `mvn javafx:run`, `maven-shade-plugin` for
  a distributable fat jar.
- **Crypto**: JDK-native `javax.crypto`/`java.security` only (AES-GCM, RSA-OAEP,
  PBKDF2-HMAC-SHA512) — no external crypto library required to build offline. See §K for
  the Argon2id trade-off discussion.

## E. PostgreSQL vs Supabase vs MongoDB comparison

| Criterion | PostgreSQL (self-hosted) | Supabase (managed Postgres) | MongoDB |
|---|---|---|---|
| Auth support | None built-in — you build it (this project does, in `AuthService`) | Built-in Auth service, but this project's design intentionally keeps its own auth logic (portable, no vendor lock-in, matches "explain every mechanism" requirement) | None built-in |
| Relational messaging structure (users ↔ conversations ↔ messages) | Excellent — this is exactly what relational FKs/joins are for | Same as Postgres (it *is* Postgres) | Possible via manual reference fields or embedding, but joins are awkward and referential integrity isn't enforced by the DB |
| Security primitives | Row-level security (RLS), CHECK constraints, strong typing | Same, plus RLS is a first-class marketed feature — directly usable for the IDOR-defense-in-depth in §I | No native RLS; access control must live entirely in application code |
| Java integration | Mature, plain JDBC or any ORM | Plain JDBC works identically (it's Postgres on the wire); or REST/JS client if you don't want direct DB access | Official Java driver exists but the impedance mismatch with a relational domain model is real work |
| File/image metadata storage | Native (see §Q schema) | Native, plus **Supabase Storage** gives you object storage in the same platform for the actual image bytes | Native, but same object-storage-for-images caveat as Postgres |
| Scalability | Vertical scaling is simple; horizontal needs more work (not a concern at student-project scale) | Handles this for you if it ever mattered | Scales horizontally more easily, irrelevant at this scale |
| Transactions | Full ACID | Full ACID (same engine) | Multi-document transactions exist but are less idiomatic and less commonly used correctly |
| Query requirements (find all messages in a conversation a user belongs to, joined with sender info) | A single indexed JOIN — see `MessageRepository.findByConversationForUser` | Identical | Requires either embedding redundant data or doing the join in application code |
| Privacy | Field-level control, RLS, easy to audit a small number of tables | Same, with less ops overhead | Schema-less nature makes it easier to accidentally over-collect/leak fields |
| Development complexity | Low-medium — need to run/manage a Postgres instance | Low — hosted, free tier exists, migrations are simple SQL | Low to start, but the relational access patterns this project actually needs fight the document model |
| Free-tier practicality | Free if self-hosted or using a free-tier host | Yes, generous free tier, good fit for a student project | Yes, free tier exists (Atlas), but see complexity note above |
| Professional architecture signal | Strong — relational DB + object storage is the standard pattern for this exact kind of app | Strong, same reasoning, plus "used a modern BaaS correctly" is itself a plus | Would raise the reasonable question "why is a message/conversation graph in a document store?" |

## F. Final database/backend recommendation

**PostgreSQL** is the right data model for this domain — everything here (users,
conversations, membership, messages, ordered history, retention limits) is inherently
relational, and the IDOR-prevention requirement in §22 is much easier to get right with
real foreign keys and joins than with manually-maintained references in a document store.

**Supabase** is the recommended way to *host* that Postgres in a real deployment — same
engine, less ops burden, free tier, and Row-Level Security available as defense-in-depth
(see the policy in `database/schema_postgres.sql`). It does **not** need to be used for
its client-side Auth/Storage features; this project's own `AuthService` and local image
storage remain the source of truth so the security design in §K/L stays fully explained
and auditable rather than delegated to a third-party black box.

**For the classroom demo actually included in this repository**, the app instead uses
**embedded SQLite** (`database/schema_sqlite.sql`) so it runs with zero external
infrastructure — see §48's instruction to prefer "secure and realistic for a student
project" over impractical infrastructure. `Database.java` is the single seam: swapping
it for a Postgres/Supabase `DataSource` is the only change needed to move to
`schema_postgres.sql` in production; every repository class talks to plain JDBC and does
not know which database it's using.

Images are **not** stored as large binaries in the relational database in either case —
`messages.image_path` is a reference (a local file path in the demo, an object-storage
key such as Supabase Storage or S3 in production); see §37.

## G. High-level architecture diagram

```
                       ┌───────────────────────────────┐
                       │        JavaFX Client          │
                       │  (this repository, com.stegomsg)│
                       │                                │
                       │  ui/  → controllers + panes    │
                       │  service/ → AuthService,        │
                       │    ChatService, MessageService  │
                       │  security/ → Crypto, KeyManager, │
                       │    PasswordHasher, DeviceKeyStore│
                       │  stego/  → SteganographyService, │
                       │    CapacityCalculator            │
                       └───────────────┬────────────────┘
                                       │ plain JDBC
                                       ▼
                       ┌───────────────────────────────┐
                       │   Database (SQLite in demo /   │
                       │   PostgreSQL·Supabase in prod)  │
                       │   users, sessions, conversations,│
                       │   messages, image_history,       │
                       │   security_events                │
                       └───────────────┬────────────────┘
                                       │ image_path reference
                                       ▼
                       ┌───────────────────────────────┐
                       │  Image storage (local ./images  │
                       │  in demo / Supabase Storage or   │
                       │  S3-compatible object storage in │
                       │  production)                     │
                       └───────────────────────────────┘
```

There is deliberately **no separate network backend process** in the runnable demo — the
JavaFX client talks directly to its local SQLite file and local image folder. §31 below
describes the REST API design a real multi-machine deployment would need (e.g. a Spring
Boot service sitting where "Database" is in this diagram), which is what you'd build if
this needed to support two people on two different computers rather than one shared
local demo database.

## H. Authentication architecture

Implemented in `AuthService` + `UserRepository` + `KeyManager`.

```
register(email, password, alias)
  -> validate email format, password strength (util/Validation)
  -> passwordHash = PBKDF2-HMAC-SHA512(password, freshSalt, 210_000 iters)   [Pbkdf2PasswordHasher]
  -> (publicKey, privateKey) = RSA-2048 keypair                              [KeyManager]
  -> protectedPrivateKey = AES-256-GCM(privateKey, key=PBKDF2(password))     [KeyManager]
  -> INSERT users(email, passwordHash, publicKey, protectedPrivateKey, ...)

login(email, password)
  -> look up user by email
  -> check account lockout (failed_login_attempts / account_locked_until)
  -> verify password against passwordHash (constant-time compare)
  -> recover private key: AES-256-GCM-decrypt(protectedPrivateKey, key=PBKDF2(password))
     (this step ALSO implicitly re-verifies the password via the GCM auth tag)
  -> reset failure counters, start in-memory SessionService with (user, privateKey)
```

Passwords are never sent anywhere except into the PBKDF2 function and the AES-key
derivation function that unlocks the private key — never stored, never logged (§36).

## I. Authorization architecture

Every protected read goes through a repository method whose `WHERE` clause **includes
the requesting user's ID as a condition of the row matching at all** — not as a
post-fetch check written in application code that could be forgotten on some new
endpoint:

```sql
-- MessageRepository.findByIdForUser — the actual guard against IDOR
SELECT * FROM messages WHERE id = ? AND (sender_id = ? OR recipient_id = ?)
```

If the calling user isn't a party to that message, the query returns zero rows —
indistinguishable from "that message doesn't exist." `ChatService`/`MessageService`
never have a code path that fetches a row *and then* checks ownership; ownership is part
of the fetch. The same pattern applies to conversations (`findAllForUser`) and messages
within a conversation (`findByConversationForUser`, which joins through
`conversations` to confirm membership). In the production Postgres schema, Row-Level
Security duplicates this same rule at the database engine level as defense-in-depth
(`database/schema_postgres.sql`), so even a future bug in application code can't leak
cross-user rows.

## J. Quick PIN architecture

Implemented in `DeviceKeyStore` + `AuthService.enableQuickPin/quickLogin`.

```
Enable (while already logged in normally):
  privateKey (already decrypted in this session)
        │
        ▼
  AES-256-GCM-encrypt(privateKey, key=PBKDF2(PIN, deviceSalt))
        │
        ▼
  write to a LOCAL FILE ONLY: ~/.stegomsg/device-keys/<userId>.devicekey
  (never sent to the database)
  also: pin_hash column in `users` = PBKDF2(PIN) — used purely for the
  app-level correctness/lockout check, independent of the file above

Quick login:
  verify PIN against pin_hash (with lockout: 5 attempts, doubling delay — Constants)
        │
        ▼
  read local .devicekey file, AES-GCM-decrypt with PBKDF2(PIN, sameSalt)
        │
        ▼
  recovered private key -> start session, same as a normal login
```

**Honest limitation, stated in the UI itself (`SettingsPane`) and in
`DeviceKeyStore`'s own javadoc:** a 6-digit PIN is ~20 bits of entropy. This is fine
against the threat this feature is actually for — a "return to the app on your own
laptop without retyping your password" convenience — because it never leaves the
device, so a database breach or network attacker gains nothing from it. It is **not**
resistant to an attacker who has both the local file and unlimited offline compute. A
production build should replace `DeviceKeyStore`'s plain file with OS-native secure
storage (Windows DPAPI / macOS Keychain / Linux Secret Service), which ties the secret
to OS-level access control instead of file permissions alone (§34).

## K. Cryptographic architecture

| Purpose | Primitive | Where |
|---|---|---|
| Password/PIN hashing | PBKDF2-HMAC-SHA512, 210,000 iterations, 128-bit random salt | `Pbkdf2PasswordHasher` |
| Message encryption | AES-256-GCM, fresh 256-bit key + 96-bit nonce **per message** | `CryptoService` |
| Key exchange | RSA-2048 with OAEP (SHA-256/MGF1) padding, hybrid-encrypts the AES key | `KeyManager` |
| Private-key-at-rest protection | AES-256-GCM with a PBKDF2-derived key from the owner's password | `KeyManager.protectPrivateKey` |
| Quick-PIN local key protection | AES-256-GCM with a PBKDF2-derived key from the PIN | `DeviceKeyStore` |
| Randomness | `SecureRandom.getInstanceStrong()` with a documented fallback | `SecureRandomUtil` |

**On Argon2id vs PBKDF2 (an explicit trade-off, not a shortcut taken silently):** the
brief asks to evaluate Argon2id or bcrypt. Argon2id is the better choice for a real
deployment — it is memory-hard, which meaningfully raises the cost of GPU/ASIC
password-cracking in a way PBKDF2 cannot. This repository ships PBKDF2-HMAC-SHA512
instead because it needs **zero third-party dependencies** (it's 100% `javax.crypto`),
which keeps the project buildable in constrained/offline environments and avoids a
grading environment failing on a missing library. `PasswordHasher` is an interface
specifically so this is a one-class swap: implement `Argon2idPasswordHasher` against a
library such as `de.mkammerer:argon2-jvm`, change one constructor call in
`AppServices`, done. This trade-off — and the fact that it *is* a trade-off, not a claim
that PBKDF2 is equally strong — is stated here and in `PasswordHasher`'s own javadoc.

Never used: JWT-as-key, password-as-key, or PIN-as-key for message encryption. The only
inputs to AES message encryption are keys `CryptoService` generated itself or
`KeyManager` unwrapped from an RSA operation.

## L. Key-management architecture

This is the hybrid cryptosystem the brief specifically asked to evaluate — implemented,
not just described:

```
Registration:  each user gets an RSA-2048 keypair.
               public key  -> stored in `users.public_key`, freely readable.
               private key -> encrypted with a password-derived key, stored in
                               `users.protected_private_key`; plaintext exists only
                               in RAM during an active session (SessionService).

Per message:
  1. fresh random AES-256 key K          (CryptoService.generateMessageKey)
  2. ciphertext = AES-256-GCM(plaintext, K, freshNonce)
  3. wrappedK = RSA-OAEP-encrypt(K, recipientPublicKey)    (KeyManager.wrapMessageKey)
  4. frame = [2-byte len][wrappedK][12-byte nonce][ciphertext+16-byte tag]
  5. frame -> steganographically embedded into the carrier image

Per receive:
  1. authorization-checked fetch of the message row (§I)
  2. extract `frame` from the stego image
  3. K = RSA-OAEP-decrypt(wrappedK, recipient'sOWNPrivateKey)   (KeyManager.unwrapMessageKey)
  4. plaintext = AES-256-GCM-decrypt(ciphertext, K, nonce)  — throws on any tampering
```

Only the intended recipient's private key can recover `K`; the server/database never
sees `K` or the plaintext, only opaque wrapped/encrypted bytes. This is verified by
`KeyManagerTest.wrappedKeyCanOnlyBeUnwrappedByTheIntendedRecipient` and the full
round-trip in `EndToEndPipelineTest`.

## M. Steganography architecture

Implemented in `SteganographyService` + `CapacityCalculator` (see §W for the exact
formula). LSB (least-significant-bit) embedding across the R, G, B channels of a PNG,
with a 4-byte big-endian length header so the extractor knows exactly how many bytes to
read back — no delimiter-scanning through attacker-controlled ciphertext.

**Format choice — PNG only, and why (§24):** PNG is lossless, so LSB modifications
survive exactly. JPEG's lossy DCT compression would silently destroy embedded bits on
the very first save — so JPEG input is rejected outright by `ImageService`, rather than
accepted and quietly broken later.

**Explicit, stated limitations** (never oversold — §49):
- This is detectable by dedicated steganalysis (chi-square/RS attacks) if an adversary
  suspects the specific image and has the tooling — it defeats casual/automated
  inspection, not a forensic investigation.
- Any lossy re-encoding of the resulting PNG (re-saving as JPEG, some chat apps'
  compression pipelines) destroys the payload. The image must be transported byte-for-byte.
- Steganography is a **privacy layer on top of encryption**, never a replacement for it —
  which is exactly why encryption happens first, unconditionally, in `MessageService`.

## N. Message send sequence diagram

```
User (JavaFX)      ChatPane           MessageService        CryptoService/KeyManager     SteganographyService     DB/Disk
     │  type msg,     │                     │                          │                         │                  │
     │  pick image ───▶ live capacity check (checkCapacity) ───────────────────────────────────────────────────────▶│(read only)
     │                │                     │                          │                         │                  │
     │  click Send ───▶ sendMessage() ─────▶│                          │                         │                  │
     │                │                     │── generate AES key ─────▶│                         │                  │
     │                │                     │◀─ ciphertext+nonce ──────│                         │                  │
     │                │                     │── wrap key for recipient▶│                         │                  │
     │                │                     │◀─ wrapped key ───────────│                         │                  │
     │                │                     │── build frame ───────────────────────────────────▶ embed(frame) ────▶│
     │                │                     │◀──────────────────────────────────────── stego PNG bytes ────────────│
     │                │                     │── save PNG, INSERT messages, INSERT image_history ─────────────────▶ DB/Disk
     │                │◀─ Message record ───│                          │                         │                  │
     │◀─ UI refresh ──│                     │                          │                         │                  │
```

## O. Message receive/decode sequence diagram

```
User clicks         ChatPane            MessageService          DB                SteganographyService   KeyManager/CryptoService
"Decode Message"        │                    │                   │                        │                       │
     │──────────────────▶ decodeMessage() ──▶│                   │                        │                       │
     │                    │                  │── findByIdForUser (auth-checked) ─────────▶ │                       │
     │                    │                  │◀── Message row (or empty if not authorized)│                       │
     │                    │                  │── load stego image from image_path ───────▶ │                       │
     │                    │                  │── extract(image) ─────────────────────────▶ extract() ────────────▶│
     │                    │                  │◀── framed payload ─────────────────────────│                       │
     │                    │                  │── parse frame ─────────────────────────────────────────────────────▶│
     │                    │                  │── unwrap(wrappedKey, MY private key) ──────────────────────────────▶│
     │                    │                  │◀── AES key ─────────────────────────────────────────────────────────│
     │                    │                  │── decrypt+verify(ciphertext, nonce, key) ──────────────────────────▶│
     │                    │                  │◀── plaintext, or MessageIntegrityException on any tamper ───────────│
     │                    │                  │── markRead() ─────▶│                        │                       │
     │◀─ plaintext shown ─│◀─────────────────│                    │                        │                       │
```

Any failure anywhere in extraction/unwrap/decrypt surfaces to the user as the single
generic message *"Unable to verify this message. The image may be corrupted or
modified."* — never a stack trace, never which specific step failed (§35).

## P. Database ER design

```
users ─┬───────────< sessions
       ├───────────< conversations (as user_a_id) ─┐
       ├───────────< conversations (as user_b_id) ─┤
       │                                            ├──< messages >── (sender_id, recipient_id both -> users)
       ├───────────< messages (as sender_id) ───────┘
       ├───────────< messages (as recipient_id)
       ├───────────< image_history
       └───────────< security_events
```

One `conversations` row per unique unordered pair of users (`UNIQUE(user_a_id,
user_b_id)`), enforced in `ConversationRepository.findBetween`'s order-independent
lookup before ever inserting a duplicate.

## Q. Complete database schema

See `database/schema_sqlite.sql` (used by the runnable demo) and
`database/schema_postgres.sql` (recommended production schema, including a Row-Level
Security policy). Column-by-column rationale:

- **users**: `password_hash`/`pin_hash` are opaque PBKDF2-encoded strings, never
  plaintext. `protected_private_key` is a full AES-GCM record (salt+nonce+ciphertext),
  never a raw key. `failed_login_attempts`/`account_locked_until` and their PIN
  equivalents implement the rate-limiting in §23. No name, phone, or address fields
  exist at all — intentionally, per §5's minimal-collection principle.
- **sessions**: stores `token_hash` (SHA-256 of the bearer token), never the raw token —
  so a database read alone can't be used to forge a session.
- **conversations**: exactly two participants; `CHECK (user_a_id <> user_b_id)` and a
  uniqueness constraint prevent duplicate/self conversations at the DB level, not just
  in application code.
- **messages**: `image_path` is a reference, not image bytes (§37); `CHECK (sender_id <>
  recipient_id)` for the same defense-in-depth reason as conversations.
- **image_history**: retention-limited to 10 rows per user by
  `ImageHistoryRepository.insertAndPrune`, which prunes in the same transaction as every
  insert (§19/38).
- **security_events**: append-only, `detail` is documented as **non-sensitive-only** —
  `SecurityEventRepository`'s javadoc explicitly warns callers never to pass a password,
  PIN, key, or message plaintext into it (§36).

## R. API design

The runnable demo has **no network API** — the JavaFX client talks directly to its local
SQLite file (§F/G). The table below is the API a real multi-machine deployment (e.g. a
Spring Boot service in front of the Postgres/Supabase database) would expose, matching
the endpoints implied by §31 of the brief. `AuthService`/`ChatService`/`MessageService`
in this repository are written so that wrapping each public method in a REST controller
is close to mechanical — they already return plain domain objects/records and throw
typed exceptions with safe messages, which is exactly what a controller layer wants.

| Endpoint | Auth required | Authorization check | Notes |
|---|---|---|---|
| `POST /register` | No | — | Rate-limited by IP in production; generic failure message (§5) |
| `POST /login` | No | — | Returns a session token; account lockout applies |
| `POST /login/pin` | No (device-bound) | Device must have previously enabled Quick PIN | Never a substitute for full remote auth (§J) |
| `GET /users/lookup?email=` | Yes | None beyond authentication (contact discovery) | Returns only public_key + alias, never password/PIN data |
| `GET /conversations` | Yes | Filtered to the authenticated user server-side | Never accepts a "list conversations for user X" parameter |
| `GET /conversations/{id}/messages` | Yes | Membership check joined into the query (§I) | 404 (not 403) if not a member — no enumeration signal |
| `POST /messages` | Yes | Sender must be a conversation participant | Body carries the stego-image upload; server re-validates image server-side (§24) even though the client already did |
| `GET /messages/{id}/image` | Yes | Sender-or-recipient check (§I) | Streams from object storage, never a raw DB blob |
| `POST /settings/pin` | Yes | Session-authenticated user only | Enables/rotates Quick PIN |
| `DELETE /sessions` | Yes | — | "Log out of all devices" |

## S. Java package structure

```
com.stegomsg
 ├─ App.java                     entry point, wires AppServices + SceneManager
 ├─ model/                       User, Conversation, Message, ImageHistoryEntry
 ├─ security/                    CryptoService, KeyManager, PasswordHasher (+ Pbkdf2 impl),
 │                                DeviceKeyStore, SecureRandomUtil
 ├─ stego/                       SteganographyService, CapacityCalculator
 ├─ db/                          Database, UserRepository, ConversationRepository,
 │                                MessageRepository, ImageHistoryRepository,
 │                                SecurityEventRepository
 ├─ service/                     AppServices (DI container), AuthService, ChatService,
 │                                MessageService, ImageService, SessionService
 ├─ ui/                          SceneManager, DashboardView, ChatPane, SettingsPane,
 │   └─ controllers/              ImageHistoryPane, LoginController, RegisterController
 └─ util/                        Ids, Validation, Constants, DefaultImageGenerator
```

Controllers/panes never touch `db/` directly — they only call `service/` methods, which
are the only layer allowed to talk to repositories. This is what §29 means by "avoid
putting all code into JavaFX controllers."

## T. JavaFX screen structure

- **login.fxml / register.fxml** (`ui/fxml/controllers`): static forms, built with FXML
  to demonstrate the FXML+CSS workflow the brief asked for, where the low dynamism of
  the screen makes FXML's declarative layout a genuine win.
- **DashboardView, ChatPane, SettingsPane, ImageHistoryPane** (`ui/`): built
  programmatically in Java rather than FXML. This is a deliberate choice, not a
  shortcut: these screens have highly dynamic content (a variable number of
  conversations, live-updating capacity bars, dynamically built message cards) that is
  more naturally and more safely expressed as code than as static markup — idiomatic
  JavaFX practice mixes both approaches depending on how dynamic a screen is.

Single-shell navigation: `SceneManager` swaps the primary `Stage`'s `Scene` root; the
dashboard itself swaps its center `StackPane`'s content between `ChatPane`/
`ImageHistoryPane`/`SettingsPane`. No secondary windows are opened for any of this (§9).

## U. UI/UX design system

Defined once in `theme-dark.css` using JavaFX's "looked-up colors" as the CSS-variable
equivalent (`-bg-base`, `-accent-primary`, etc., defined on `.root`, referenced
everywhere else by name — change the theme by editing that one block). Component
classes: `.button-primary/-secondary/-danger`, `.card`, `.stego-card`,
`.plaintext-bubble`, `.badge-unread`, `.status-safe/-warning/-danger`,
`.capacity-panel`, `.sidebar-nav-button`. Dark theme is the default and only theme
shipped in the demo; a light theme would be a second stylesheet swapped the same way
`SceneManager` swaps FXML roots.

## V. Complete navigation flow

```
App launch
  └─ Login screen
       ├─ "Log In" (password)              ──▶ Dashboard (Chats view)
       ├─ "Log In with Quick PIN"           ──▶ Dashboard (Chats view)
       └─ "Create one"                      ──▶ Register screen ──▶ (back to) Login

Dashboard (single shell, sidebar swaps center content)
  ├─ Chats           : conversation list ─▶ thread + compose ─▶ Send / Decode
  ├─ Image History   : last 10 sent images, read-only table
  ├─ Settings        : account info, Quick PIN toggle, privacy/about
  └─ Log Out         ──▶ Login screen
```

## W. Capacity calculator design/formula

Implemented once, in `CapacityCalculator`, and used identically by both the live UI
indicator and the actual send pipeline (`MessageService.checkCapacity` calls the exact
same method `MessageService.sendMessage` uses internally) — so the number the user sees
before sending is never an approximation that could drift from what actually happens.

```
maxPayloadBytes(width, height, bitsPerChannel)
    = floor( width * height * 3 channels * bitsPerChannel / 8 )

actualPayloadBytes(plaintextLength)
    = plaintextLength                         (AES-GCM ciphertext is same length as plaintext)
    + 4   (length header, CapacityCalculator.LENGTH_HEADER_BYTES)
    + 2   (wrapped-key length field)
    + 256 (RSA-2048 wrapped AES key, fixed size)
    + 12  (GCM nonce)
    + 16  (GCM authentication tag)
    = plaintextLength + 290 bytes fixed overhead

status:
    actual > max            -> TOO_LARGE  ("✕ Image Too Small")
    actual / max >= 85%     -> WARNING    ("⚠ Near Capacity")
    otherwise               -> SAFE       ("✓ Safe")
```

This is verified against hand-computed values in `CapacityCalculatorTest` (e.g. a
100×100 image at 1 bit/channel has exactly 3,750 bytes of raw capacity).

## X. Image validation strategy

`ImageService.loadAndValidate` is the single choke point every image — built-in library
or user-uploaded — passes through before being used as a carrier or displayed:

1. File size between 0 and 20 MB (rejects empty files and a decompression-bomb-style
   oversized upload).
2. Filename must end in `.png` (extension check is a cheap first filter, never trusted
   alone).
3. **The actual bytes must decode via `ImageIO.read`** — this is the real check; a
   renamed `.exe` or corrupted file fails here regardless of its extension.
4. Decoded dimensions must not exceed 6000px in either direction.

Any failure throws `ImageValidationException` with one of a small set of generic,
pre-written messages ("Unable to process this image.", "Selected image does not have
enough capacity...") — the underlying `IOException`/decoder detail is never surfaced
(§35).

## Y. Security threat model

| Threat | Mitigated by |
|---|---|
| Passive network eavesdropping | Out of scope for the local demo (no network); a production deployment adds TLS on every client-server call (§31) |
| Database compromise (read access) | Passwords/PINs are hashed, private keys are encrypted with a password-derived key the DB doesn't have, message content isn't stored at all — only ciphertext-bearing images referenced by path |
| Stolen/lost device with the app installed | Session ends on logout; Quick PIN is rate-limited and device-scoped (§J); full remote compromise still requires the actual password |
| Online password guessing | Progressive lockout after 5 failed attempts, doubling delay (`AuthService`, `Constants`) |
| IDOR / cross-user data access | Ownership baked into every query's `WHERE` clause (§I), reinforced by RLS in the production schema |
| Tampered/corrupted stego image | AES-GCM authentication tag catches any modification; surfaced as one generic, safe error (§35) |
| Malicious image upload (disguised executable, decompression bomb, oversized file) | `ImageService.loadAndValidate` (§X) |
| SQL injection | Every query uses `PreparedStatement` with bound parameters — no string concatenation anywhere in `db/` |
| Steganalysis (detecting that an image contains hidden data) | Explicitly **not** claimed to be defended against — documented as a known limitation (§M/§25), because claiming otherwise would violate §49 |
| Sensitive data in logs | `SecurityEventRepository`'s contract explicitly forbids passing secrets into `detail`; no code path in this repo does |

## Z. Security checklist

- [x] Passwords hashed with a salted, industry-standard KDF (PBKDF2-HMAC-SHA512, 210k
      iterations) — never stored or logged in plaintext.
- [x] Message encryption uses authenticated encryption (AES-256-GCM) with a fresh
      key+nonce per message.
- [x] Key exchange via RSA-2048/OAEP hybrid encryption, not a shared/derived secret.
- [x] PIN is a local convenience only, never a remote-account credential; rate-limited.
- [x] All SQL is parameterized.
- [x] Every data-access query enforces ownership as part of the query itself (IDOR).
- [x] Uploaded/selected images are validated by actual decoding, not trusted metadata.
- [x] Generic, non-leaking error messages for auth failures and message-decode failures.
- [x] No plaintext secrets (passwords, PINs, keys, tokens, message content) in logs.
- [x] Account + PIN lockout with progressive delay.
- [ ] TLS in transit — not applicable to the local-only demo; required for any real
      client-server deployment (§31).
- [ ] OS-native secure storage for the Quick-PIN device key — the demo uses a
      permission-restricted local file with the limitation explicitly documented (§J).

## AA. Testing strategy

Implemented under `src/test/java`, runnable with `mvn test`:

- **Unit — security**: `CryptoServiceTest` (round-trip, tamper detection, wrong-key
  rejection, nonce uniqueness), `Pbkdf2PasswordHasherTest` (verify/reject, salting,
  malformed-hash handling), `KeyManagerTest` (cross-user key isolation, private-key
  protect/recover, wrong-password rejection).
- **Unit — steganography/capacity**: `SteganographyServiceTest` (round-trip, no mutation
  of the original image, capacity-exceeded rejection, variable bits-per-channel),
  `CapacityCalculatorTest` (hand-computed formula checks, SAFE/WARNING/TOO_LARGE
  boundaries).
- **Integration**: `EndToEndPipelineTest` runs the full encrypt → wrap → embed → extract
  → unwrap → decrypt pipeline exactly as `MessageService` does internally, plus a
  dedicated test proving a single flipped bit in the stego image is caught at decryption
  rather than silently corrupting the message.
- **Still to add for a full production test suite** (not included, to keep the demo's
  test run fast and dependency-free): repository-level tests against a real SQLite file,
  `AuthService`/`ChatService` tests with an in-memory `Database`, and JavaFX `TestFX`-based
  UI tests for the login/send/decode flows.

## AB. Development roadmap

The code in this repository already covers stages 1–13 below in a single pass (a
classroom demo benefits more from a complete, working vertical slice than from an
artificially staged delivery); the remaining stages are the realistic next steps for
turning this into a hardened production system.

1. Project setup — Maven, package layout ✅
2. Database + schema (SQLite demo + Postgres production target) ✅
3. Authentication (register/login, lockout) ✅
4. Session/security services ✅
5. JavaFX base UI (shell, login, register) ✅
6. Chat system (multi-conversation UI) ✅
7. Image handling (validation, default library) ✅
8. Capacity calculator ✅
9. Encryption (AES-GCM) ✅
10. Steganography (LSB embed/extract) ✅
11. Send/receive flow (full pipeline wiring) ✅
12. PIN login ✅
13. Image history ✅
14. **Security hardening for production**: TLS, a real backend service (§31), OS
    keystore integration for Quick PIN, Argon2id swap-in, dependency-scanning/SCA.
15. **Testing**: repository/service-level tests against a real DB, UI automation.
16. **Packaging/deployment**: `jpackage` native installers, CI pipeline, Supabase
    migration scripts.

## AC. Final recommended project structure

```
secure-stego-messenger/
├── ARCHITECTURE.md              (this file)
├── README.md
├── pom.xml
├── database/
│   ├── schema_sqlite.sql        (used by the runnable demo)
│   └── schema_postgres.sql      (recommended production schema, with RLS)
└── src/
    ├── main/java/com/stegomsg/  (see §S)
    ├── main/resources/com/stegomsg/
    │   ├── css/theme-dark.css
    │   └── fxml/login.fxml, register.fxml
    └── test/java/com/stegomsg/  (see §AA)
```
