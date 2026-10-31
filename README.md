# Journal App

A private journal: write entries, tag how they felt (Happy / Sad / Angry / Anxious), and see the
last 30 days as a mood ribbon. Spring Boot 3 + MySQL backend, with the web UI built in (served by
the same app, so there is just **one thing to deploy**).

Sign in with a username and password, or with Google. Authentication is a JWT stored in an
HttpOnly cookie (the API also accepts `Authorization: Bearer <token>` for Swagger / Postman).

See **CHANGES.md** for every bug and security issue that was fixed.

---

## 1. Run it on your computer

You need Java 21 and MySQL 8 running locally.

```bash
# 1. (optional) a stable signing key so you stay logged in across restarts
export JWT_SECRET="$(openssl rand -base64 48)"
export DB_PASSWORD="your-mysql-root-password"

# 2. start
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Open <http://localhost:8080>. The `dev` profile is the default; it creates the `Journal_App`
database for you and enables Swagger at <http://localhost:8080/swagger-ui.html>.

Everything else (Redis, Kafka, e-mail, weather, Google) is **optional**; the app starts without them.

### Make yourself an admin
Set these before starting; the account is created once if it does not exist:
```bash
export ADMIN_USERNAME=pranay ADMIN_PASSWORD='a-long-password' ADMIN_EMAIL=you@example.com
```
An **Admin** tab then appears in the UI. (Remove `ADMIN_PASSWORD` afterwards.)

---

## 2. Put it live

### Option A: Docker (any VPS, or your own PC)
```bash
cp .env.example .env        # fill in DB_PASSWORD and JWT_SECRET at minimum
docker compose up --build -d
```
This starts MySQL, Redis and the app on port 8080. Put a reverse proxy with HTTPS in front
(Caddy is the easiest: `your-domain.com { reverse_proxy localhost:8080 }`).
For a quick test over plain `http://localhost:8080`, add `COOKIE_SECURE=false` to `.env`.

### Option B: a platform (Render, Railway, Fly.io, ...)
1. Create a managed **MySQL** database (Railway / Aiven / PlanetScale-compatible / etc.).
2. Create a **Web Service** from this repo using the included `Dockerfile`.
3. Set the environment variables below. Use HTTPS (platforms give it to you).
4. Health check path: `/public/health-check`.

### Environment variables

| Variable | Required | What it is |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | prod | `prod` (the Dockerfile already sets it) |
| `DB_URL` | prod | e.g. `jdbc:mysql://host:3306/Journal_App?useSSL=true&serverTimezone=UTC` |
| `DB_USERNAME`, `DB_PASSWORD` | prod | database login |
| `JWT_SECRET` | **prod** | at least 32 characters; the app refuses to start in prod without it. `openssl rand -base64 48` |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | no | enables "Continue with Google" |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `ADMIN_EMAIL` | no | creates the first admin |
| `COOKIE_SECURE` | no | `true` by default in prod. Only set `false` for plain-http testing |
| `JWT_EXPIRATION_MINUTES` | no | default 60 |
| `WEATHER_API_KEY`, `WEATHER_DEFAULT_CITY` | no | weather greeting (weatherstack.com) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | no | weekly mood e-mail. Use a Gmail **App Password** |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL` | no | weather cache |
| `KAFKA_ENABLED`, `KAFKA_SERVERS` | no | route weekly mails through Kafka (off by default) |
| `CORS_ALLOWED_ORIGINS` | no | only if the UI is hosted on a different domain |
| `DDL_AUTO` | no | Hibernate schema mode, default `update` |
| `SWAGGER_ENABLED` | no | `false` in prod unless you set it |

### Set up Google sign-in
1. <https://console.cloud.google.com> -> **APIs & Services -> Credentials -> Create credentials -> OAuth client ID** -> *Web application*.
2. **Authorized redirect URI** (exactly, with your real domain):
   `https://YOUR-DOMAIN/login/oauth2/code/google`
   (for local testing also add `http://localhost:8080/login/oauth2/code/google`)
3. Copy the client id and secret into `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` and restart.

---

## 3. Existing database? Read this

The app uses `ddl-auto: update`, which adds new tables/columns but never changes existing ones.
Two column types were corrected, so if you already have a `Journal_App` database with
entries you want to keep, run this once:

```sql
ALTER TABLE Journal_Entries MODIFY COLUMN content TEXT;          -- was VARCHAR(255)
ALTER TABLE Journal_Entries MODIFY COLUMN sentiment VARCHAR(20); -- was a number (0-3)
UPDATE Journal_Entries SET sentiment = CASE sentiment
  WHEN '0' THEN 'HAPPY' WHEN '1' THEN 'SAD' WHEN '2' THEN 'ANGRY' WHEN '3' THEN 'ANXIOUS' ELSE NULL END;
```
If MySQL refuses because of a CHECK constraint on `sentiment`, drop it first
(`SHOW CREATE TABLE Journal_Entries;` shows its name, then `ALTER TABLE Journal_Entries DROP CHECK <name>;`).
If it is only test data, simply `DROP DATABASE Journal_App;` and let the app recreate it.

Entry times are now stored in **UTC** (the UI shows them in each visitor's own time zone).
Entries saved by the old version were stored in your computer's local time and will show shifted.
Users created by the old version keep working (their `provider` is empty and treated as password accounts).

---

## 4. API summary

| Method & path | Who | Purpose |
|---|---|---|
| `POST /public/signup` | anyone | create account (`userName`, `email?`, `password`, `sentimentAnalysis`) |
| `POST /public/login` | anyone | `{username, password}` -> sets cookie, returns `{token, user}` |
| `POST /public/logout` | anyone | clears the cookie |
| `GET /public/config`, `/public/health-check` | anyone | UI config, health |
| `GET /oauth2/authorization/google` | anyone | start Google sign-in |
| `GET /user/me` | signed in | current user |
| `PUT /user` | signed in | change username / e-mail / weekly mail / password |
| `DELETE /user` | signed in | delete my account and entries |
| `GET /user/weather?city=` | signed in | weather greeting data |
| `GET /journal`, `POST /journal` | signed in | list / create my entries |
| `GET /journal/id/{id}`, `PUT /journal/{id}`, `DELETE /journal/{id}` | signed in | one of **my** entries |
| `GET /admin/all-users`, `POST /admin/create-admin-user`, `POST /admin/clear-app-cache` | admin | administration |

Errors always look like `{"status":400,"message":"...","errors":{"field":"reason"}}`.

Using Swagger / Postman: call `POST /public/login`, copy `token`, then send
`Authorization: Bearer <token>`.

## 5. Tests
```bash
./mvnw test
```
Unit tests cover JWT creation/validation (tampered, expired, wrong key, short key) and the login
rate limiter. The full-context test is disabled because it needs a real MySQL.

## 6. Project layout
```
src/main/java/.../config      security, CORS, Redis, HTTP client, Swagger, admin bootstrap
src/main/java/.../security    cookie handling, rate limiter, Google sign-in handlers
src/main/java/.../filter      JwtFilter (authenticates every request)
src/main/java/.../controller  Public, User, Journal, Admin
src/main/java/.../service     business logic
src/main/java/.../dto         what goes in/out of the API (never the raw entities)
src/main/resources/static     the web UI (index.html, css/, js/, fonts/)
```
