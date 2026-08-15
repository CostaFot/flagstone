# flagstone

Caveman feature management: a tiny Ktor API that serves a mostly-static JSON of
feature flags to my own apps. Git is the admin UI — edit a file in `flags/`,
commit, push, and Railway redeploys.

Each app gets its own file: `flags/myapp.json` is served at `/v1/flags/myapp`.
Adding an app = adding a file.

## API

| Endpoint     | Auth               | Response                                      |
|--------------|--------------------|-----------------------------------------------|
| `GET /health`    | none           | `200 OK` (for Railway healthchecks)           |
| `GET /v1/flags/{app}`  | `X-API-Key` header | `flags/{app}.json` with `Cache-Control: max-age=300`, or `404` for an unknown app |

Flag values are JSON primitives (boolean, string, or number). Missing or wrong
API key → `401`.

## Configuration

| Env var             | Purpose                                  |
|---------------------|------------------------------------------|
| `FLAGSTONE_API_KEY` | Required. The API key clients must send. |
| `PORT`              | Listen port (default `8080`).            |
| `FLAGS_DIR`         | Directory of per-app flag files (default `flags`). |

## Development

```sh
$env:FLAGSTONE_API_KEY = "dev-key"
./gradlew run
```

Tests: `./gradlew test`
