# flagstone

Caveman feature management: a tiny API that serves a mostly-static JSON of feature
flags to my own apps. Deliberately minimal — git is the admin UI, a push is a flag
change. Repo: https://github.com/CostaFot/flagstone.

Status (2026-08-15): LIVE at https://flagstone-production.up.railway.app
(Railway project `flagstone`, service `flagstone`, production environment;
project id dcbbe5bf-0664-469e-85fe-51ae5e887180). First deploy observed SUCCESS
and verified with live authed requests. `FLAGSTONE_API_KEY` lives ONLY in the
Railway service variables — never commit it to this repo. Healthcheck path
/health, restart policy ON_FAILURE (5 retries). Every push to main redeploys.
User wants to be checked with before deploys; routine flag-file pushes are the
normal workflow, but confirm anything beyond that.

## Decisions already made (don't re-litigate)

- **Stack: Kotlin + Ktor** (Netty engine, kotlinx.serialization). Chosen over
  Go/Node because the consumers are Kotlin apps and the future SDK may share flag
  data classes (possibly via a KMP shared module). Spring Boot rejected (RAM cost,
  overkill). Framework speed is irrelevant at this scale.
- **Separate repos**, not a monorepo. This repo is the API only; an SDK, if it
  ever exists, gets its own repo. Decided after discussion.
- **No CDN, no extra services.** Single Railway service. Client-side caching via
  `Cache-Control` header instead.
- **Deploy target: Railway** (account: costafot). Create a NEW Railway project
  named `flagstone`, connected to the GitHub repo. Existing Railway projects
  (exemplary-connection, clever-perfection, precious-vibrancy) are unrelated —
  don't touch them.
- **Per-app flag files, path-based caller ID.** One JSON file per consuming app
  in `flags/` (e.g. `flags/myapp.json` → `GET /v1/flags/myapp`); adding an app =
  adding a file. Single shared API key for all apps (chosen over per-app keys —
  they're all the user's own apps). Decided after discussion.
- **Flag values are mixed primitives** (boolean, string, or number — no nested
  objects; validated at startup). Chosen over booleans-only and object-per-flag.
- **Consumers aren't only Kotlin**: at least one C# Windows app will call this.
  Note .NET's HttpClient ignores Cache-Control — C# callers need a manual
  ~5-minute cache.

## API design (as implemented)

- Flags live in per-app JSON files under `flags/` (env `FLAGS_DIR`, default
  `flags`), loaded into memory at startup. Changing flags = edit file, commit,
  push, Railway redeploys.
- `GET /v1/flags/{app}`: returns `flags/{app}.json`. Requires API key. Unknown
  app → 404. Non-.json files in the directory are ignored; startup fails fast on
  a missing/empty directory or non-primitive flag values.
- Auth: `X-API-Key` header checked (constant-time) against env var
  `FLAGSTONE_API_KEY`; missing or wrong → 401. Startup fails if the var is unset.
  Key is a long random string, set as a Railway variable and in consuming apps.
  All paths authed except `/health` — enforced by an application-level
  interceptor, so unknown paths fail closed. (Route-level `intercept` in Ktor
  leaks beyond its subtree — that's why it's application-level.)
- `GET /health`: unauthenticated, for Railway healthchecks.
- Flags responses send `Cache-Control: max-age=300` so clients reuse them.
- Listen on `PORT` env var (Railway injects it; default 8080 locally).

## Cost rationale (why it's built this way)

Railway bills idle RAM at $10/GB-month + $0.05/GB egress; requests are ~free.
Target: small heap, ~150–250 MB idle JVM → ~$2/month. Keep the JSON payload small.
Measured: the container idles at ~86 MiB with `-Xms16m -Xmx96m
-XX:MaxMetaspaceSize=96m -XX:+UseSerialGC` (set via `JAVA_OPTS` in the
Dockerfile) → under $1/month. Image is ~315 MB (build/deploy time only).

## Local environment

- Java 21 (OpenJDK 21.0.10) is on PATH. **No Gradle on PATH** — the Gradle
  wrapper (8.14.3) is set up in the repo; use `.\gradlew.bat` for everything.
- Windows 11, PowerShell. The `railway` CLI (5.41+) and Railway MCP are available
  and authenticated.
- Docker Desktop is installed but NOT auto-started. To test Docker locally:
  launch `C:\Program Files\Docker\Docker\Docker Desktop.exe` and poll
  `docker version` until the engine is up (~1 min). Verified working.
- The existing `.gitignore` is Android-flavored; fine to keep, extend if needed.
- Local run: `$env:FLAGSTONE_API_KEY = "dev-key"; .\gradlew run` (port 8080).

## Deploy notes

- **Check with the user before deploying** (or pushing anything that triggers a
  deploy). Local work is fine autonomously.
- The multi-stage Dockerfile (gradle jdk21 build → temurin 21 JRE alpine) is
  written and verified with a local `docker build` + container smoke test.
- Railway won't auto-connect the GitHub repo unless the Railway GitHub App is
  authorized for the CostaFot account — if the repo isn't visible when creating
  the service, that's a one-time browser step only the user can do.
- A public domain is not created automatically: trigger it (`railway domain` /
  MCP generate-domain) after the service exists; Railway picks the
  `*.up.railway.app` subdomain (editable later in service settings).
- Deploy sequence agreed with user: commit, push, create Railway project
  `flagstone` connected to the repo, generate a random `FLAGSTONE_API_KEY` and
  set it as a Railway variable, generate the domain, verify SUCCESS + a working
  authed request.
- Never report a deploy successful without observing terminal SUCCESS status.
