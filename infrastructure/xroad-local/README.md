# Local X-Road Security Server (sidecar)

**Purpose: operator familiarity only.** Lets you log into the X-Road admin UI and click around without needing AWS or
RIA-issued certs. **This does not validate code-level integration with rahvastikuregister or any real X-Road service** —
code confidence comes from `@RestClientTest` tests in onboarding-service.

Mirrors the production deployment in spirit (same image `niis/xroad-security-server-sidecar:7.8.0-ee`, same external
Postgres 16 pattern, same volume layout) but is single-machine, no anchor, no certs.

## Prerequisites

- Docker Desktop running
- ~5 GiB free disk
- ~4 GiB free RAM (the sidecar full image is heavy)

## First-time setup

```sh
cp .env.example .env
# (optional: edit .env if you want different local passwords)
docker compose up -d
```

First boot takes ~2–3 minutes. Watch progress:

```sh
docker compose logs -f xroad
```

Wait for `xroad-proxy-ui-api` to settle. The healthcheck container status moves from `starting` → `healthy` once the
admin UI port is up.

## Open the admin UI

Browser: <https://127.0.0.1:4000>

Log in with the credentials from `.env` (`XROAD_ADMIN_USER` / `XROAD_ADMIN_PASSWORD`). The browser will warn about the
self-signed cert — accept it (local dev only).

The UI will say "configuration anchor not loaded" — that's expected. We don't import an anchor here. If you want to
actually click through to a registered subsystem locally, see "optional: connect to ee-test" below.

## Verify the stack is healthy

```sh
docker compose exec xroad supervisorctl status
# All xroad-* services should be RUNNING (xroad-autologin and the embedded postgres
# show EXITED/STOPPED — both are normal: autologin runs once, embedded postgres is
# disabled because we point at the sibling container via XROAD_DB_HOST).

curl http://127.0.0.1:5588/
# Returns "Global configuration is expired" with HTTP 500 — expected for local dev
# without an anchor loaded. The process being up is what matters.

curl -k -s -o /dev/null -w "HTTP %{http_code}\n" https://127.0.0.1:4000/
# Returns HTTP 200 — admin UI is reachable.
```

## Get the sidecar UID/GID (needed for production EFS access points)

```sh
docker compose exec xroad id xroad
# uid=999(xroad) gid=999(xroad) — these are the values to use in the Phase 2
# Terraform EFS access-point config so the mount is writable by the sidecar.
```

## Apple Silicon note

The NIIS image is `linux/amd64` only — on M-series Macs Docker runs it via Rosetta 2 emulation. Slower (first boot can
take 3–5 min instead of 2–3) but functional.

## Optional: connect to ee-test

Useful if you want to explore the real X-Road catalogue. **Not required** for the X-Road plan since production rolls
straight to `ee` and onboarding-service code is exercised via `@RestClientTest` against mocks.

1. Download the `ee-test` configuration anchor from <https://x-tee.ee/anchors/>. Verify the published hash.
2. In the admin UI: go through Initial Configuration, set token PIN, upload the anchor.
3. Generate an AUTH and SIGN CSR. Order test certs from RIA via the iseteenindus.
4. (Several days waiting for RIA approval.)
5. Import certs, register the SS, register the subsystem.

This is the same flow as the production bootstrap in `infrastructure/terraform-xroad/BOOTSTRAP.md` but for the test
environment. Most local-dev work doesn't need any of this.

## Tear down

```sh
docker compose down            # stop containers, keep volumes (config, archive, db)
docker compose down -v         # WARNING: also wipes /etc/xroad, the message-log archive, and the local postgres
```

`docker compose down -v` is the easiest reset path if you've explored the UI and want to start clean. Don't run it in
production-adjacent environments.

## Why no `5500` / `5577` in this compose

Those ports are for inter-Security-Server message exchange. We're consumer-only in production and never run as a
provider, so we don't need them locally either. If you connect this dev sidecar to a real X-Road network later, add
them.

## Why an external Postgres container instead of the embedded one

Matches the production design (dedicated RDS). Lets us validate the `XROAD_DB_HOST/PORT/PWD` injection path locally. The
sidecar's embedded Postgres also works for pure-local play but doesn't exercise the same code path.
