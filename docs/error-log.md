# Reviewer error log

Keycloak writes a JSON error log for a daily review. The console log is unchanged.

The file is:

`/var/log/ds-errors/keycloak.errors.log`

Rotation is by size: 10MB, then five backups (`keycloak.errors.log.1` through `keycloak.errors.log.5`). The file is not rotated just because the container started.

## What is in the file

Two categories are attached to this file and nowhere else is:

- `com.mesutpiskin.keycloak.auth.email.ds.idp` at WARN and above. One line per social sign-in failure (profile fetch, token exchange, or anything else on the broker callback). The line has the provider alias, realm, client id, stage, upstream HTTP status, and a short snippet. Access tokens, refresh tokens, authorization codes, secrets, and email addresses are removed.
- `com.mesutpiskin.keycloak.auth.email.ds.errors` at ERROR. Keycloak `*_ERROR` events that are server problems, such as `IDENTITY_PROVIDER_LOGIN_ERROR` or a failed email send. Wrong passwords, expired or invalid codes, cancelled logins, and similar user mistakes are not written here.

Keycloak's own broker logger can still print an upstream body on stdout. That line is not copied into this file.

A social failure looks like this (the timestamp and host come from Quarkus JSON logging):

```json
{"timestamp":"2026-11-22T01:49:12.123+01:00","level":"WARN","loggerName":"com.mesutpiskin.keycloak.auth.email.ds.idp","message":"IdP sign-in failed provider=x realm=ds client_id=gp-web stage=profile fetch upstream_status=403 snippet=\"title=Forbidden type=about:blank status=403 detail=User has been suspended\""}
```

A server event looks like this:

```json
{"timestamp":"2026-11-22T01:49:12.180+01:00","level":"ERROR","loggerName":"com.mesutpiskin.keycloak.auth.email.ds.errors","message":"Keycloak server error type=IDENTITY_PROVIDER_LOGIN_ERROR realm=ds client_id=gp-web error=identity_provider_login_failure provider=x user_id=8d0c1a2e-4b6f-4c8a-9d1e-2f3a4b5c6d7e"}
```

## Coolify volume

Mount a persistent volume so the file survives a redeploy:

| Setting | Value |
| --- | --- |
| Destination path | `/var/log/ds-errors` |
| Source | a Coolify volume, for example `ds-keycloak-errors` |

The process runs as uid `1000`. The mount has to be writable by that user:

```bash
sudo chown 1000:0 /path/on/the-host/ds-keycloak-errors
sudo chmod 775 /path/on/the-host/ds-keycloak-errors
```

The image already appends `conf/ds-error-log.properties` to `/opt/keycloak/conf/keycloak.conf` and creates `/var/log/ds-errors`. No extra environment variable is required. If a Coolify config mount replaces `keycloak.conf`, keep the `quarkus.log.handler.file."ds-errors"` and the two `quarkus.log.category` blocks from that properties file.

Do not set `KC_LOG=console,file` for this. That turns on Keycloak's root file handler and would mix ordinary errors, including unsanitized upstream bodies, into a log file. Stdout stays on the console handler.
