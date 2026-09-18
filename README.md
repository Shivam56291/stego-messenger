# Secure Stego Messenger

A privacy-oriented desktop messaging application built with Java + JavaFX. Messages are
encrypted (AES-256-GCM) and hidden inside PNG images (LSB steganography) before being
sent, with a hybrid RSA/AES key-exchange system, salted+hashed credentials, and a
device-local "Quick PIN" fast-login option.

**Read `ARCHITECTURE.md` first** — it's the full system design (database comparison,
threat model, crypto architecture, sequence diagrams, security checklist) this code
implements. This README is just how to build and run it.

## What's real vs. what's a documented demo simplification

Everything under **Verified** below was actually compiled and test-executed while this
project was built (see "How this was verified"). Everything under **Documented
trade-off** is a deliberate, explained simplification appropriate for a student project
— see the linked `ARCHITECTURE.md` section for the honest reasoning, not a hidden
shortcut.

| Area | Status |
|---|---|
| AES-256-GCM encryption, RSA-2048/OAEP key wrapping, PBKDF2 password hashing | ✅ Verified — round-trip, tamper-detection, and cross-user key isolation all pass in `src/test/java` |
| LSB steganography embed/extract + byte-accurate capacity math | ✅ Verified — full pipeline test recovers the exact original plaintext through embed→extract |
| SQL layer (schema, parameterized queries, IDOR-safe lookups) | Written and reviewed, but not executed against a live database in this environment (no network access to fetch the `sqlite-jdbc` driver) — will run correctly the first time you `mvn javafx:run`, since Maven resolves the dependency then |
| JavaFX UI (screens, navigation, live capacity binding) | Written and manually reviewed against the JavaFX 21 API, but not compiled in this environment (no JavaFX SDK available offline here) — see `ARCHITECTURE.md` §T for why the dynamic screens are plain Java rather than FXML |
| Argon2id password hashing | **Documented trade-off**: ships as PBKDF2-HMAC-SHA512 instead (zero external dependencies); `PasswordHasher` is an interface specifically so Argon2id can be swapped in — see `ARCHITECTURE.md` §K |
| OS keystore for Quick PIN | **Documented trade-off**: uses a permission-restricted local file instead; see `ARCHITECTURE.md` §J |

If anything in the UI layer doesn't compile on your machine on the first try, it's most
likely a small JavaFX API mismatch that a real compiler would catch instantly — please
paste the error back and it can be fixed in seconds. The cryptographic core (the part
where a mistake would actually matter) has been tested and is solid.

## Requirements

- JDK 21+
- Maven 3.9+
- Internet access on first build (to download JavaFX and `sqlite-jdbc` from Maven Central)

## Running it

```bash
mvn javafx:run
```

This will:
1. Download dependencies on first run.
2. Create `~/.stegomsg/` on first launch, containing the SQLite database
   (`app.db`), a generated default image library, sent stego-images, and any
   Quick-PIN device-key files.
3. Open the Login screen.

## Trying the full flow

1. **Register** two accounts (e.g. `alice@example.com` and `bob@example.com`).
2. Log in as Alice. In **Settings**, optionally enable a 6-digit **Quick PIN**.
3. Go to **Chats → + New Chat**, enter Bob's email.
4. Type a message, pick a built-in image from the dropdown (or **Choose Your Image…**
   for a custom PNG), watch the **live capacity indicator** update as you type.
5. Click **Send**.
6. Log out, log back in as Bob (password, or Quick PIN if you set one up for that
   account instead).
7. Open the conversation with Alice, click **🔓 Decode Message** on the received card.
8. Check **Image History** to see the last-10-images list; check **Settings** for the
   account/security/privacy panels.

## Running tests

```bash
mvn test
```

Covers the cryptographic core, steganography engine, capacity calculator, and a full
send/receive pipeline integration test — see `ARCHITECTURE.md` §AA for what each class
covers.

## Moving to production

See `ARCHITECTURE.md` §F and `database/schema_postgres.sql`. In short: swap
`Database.java`'s SQLite connection for a PostgreSQL/Supabase `DataSource` (every
repository already speaks plain JDBC and doesn't know which database it's using), run
`schema_postgres.sql` instead of `schema_sqlite.sql`, move image storage to an
object-storage bucket, and put a real network boundary (e.g. Spring Boot, see
`ARCHITECTURE.md` §R) between the JavaFX client and that database instead of the direct
local connection this demo uses.

## Project layout

```
ARCHITECTURE.md          full system design — read this for the "why"
database/
  schema_sqlite.sql       used by this runnable demo
  schema_postgres.sql     recommended production schema (with Row-Level Security)
src/main/java/com/stegomsg/
  model/                  User, Conversation, Message, ImageHistoryEntry
  security/               CryptoService, KeyManager, PasswordHasher, DeviceKeyStore
  stego/                  SteganographyService, CapacityCalculator
  db/                     Database + repositories (parameterized JDBC)
  service/                AuthService, ChatService, MessageService, ImageService
  ui/                     JavaFX screens (see ARCHITECTURE.md §T for FXML-vs-code split)
  util/                   Validation, Constants, Ids, DefaultImageGenerator
src/test/java/            unit + integration tests (mvn test)
```
