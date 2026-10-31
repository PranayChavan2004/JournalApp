# What was changed, and why

This document lists everything found in the original project and what was done about it.
Severity: **Critical** = data theft / account takeover / app unusable. **High** = serious but needs a
condition. **Medium/Low** = hardening or correctness.

> **Verification note.** The review environment could not download Maven dependencies, so the Java
> code was verified with `javac` syntax parsing (0 syntax errors) and careful line-by-line review,
> **not** a full `mvn package` / live run. The web UI was tested with a scripted browser-DOM test
> (all checks pass). Please run `./mvnw test` and start the app once on your machine; if anything
> fails to compile, send me the error and I will fix it right away.

---

## A. Security problems fixed

| # | Severity | Problem in the original | Fix |
|---|---|---|---|
| S1 | **Critical** | **Password saved in plain text.** `PUT /user` set the new password without encoding it (the comment even said so), then login stopped working for that user. | Passwords are always BCrypt-hashed (cost 12). Changing a password now requires the *current* password, and revokes all older tokens. |
| S2 | **Critical** | **Any logged-in user could download every user, including password hashes** (`GET /user`), and read any user by id (`GET /user/{id}`) - an IDOR. | Both endpoints removed. Users can only see themselves (`GET /user/me`). Listing users is admin-only and returns a DTO with no hash. `@JsonIgnore` on the entity's password and journal list as a second safety net. |
| S3 | **Critical** | **JWT filter was never in the security chain**, so JWT login did not actually protect anything, while **HTTP Basic auth was on**, letting clients send the real password on every request and bypass login logic. | `JwtFilter` is registered in the chain (and de-registered from the servlet container so it runs once). HTTP Basic, form login and the default logout are disabled. API is stateless. |
| S4 | **Critical** | **Hand-written Google OAuth endpoint was unsafe**: no `state` check (login CSRF), never verified the token was meant for this app (audience), never checked the e-mail was verified, hard-coded Google's *OAuth Playground* as the redirect URI, put the e-mail straight into username, and sat behind authentication so it could not even be reached. | Replaced with Spring Security's standard OpenID Connect login, which validates the ID-token signature, issuer, audience, expiry, nonce and `state`. We additionally require `email_verified = true`. |
| S5 | **High** | **Account pre-hijacking via Google.** Anyone could sign up with a victim's e-mail, then inherit the account when the victim used Google. | Google sign-in only logs into accounts created via Google. If a password account owns that e-mail it is refused (`account_exists`), never merged. Usernames may not contain `@`, so nobody can squat on an e-mail-shaped username. |
| S6 | **High** | **Hard-coded secrets in the repo**: DB `root`/`xxxx`, Gmail address, Kafka SASL `X`/`X`; no JWT secret defined anywhere. Log files and `.idea/` were also shipped. | Every secret comes from an environment variable. JWT secret must be >= 32 chars and the app **refuses to start in prod without it**. `.env` is git-ignored; `.env.example` provided. Logs and IDE files removed. |
| S7 | **High** | **No brute-force protection** on login. | 5 wrong passwords in 15 min locks that login for 15 min (HTTP 429). Same message for "unknown user" and "wrong password" (no account probing). |
| S8 | **High** | **Mass assignment.** Controllers bound raw entities, so a client could choose the entry `id`/`date`, or send `roles`/`id` fields when creating users. | Request DTOs only accept the fields a client may set. Role is always `USER` on sign-up. |
| S9 | **High** | **Weather URL injection.** City was pasted into the outgoing URL unencoded (e.g. `x&access_key=...`). | City is validated (letters/space/`.'-`) and URL-encoded. |
| S10 | **High** | **Token handling exposed to XSS/CSRF risks** (typical for tokens in `localStorage`). | JWT lives in an `HttpOnly; SameSite=Strict; Secure` cookie, so scripts cannot read it. Cookie-authenticated writes also need an `X-Requested-With` header (blocks CSRF). Bearer-header clients are unaffected. |
| S11 | Medium | **Error messages leaked internals** (`e.getMessage()` returned to the client, `printStackTrace()`). | One global exception handler: clear messages for client errors, generic text for server errors; details only in server logs. |
| S12 | Medium | **No security headers.** | Strict Content-Security-Policy (own scripts/styles/fonts only), `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, HSTS on HTTPS. |
| S13 | Medium | **State-changing GET** (`GET /admin/clear-app-cache`). | Now `POST`. |
| S14 | Medium | **Admin check was URL-only.** | Also enforced with `@PreAuthorize("hasRole('ADMIN')")`. There was no safe way to create the first admin; added `ADMIN_USERNAME/ADMIN_PASSWORD` bootstrap. |
| S15 | Medium | **Tokens could not be revoked** and identified users by a changeable username. | Token subject is the immutable user id and carries a version; changing the password bumps it, killing old tokens. Roles are re-read from the DB on each request (a demoted admin loses access immediately). |
| S16 | Medium | **Invalid JWT crashed with HTTP 500** (parse exceptions escaped the filter). | Invalid/expired/tampered tokens are simply treated as "not signed in" (401). |
| S17 | Medium | **Swagger always exposed.** | Disabled by default; enabled only in `dev` (or `SWAGGER_ENABLED=true`). |
| S18 | Medium | **Practice controller `/forPractice`** let any user read/write a shared in-memory map. | Removed. |
| S19 | Low | **Input not validated** (no length/format limits on username, password, title, content, e-mail). | Bean Validation on all inputs; password 8-72 chars (72 is BCrypt's real limit). |
| S20 | Low | **Sentiment e-mail logged the recipient's address.** | Address no longer logged. |

## B. Bugs fixed (things that crashed or silently did the wrong thing)

| # | Where | Problem | Fix |
|---|---|---|---|
| B1 | `JwtUtils` | Used `@Value` without importing it (**compile error**); property `jwt.secret` did not exist; comment said "5 minutes" but tokens lasted 1 hour. | Rewritten with constructor injection, config property, honest configurable expiry. |
| B2 | `User` / `UserService` | `id` was never set by `saveNewUser`/`saveAdmin`, so **every sign-up failed**; the error was swallowed and the API still answered 200 OK (with joke `log` lines "hahahah"). | UUID is generated by JPA; failures are reported (409 for duplicates, 400 for bad input). |
| B3 | `application-dev.yaml` | Redis config was at `spring.redis.data.*` (wrong; Boot 3 uses `spring.data.redis.*`), so your Redis host/password were ignored. | Correct path, with 2 s time-outs. |
| B4 | `application-dev.yaml` | `spring.server.port` / `context-path` were misplaced and did nothing; Swagger listed ports 8081/8082 that never existed. | Removed; `server.port` uses `$PORT`; Swagger uses relative URL. |
| B5 | Startup | App could not start: unresolved `${KAFKA_SERVERS}`, missing Google client-id placeholder, missing JWT secret, DB password wrong. (Your own logs show each of these.) | Safe defaults; Kafka/Redis/mail/Google are optional; clear failure only for genuinely required settings. |
| B6 | `UserController` | `GET /{id}` and `GET /{username}` have the same URL shape -> Spring refuses to start ("Ambiguous mapping"). | Endpoints removed (see S2). |
| B7 | `WeatherResponse` | Inner class was not `static`, so Jackson could never create it: every weather call failed. | Made `static`; unknown JSON fields ignored. |
| B8 | `WeatherService` | Called the weather API with **POST** (it is GET); `NullPointerException` if the config table had no `WEATHER_API` row; no time-outs. | GET, default URL in config, timeouts, graceful "unavailable". |
| B9 | `RedisService` | A cache *miss* called `.toString()` on null -> exception + ERROR log on every first lookup. Raw generic types. | Clean null handling; Redis being down just means "no cache". |
| B10 | `UserScheduler` | Weekly job read lazy `journalEntries` outside a transaction -> `LazyInitializationException`, so **no weekly e-mail was ever sent**; NPE on entries without date/mood; the Kafka -> e-mail fallback could never trigger (send is async). | Runs in a transaction, null-safe, fallback handled in the async callback; Kafka is opt-in. |
| B11 | `JournalEntry` | `content` was `VARCHAR(255)` - longer journal entries failed to save. `Sentiment` stored as a number - reordering the enum would silently relabel old data. | `TEXT` column; stored by name. |
| B12 | `JournalEntryService` | Saving swallowed all exceptions (only `printStackTrace`), controller still said "Data Saved Successfully". | Errors propagate and roll back; client gets a real error. |
| B13 | `JournalController` | `GET /journal` returned **404 when the list was empty** (a new account looked broken); editing an entry changed its creation date. | Returns `[]`; date preserved. Entries newest-first. |
| B14 | Deleting a user | Their journal entries stayed in the database forever. | Cascade + orphan removal. |
| B15 | `User` | `@Builder` ignored the `journalEntries = new ArrayList<>()` default (null list). | `@Builder.Default`. |
| B16 | `JournalAppApplication` | Redundant manual transaction manager; `new RestTemplate()` without time-outs. | Removed / moved to `HttpClientConfig` with 3 s / 5 s limits. |
| B17 | `pom.xml` | Duplicate `spring-boot-starter-security`; unused ModelMapper; empty license/scm blocks. | Cleaned; added `spring-boot-starter-oauth2-client`. |
| B18 | `logback.xml` | Wrote log files into the project folder (and they were committed). | `logback-spring.xml`: console everywhere, rolling file only in `dev`, into `logs/`. |
| B19 | Tests | `contextLoads` fails on any machine without MySQL/Redis/Kafka, breaking `mvn package`. | Disabled with explanation; real unit tests added. |
| B20 | Time zones | Times stored in the server's local zone with no zone info. | Stored in UTC, sent as ISO instants, displayed in the viewer's zone. |

## C. New features added

* **Web UI** (served at `/`): sign in / sign up, Continue with Google, write entries with a mood,
  the 30-day mood ribbon, search and filter, edit and delete with confirmation, account page
  (weekly e-mail preference, weather city, change password, delete account), admin user list.
  Responsive, keyboard accessible, light and dark mode, reduced-motion respected. Fonts are
  self-hosted (Literata and Figtree, SIL Open Font License) so no third-party requests are made.
* **Google OAuth2 (OIDC) + JWT**, see S3, S4, S10.
* **Deployment kit**: `Dockerfile` (non-root), `docker-compose.yml` (MySQL + Redis + app),
  `.env.example`, `README.md` with Render/Railway/VPS steps.
* **Unit tests** for JWT and the login rate limiter.

## D. Behaviour changes you should know about

* `POST /public/login` now returns JSON (`{token, tokenType, expiresIn, user}`) instead of a bare string, and also sets the cookie.
* Sign-up returns `201` with the user (no password) and `409` for taken username/e-mail.
* `GET /admin/clear-app-cache` is now `POST /admin/clear-app-cache`.
* `GET /user`, `GET /user/{id}`, `GET /user/{username}`, `/forPractice/**`, `/auth/google/callback` no longer exist.
* Environment variable `JAVA_EMAIL_PASSWORD` still works as a fallback; the preferred name is `MAIL_PASSWORD`.
* Login accepts a username **or** e-mail address.

## E. Known limitations / suggested next steps

* **Rate limiting is in memory** (fine for one server). Use Redis if you run several instances.
* **No e-mail verification** for password sign-ups. (Google accounts are verified by Google.) Adding it is the proper long-term fix for e-mail ownership.
* **`GET /journal` returns all entries** (fine for personal use). Add paging if a user could have thousands.
* **Schema is managed by Hibernate `update`.** For serious production use, adopt Flyway/Liquibase migrations.
* Weatherstack's free plan only supports `http`; the API key is sent in clear text server-to-server. Use a paid plan / provider with HTTPS via `WEATHER_API_URL` if that matters to you.
* A stolen token stays valid until it expires (default 60 minutes) unless the password is changed. Shorten `JWT_EXPIRATION_MINUTES` if you prefer.
